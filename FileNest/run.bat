@echo off
setlocal
if not exist out mkdir out
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\javac.exe" --release 17 -Xlint:all -d out src\filenest\FileNestApp.java
) else (
  javac --release 17 -Xlint:all -d out src\filenest\FileNestApp.java
)
if errorlevel 1 exit /b 1
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -cp out filenest.FileNestApp
) else (
  java -cp out filenest.FileNestApp
)
