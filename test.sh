#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
mvn test-compile
exec java --enable-preview -cp target/test-classes:target/classes rewards.LogicTest
