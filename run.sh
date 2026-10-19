#!/usr/bin/env bash
set -euo pipefail
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ ! -f "$project_dir/build/rewards-javafx.jar" ]; then
  "$project_dir/build.sh"
fi
exec java --enable-native-access=javafx.graphics \
  --module-path "$project_dir/lib" --add-modules javafx.controls \
  -jar "$project_dir/build/rewards-javafx.jar" "$@"
