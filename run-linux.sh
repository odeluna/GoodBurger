#!/usr/bin/env bash
# GoodBurger launcher for Linux users.
#
# Requires a 64-bit JDK 21 or newer on PATH (or set JAVA_HOME).
# Uses the compiled app jar plus the OpenJFX 21.0.6 Linux jars that Maven
# downloaded into ~/.m2 (any build run populates them). Set
# GOODBURGER_JAVAFX_DIR to a folder containing the four JavaFX Linux jars
# (javafx-{base,graphics,controls,fxml}-21.0.6-linux.jar) to override.
set -euo pipefail
cd -- "$(dirname -- "$0")"

# --- Find Java ---------------------------------------------------------------
JAVA_BIN="java"
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
fi
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "Java was not found. Install 64-bit Java 21 or newer." >&2
  exit 1
fi

VERSION_LINE="$("$JAVA_BIN" -version 2>&1 | head -n1)"
VERSION_NUM="$(echo "$VERSION_LINE" | sed -n 's/.*version="\{0,1\}\([0-9][0-9]*\)\..*/\1/p')"
if [[ -n "$VERSION_NUM" && "$VERSION_NUM" -lt 21 ]]; then
  echo "Java 21 or newer is required (found: $VERSION_LINE)." >&2
  exit 1
fi

# --- Find the app jar --------------------------------------------------------
JAR="windows-runtime/goodburger.jar"
if [[ ! -f "$JAR" ]]; then
  JAR="target/GoodBurger-1.0-SNAPSHOT.jar"
fi
if [[ ! -f "$JAR" ]]; then
  echo "No compiled app found. Building with Maven first..."
  mvn -q clean package
  JAR="target/GoodBurger-1.0-SNAPSHOT.jar"
fi

# --- Find the Linux JavaFX jars ----------------------------------------------
MODULE_PATH="${GOODBURGER_JAVAFX_DIR:-}"
if [[ -z "$MODULE_PATH" ]]; then
  M2="$HOME/.m2/repository/org/openjfx"
  FX_JARS=()
  for m in javafx-base javafx-graphics javafx-controls javafx-fxml; do
    j="$(ls -1 "$M2/$m"/*/"$m"-*-linux.jar 2>/dev/null | head -n1 || true)"
    if [[ -z "$j" || ! -f "$j" ]]; then
      echo "Could not find the $m Linux jar under $M2." >&2
      echo "Run ./build.sh once so Maven downloads the JavaFX jars, then re-run." >&2
      exit 1
    fi
    FX_JARS+=("$j")
  done
  MODULE_PATH="$(IFS=':'; echo "${FX_JARS[*]}")"
fi

echo "Starting GoodBurger (Linux)..."
exec "$JAVA_BIN" \
  --module-path "$MODULE_PATH" \
  --add-modules javafx.controls,javafx.fxml \
  -cp "$JAR" \
  edu.utsa.cs3443.goodburger.GoodBurgerApplication
