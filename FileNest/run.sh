#!/usr/bin/env sh
set -eu
mkdir -p out
javac -Xlint:all -d out src/filenest/FileNestApp.java
java -cp out filenest.FileNestApp
