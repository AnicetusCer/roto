#!/bin/sh

# Minimal Gradle wrapper launcher for POSIX environments.

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_DIR="$SCRIPT_DIR/gradle/wrapper"
CLASSPATH="$WRAPPER_DIR/gradle-wrapper.jar"
CLASSPATH="$CLASSPATH:$WRAPPER_DIR/gradle-wrapper-shared.jar"
CLASSPATH="$CLASSPATH:$WRAPPER_DIR/gradle-cli.jar"
CLASSPATH="$CLASSPATH:$WRAPPER_DIR/gradle-functional.jar"
CLASSPATH="$CLASSPATH:$WRAPPER_DIR/gradle-files.jar"
CLASSPATH="$CLASSPATH:$WRAPPER_DIR/gradle-base-annotations.jar"

if [ -n "$JAVA_HOME" ]; then
  JAVA_EXEC="$JAVA_HOME/bin/java"
else
  JAVA_EXEC="java"
fi

exec "$JAVA_EXEC" -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
