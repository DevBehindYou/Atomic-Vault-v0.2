#!/usr/bin/env bash
# Fails on unused Kotlin imports (TASKS T10). Uses ktlint with only its
# no-unused-imports rule; the jar is pinned and checked against its SHA-256.
# Fix locally with the same command plus --format.
set -euo pipefail
VERSION=1.8.0
SHA256=369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab
JAR="${RUNNER_TEMP:-/tmp}/ktlint-$VERSION.jar"
if [ ! -f "$JAR" ]; then
  curl -sSfL --retry 5 --retry-delay 10 --retry-all-errors -o "$JAR" "https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/$VERSION/ktlint-cli-$VERSION-all.jar"
fi
echo "$SHA256  $JAR" | sha256sum -c -
java -jar "$JAR" --editorconfig="$(dirname "$0")/ktlint-unused-imports.editorconfig" "app/src/**/*.kt" "autofilltest/src/**/*.kt"
echo "No unused imports"
