@ECHO OFF
REM Maven Wrapper bootstrap for Windows.
REM Downloads maven-wrapper.jar on first use (requires network), then launches Maven.

SETLOCAL EnableDelayedExpansion
SET WRAPPER_JAR=.mvn\wrapper\maven-wrapper.jar
SET WRAPPER_PROPS=.mvn\wrapper\maven-wrapper.properties

IF NOT EXIST "%WRAPPER_JAR%" (
  ECHO Downloading Maven wrapper jar...
  FOR /F "tokens=2 delims==" %%U IN ('FINDSTR /B "wrapperUrl" "%WRAPPER_PROPS%"') DO SET WRAPPER_URL=%%U
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '!WRAPPER_URL!' -OutFile '!WRAPPER_JAR!'"
  IF ERRORLEVEL 1 (
    ECHO Failed to download Maven wrapper jar. Check network and wrapperUrl in %WRAPPER_PROPS%.
    EXIT /B 1
  )
)

java -Dmaven.multiModuleProjectDirectory="%CD%" -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
