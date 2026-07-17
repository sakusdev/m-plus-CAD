@rem SPDX-License-Identifier: Apache-2.0
@echo off
setlocal
set APP_HOME=%~dp0
set WRAPPER_DIR=%APP_HOME%gradle\wrapper
set WRAPPER_JAR=%WRAPPER_DIR%\gradle-wrapper.jar
set WRAPPER_URL=https://raw.githubusercontent.com/gradle/gradle/v9.5.1/gradle/wrapper/gradle-wrapper.jar
set WRAPPER_GIT_BLOB_SHA=b1b8ef56b44f16b14dc800fa8103a6d89abb526f

if not exist "%WRAPPER_JAR%" (
  if not exist "%WRAPPER_DIR%" mkdir "%WRAPPER_DIR%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%.tmp'"
  if errorlevel 1 exit /b 1
  for /f %%H in ('git hash-object "%WRAPPER_JAR%.tmp"') do set ACTUAL_SHA=%%H
  if /I not "%ACTUAL_SHA%"=="%WRAPPER_GIT_BLOB_SHA%" (
    echo gradle-wrapper.jar verification failed: expected %WRAPPER_GIT_BLOB_SHA%, got %ACTUAL_SHA% 1>&2
    del "%WRAPPER_JAR%.tmp"
    exit /b 1
  )
  move /Y "%WRAPPER_JAR%.tmp" "%WRAPPER_JAR%" >nul
)

if defined JAVA_HOME (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
) else (
  set JAVACMD=java.exe
)

"%JAVACMD%" -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
