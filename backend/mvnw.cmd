@echo off
setlocal
set "BASE=%~dp0"
if "%BASE:~-1%"=="\" set "BASE=%BASE:~0,-1%"
set "JAR=%BASE%\.mvn\wrapper\maven-wrapper.jar"
if not exist "%JAR%" (
  if not exist "%BASE%\.mvn\wrapper" mkdir "%BASE%\.mvn\wrapper"
  powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar' -OutFile '%JAR%'"
)
java %MAVEN_OPTS% -classpath "%JAR%" "-Dmaven.multiModuleProjectDirectory=%BASE%" org.apache.maven.wrapper.MavenWrapperMain %*
if errorlevel 1 exit /b 1
endlocal
