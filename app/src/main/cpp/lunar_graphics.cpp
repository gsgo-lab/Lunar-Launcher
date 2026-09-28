#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <mutex>

#define TAG "LunarCore"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_lock;
static ANativeWindow* g_window = nullptr;
static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLSurface g_surface = EGL_NO_SURFACE;
static EGLContext g_context = EGL_NO_CONTEXT;

extern "C" ANativeWindow* lunar_get_window() {
    std::lock_guard<std::mutex> guard(g_lock);
    return g_window;
}

static void releaseWindowLocked() {
    if (g_window) {
        ANativeWindow_release(g_window);
        g_window = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_lunar_launcher_runtime_LunarGraphicsBridge_nativeSetSurface(
        JNIEnv* env, jclass, jobject surface) {
    std::lock_guard<std::mutex> guard(g_lock);
    releaseWindowLocked();
    if (surface) {
        g_window = ANativeWindow_fromSurface(env, surface);
        LOGI("Surface attached");
    } else {
        LOGI("Surface detached");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lunar_launcher_runtime_LunarGraphicsBridge_nativeInitEgl(
        JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (!g_window) {
        LOGE("EGL init requested without Android Surface");
        return JNI_FALSE;
    }
    if (g_display != EGL_NO_DISPLAY) return JNI_TRUE;

    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return JNI_FALSE;
    }
    EGLint major = 0, minor = 0;
    if (!eglInitialize(g_display, &major, &minor)) {
        LOGE("eglInitialize failed");
        g_display = EGL_NO_DISPLAY;
        return JNI_FALSE;
    }

    const EGLint attrs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };
    EGLConfig config = nullptr;
    EGLint count = 0;
    if (!eglChooseConfig(g_display, attrs, &config, 1, &count) || count == 0) {
        LOGE("No EGL ES3 config");
        eglTerminate(g_display);
        g_display = EGL_NO_DISPLAY;
        return JNI_FALSE;
    }

    const EGLint ctxAttrs[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    g_context = eglCreateContext(g_display, config, EGL_NO_CONTEXT, ctxAttrs);
    if (g_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        eglTerminate(g_display);
        g_display = EGL_NO_DISPLAY;
        return JNI_FALSE;
    }

    g_surface = eglCreateWindowSurface(g_display, config, g_window, nullptr);
    if (g_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        eglDestroyContext(g_display, g_context);
        g_context = EGL_NO_CONTEXT;
        eglTerminate(g_display);
        g_display = EGL_NO_DISPLAY;
        return JNI_FALSE;
    }

    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("eglMakeCurrent failed");
        eglDestroySurface(g_display, g_surface);
        eglDestroyContext(g_display, g_context);
        eglTerminate(g_display);
        g_surface = EGL_NO_SURFACE;
        g_context = EGL_NO_CONTEXT;
        g_display = EGL_NO_DISPLAY;
        return JNI_FALSE;
    }

    LOGI("EGL initialized %d.%d", major, minor);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_lunar_launcher_runtime_LunarGraphicsBridge_nativeSwap(
        JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (g_display != EGL_NO_DISPLAY && g_surface != EGL_NO_SURFACE)
        eglSwapBuffers(g_display, g_surface);
}

extern "C" JNIEXPORT void JNICALL
Java_com_lunar_launcher_runtime_LunarGraphicsBridge_nativeShutdownEgl(
        JNIEnv*, jclass) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (g_display == EGL_NO_DISPLAY) return;
    eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
    if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
    eglTerminate(g_display);
    g_surface = EGL_NO_SURFACE;
    g_context = EGL_NO_CONTEXT;
    g_display = EGL_NO_DISPLAY;
}
