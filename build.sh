#!/usr/bin/env bash
set -euo pipefail
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
mkdir -p "$project_dir/build/classes"
javac --module-path "$project_dir/lib" --add-modules javafx.controls \
  -d "$project_dir/build/classes" "$project_dir"/src/main/java/rewards/*.java
jar --create --file "$project_dir/build/rewards-javafx.jar" \
  --main-class rewards.RewardsApp \
  -C "$project_dir/build/classes" . \
  -C "$project_dir/src/main/resources" .
echo "Built $project_dir/build/rewards-javafx.jar"
