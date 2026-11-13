@echo off
setlocal
if not exist out mkdir out
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\javac.exe" --release 17 -Xlint:all -d out src\filenest\FileNestApp.java tests\filenest\FileNestManagerTest.java
) else (
  javac --release 17 -Xlint:all -d out src\filenest\FileNestApp.java tests\filenest\FileNestManagerTest.java
)
if errorlevel 1 exit /b 1
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -Djava.awt.headless=true -cp out filenest.FileNestManagerTest
) else (
  java -Djava.awt.headless=true -cp out filenest.FileNestManagerTest
)
