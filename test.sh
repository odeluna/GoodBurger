#!/usr/bin/env bash
set -euo pipefail
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$project_dir/build.sh"
mkdir -p "$project_dir/build/test-classes"
javac --module-path "$project_dir/lib" --add-modules javafx.controls \
  -cp "$project_dir/build/rewards-javafx.jar" \
  -d "$project_dir/build/test-classes" \
  "$project_dir"/src/test/java/rewards/*.java
java --module-path "$project_dir/lib" --add-modules javafx.controls \
  -cp "$project_dir/build/rewards-javafx.jar:$project_dir/build/test-classes" \
  rewards.LogicTest
