#!/usr/bin/env sh
set -eu
mkdir -p out
javac -Xlint:all -d out src/filenest/FileNestApp.java tests/filenest/FileNestManagerTest.java
java -Djava.awt.headless=true -cp out filenest.FileNestManagerTest
