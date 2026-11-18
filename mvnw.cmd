@echo off
setlocal enabledelayedexpansion
set "BASE_DIR=%~dp0"
set "PROJECT_DIR=%BASE_DIR:~0,-1%"
set "WRAPPER_JAR=%BASE_DIR%.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

if exist "%WRAPPER_JAR%" (
  java "-Dmaven.multiModuleProjectDirectory=%PROJECT_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
  exit /b %ERRORLEVEL%
)

where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  call mvn %*
  exit /b %ERRORLEVEL%
)

where curl >nul 2>nul
if %ERRORLEVEL%==0 (
  if not exist "%BASE_DIR%.mvn\wrapper" mkdir "%BASE_DIR%.mvn\wrapper"
  curl -fsSL "%WRAPPER_URL%" -o "%WRAPPER_JAR%"
  if %ERRORLEVEL%==0 (
    java "-Dmaven.multiModuleProjectDirectory=%PROJECT_DIR%" -classpath "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
    exit /b %ERRORLEVEL%
  )
)

echo Neither mvn nor maven-wrapper.jar is available. >&2
exit /b 1
