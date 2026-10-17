#!/usr/bin/env bash
# Builds a native app bundle with jpackage (PLAN.md Phase 8: "packaging
# (jpackage for a native bundle per OS)").
#
# `mvn package` produces target/sketcher-*.jar with a Class-Path manifest
# pointing at target/libs/*.jar (staged there by maven-dependency-plugin).
# jpackage bundles that jar plus a full JDK runtime image and runs it via
# the classpath, so this works without needing javafx jmods.
#
# Usage: ./jpackage.sh [jpackage-type]
#   jpackage-type defaults to "app-image" (a runnable app folder, no
#   installer, works on every platform with no extra tooling). Pass an
#   installer type your platform supports instead, e.g.:
#     Linux:   deb | rpm
#     macOS:   dmg | pkg
#     Windows: msi | exe
set -euo pipefail
cd "$(dirname "$0")"

TYPE="${1:-app-image}"
APP_NAME="Sketcher"
DEST="target/dist"

echo "==> mvn package"
mvn -q package -DskipTests

MAIN_JAR=$(find target -maxdepth 1 -name 'sketcher-*.jar' ! -name '*sources*' | head -1)
if [ -z "$MAIN_JAR" ]; then
  echo "Could not find target/sketcher-*.jar — did 'mvn package' succeed?" >&2
  exit 1
fi

VERSION=$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>([^<]*)<\/version>.*/\1/' | sed 's/-SNAPSHOT//')
[ -z "$VERSION" ] && VERSION="1.0.0"

echo "==> jpackage --type $TYPE (app version $VERSION)"
rm -rf "$DEST"
mkdir -p "$DEST"

jpackage \
  --input target \
  --main-jar "$(basename "$MAIN_JAR")" \
  --main-class com.sketcher.Launcher \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --dest "$DEST" \
  --type "$TYPE"

echo "==> done: $DEST"
