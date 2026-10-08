@echo off
set DIRNAME=%~dp0
if exist "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" goto execute
echo gradle-wrapper.jar missing. Run: gradle wrapper --gradle-version 8.14.5
exit /b 1
:execute
java -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
