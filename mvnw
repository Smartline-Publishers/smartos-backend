#!/bin/sh
# Maven Wrapper bootstrap (POSIX).
# Downloads maven-wrapper.jar on first use (requires network), then launches Maven.
set -e

WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPS=".mvn/wrapper/maven-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven wrapper jar..."
  WRAPPER_URL=$(grep '^wrapperUrl' "$WRAPPER_PROPS" | cut -d'=' -f2-)
  if command -v curl >/dev/null 2>&1; then
    curl -fL -o "$WRAPPER_JAR" "$WRAPPER_URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$WRAPPER_JAR" "$WRAPPER_URL"
  else
    echo "Need curl or wget to download the wrapper jar." >&2
    exit 1
  fi
fi

exec java -Dmaven.multiModuleProjectDirectory="$PWD" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
