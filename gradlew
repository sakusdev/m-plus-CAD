#!/bin/sh
# SPDX-License-Identifier: Apache-2.0
# Bootstrap the official Gradle 9.5.1 wrapper JAR when a binary checkout is unavailable.

set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
WRAPPER_DIR="$APP_HOME/gradle/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v9.5.1/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_GIT_BLOB_SHA="b1b8ef56b44f16b14dc800fa8103a6d89abb526f"

if [ ! -f "$WRAPPER_JAR" ]; then
    mkdir -p "$WRAPPER_DIR"
    TMP="$WRAPPER_JAR.tmp.$$"
    trap 'rm -f "$TMP"' EXIT HUP INT TERM
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --silent --show-error "$WRAPPER_URL" --output "$TMP"
    elif command -v wget >/dev/null 2>&1; then
        wget --quiet "$WRAPPER_URL" --output-document="$TMP"
    else
        echo "curl or wget is required to bootstrap gradle-wrapper.jar" >&2
        exit 1
    fi
    if ! command -v git >/dev/null 2>&1; then
        echo "git is required to verify gradle-wrapper.jar" >&2
        exit 1
    fi
    ACTUAL_SHA=$(git hash-object "$TMP")
    if [ "$ACTUAL_SHA" != "$WRAPPER_GIT_BLOB_SHA" ]; then
        echo "gradle-wrapper.jar verification failed: expected $WRAPPER_GIT_BLOB_SHA, got $ACTUAL_SHA" >&2
        exit 1
    fi
    mv "$TMP" "$WRAPPER_JAR"
    trap - EXIT HUP INT TERM
fi

if [ -n "${JAVA_HOME:-}" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi

exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
