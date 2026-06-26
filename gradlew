#!/bin/sh
# Gradle wrapper startup script — simplified for macOS/Linux
set -e

# Resolve the real directory of this script
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")/"$link"
    fi
done
APP_HOME=$(cd "$(dirname "$PRG")" && pwd -P)
APP_BASE_NAME=$(basename "$0")

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Find java
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "ERROR: java not found. Set JAVA_HOME or add java to PATH." >&2
    exit 1
fi

exec "$JAVACMD" \
    -Xmx512m \
    -Xms64m \
    -classpath "$CLASSPATH" \
    -Dorg.gradle.appname="$APP_BASE_NAME" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
