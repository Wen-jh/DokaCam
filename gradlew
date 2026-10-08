#!/bin/sh
# Gradle Wrapper 启动脚本（标准模板）
# 若 gradle-wrapper.jar 缺失，Android Studio 会自动补全；CLI 用户执行:
#   gradle wrapper --gradle-version 8.14.5
APP_HOME=$(cd "$(dirname "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$CLASSPATH" ]; then
  echo "gradle-wrapper.jar 缺失，请先用本机 gradle 生成: gradle wrapper --gradle-version 8.14.5"
  exit 1
fi
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
