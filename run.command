#!/usr/bin/env bash
# Convenience launcher for macOS: double-click in Finder to run.
# Forces the script to run from its own directory so the Gradle wrapper
# resolves correctly even when launched from outside Terminal.

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
./gradlew run
