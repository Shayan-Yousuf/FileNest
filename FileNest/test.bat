@echo off
if not exist out mkdir out
javac -Xlint:all -d out src\filenest\FileNestApp.java tests\filenest\FileNestManagerTest.java
if errorlevel 1 exit /b 1
java -Djava.awt.headless=true -cp out filenest.FileNestManagerTest
