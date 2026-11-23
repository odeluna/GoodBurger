#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
mvn test-compile
mvn -q dependency:build-classpath -Dmdep.outputFile=target/test-cp.txt
CP="target/test-classes:target/classes:$(cat target/test-cp.txt)"
java -cp "$CP" rewards.LogicTest
java -cp "$CP" edu.utsa.cs3443.goodburger.ModelTest
