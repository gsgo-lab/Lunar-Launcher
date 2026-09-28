// Lunar core: hosts a mobile OpenJDK inside the game process via JLI_Launch.
// Android (targetSdk >= 29) forbids exec() of files from the app data dir,
// so the JVM must be loaded in-process with dlopen().
#include <jni.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <mutex>

#define TAG "LunarCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef int (*JLI_Launch_t)(int argc, char **argv, int jargc, const char **jargv,
                            int appclassc, const char **appclassv,
                            const char *fullversion, const char *dotversion,
                            const char *pname, const char *lname,
                            jboolean javaargs, jboolean cpwildcard,
                            jboolean javaw, jint ergo);

static std::string jstr(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r(c);
    env->ReleaseStringUTFChars(s, c);
    return r;
}

static bool exists(const std::string &p) { return access(p.c_str(), R_OK) == 0; }

// The dynamic linker reads LD_LIBRARY_PATH only at process start; this hook
// makes the change visible to later dlopen() calls (used by libjvm's deps).
static void updateLdPath(const std::string &paths) {
    setenv("LD_LIBRARY_PATH", paths.c_str(), 1);
    void *h = dlopen("libdl_android.so", RTLD_LAZY);
    if (!h) return;
    typedef void (*upd_t)(const char *);
    auto fn = (upd_t) dlsym(h, "android_update_LD_LIBRARY_PATH");
    if (fn) fn(paths.c_str()); else LOGE("android_update_LD_LIBRARY_PATH not found");
}

static std::string findFirst(const std::string &home, const std::vector<std::string> &rel) {
    for (auto &r : rel) { std::string p = home + "/" + r; if (exists(p)) return p; }
    return "";
}

// ---- Surface handoff (consumed by the GLFW/EGL bridge of the graphics core) ----
static std::mutex g_winLock;
static ANativeWindow *g_window = nullptr;

extern "C" ANativeWindow *lunar_get_window() {
    std::lock_guard<std::mutex> g(g_winLock);
    return g_window;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lunar_launcher_runtime_NativeJvm_nativeSetSurface(JNIEnv *env, jclass, jobject surface) {
    std::lock_guard<std::mutex> g(g_winLock);
    if (g_window) { ANativeWindow_release(g_window); g_window = nullptr; }
    if (surface) g_window = ANativeWindow_fromSurface(env, surface);
}

// ---- JVM host ----
extern "C" JNIEXPORT jint JNICALL
Java_com_lunar_launcher_runtime_NativeJvm_nativeRun(JNIEnv *env, jclass, jstring jHome, jstring jGameDir,
                                                    jstring jLog, jobjectArray jArgs, jobjectArray jEnv) {
    std::string home = jstr(env, jHome), gameDir = jstr(env, jGameDir), logPath = jstr(env, jLog);

    // stdout/stderr of the JVM -> log file
    int fd = open(logPath.c_str(), O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) { dup2(fd, 1); dup2(fd, 2); close(fd); }

    // environment: array of "KEY=VALUE"
    std::string ld;
    jsize en = env->GetArrayLength(jEnv);
    for (jsize i = 0; i < en; i++) {
        std::string kv = jstr(env, (jstring) env->GetObjectArrayElement(jEnv, i));
        auto eq = kv.find('=');
        if (eq == std::string::npos) continue;
        std::string k = kv.substr(0, eq), v = kv.substr(eq + 1);
        if (k == "LD_LIBRARY_PATH") ld = v; else setenv(k.c_str(), v.c_str(), 1);
    }
    setenv("JAVA_HOME", home.c_str(), 1);
    setenv("HOME", gameDir.c_str(), 1);
    if (chdir(gameDir.c_str()) != 0) LOGE("chdir(%s) failed", gameDir.c_str());

    // JDK 8 layout: jre/lib/<arch>; JDK 9+ layout: lib
    std::string jli = findFirst(home, {"lib/libjli.so", "lib/jli/libjli.so", "jre/lib/aarch64/jli/libjli.so",
                                        "jre/lib/aarch64/libjli.so", "jre/lib/arm/jli/libjli.so", "jre/lib/arm/libjli.so",
                                        "jre/lib/amd64/jli/libjli.so", "jre/lib/i386/jli/libjli.so"});
    if (jli.empty()) { LOGE("libjli.so not found in %s", home.c_str()); return -10; }

    std::string libDirs = ld;
    auto add = [&](const std::string &rel) {
        std::string p = home + "/" + rel;
        if (access(p.c_str(), F_OK) == 0) libDirs += (libDirs.empty() ? "" : ":") + p;
    };
    add("lib"); add("lib/jli"); add("lib/server");
    add("jre/lib/aarch64"); add("jre/lib/aarch64/jli"); add("jre/lib/aarch64/server");
    add("jre/lib/arm"); add("jre/lib/arm/jli"); add("jre/lib/arm/server");
    updateLdPath(libDirs);
    LOGI("LD_LIBRARY_PATH=%s", libDirs.c_str());

    void *h = dlopen(jli.c_str(), RTLD_LAZY | RTLD_GLOBAL);
    if (!h) { LOGE("dlopen(%s): %s", jli.c_str(), dlerror()); return -11; }
    auto launch = (JLI_Launch_t) dlsym(h, "JLI_Launch");
    if (!launch) { LOGE("JLI_Launch missing: %s", dlerror()); return -12; }

    std::vector<std::string> store;
    store.push_back(home + "/bin/java");
    jsize an = env->GetArrayLength(jArgs);
    for (jsize i = 0; i < an; i++) store.push_back(jstr(env, (jstring) env->GetObjectArrayElement(jArgs, i)));
    std::vector<char *> argv;
    for (auto &s : store) argv.push_back(const_cast<char *>(s.c_str()));
    argv.push_back(nullptr);

    LOGI("JLI_Launch with %zu args", store.size());
    int rc = launch((int) store.size(), argv.data(), 0, nullptr, 0, nullptr,
                    "1.8.0-internal", "1.8", "java", "openjdk",
                    JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
    LOGI("JLI_Launch returned %d", rc);
    return rc;
}
