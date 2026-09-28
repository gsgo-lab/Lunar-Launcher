#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
export JAVA_HOME="${JAVA_HOME:-$PREFIX/lib/jvm/java-17-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"
if ! command -v java >/dev/null 2>&1; then
  echo "Java не найдена. Выполните: pkg install openjdk-17 -y"; exit 1
fi
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle не найден. Выполните: pkg install gradle -y"; exit 1
fi
java -version
gradle assembleDebug --no-daemon
printf '\nAPK: %s\n' "$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
