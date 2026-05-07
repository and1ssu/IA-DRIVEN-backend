#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

rm -rf out
find src/main/java -name "*.java" > sources.txt
javac -encoding UTF-8 -d out @sources.txt
rm sources.txt

java -cp out com.centralpedidos.Application
