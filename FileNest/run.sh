#!/usr/bin/env sh
set -eu
mkdir -p out
JAVAC="\${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="\${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVAC" --release 17 -Xlint:all -d out src/filenest/FileNestApp.java
"$JAVA" -cp out filenest.FileNestApp
