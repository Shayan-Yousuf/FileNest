@echo off
if not exist out mkdir out
javac -Xlint:all -d out src\filenest\FileNestApp.java
if errorlevel 1 exit /b 1
java -cp out filenest.FileNestApp
