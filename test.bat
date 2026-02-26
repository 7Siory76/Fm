@echo off
setlocal enabledelayedexpansion

set SRC_DIR=src\main\java
set BUILD_DIR=build\classes
set LIB_DIR=lib

if exist %BUILD_DIR% rd /s /q %BUILD_DIR%
mkdir %BUILD_DIR%

set CLASSPATH=
for %%f in (%LIB_DIR%\*.jar) do (
    set CLASSPATH=!CLASSPATH!;"%%f"
)

echo Compilation...
set SOURCES=
for /r %SRC_DIR% %%f in (*.java) do (
    set SOURCES=!SOURCES! "%%f"
)

javac -d %BUILD_DIR% -sourcepath %SRC_DIR% -cp !CLASSPATH! %SOURCES%
if %errorlevel% neq 0 (
    echo Erreur de compilation.
    exit /b %errorlevel%
)
echo Compilation OK.

echo.
echo Lancement de test.Main...
java -cp "%BUILD_DIR%;%LIB_DIR%\servlet-api.jar" test.Main
