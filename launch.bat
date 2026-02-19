@echo off
setlocal enabledelayedexpansion

REM Nom du jar à créer
set JAR_FILE=framework.jar

REM Dossiers source et build
set SRC_DIR=src\main\java
set BUILD_DIR=build\classes
set LIB_DIR=lib
set SERVLET_JAR=%LIB_DIR%\servlet-api.jar
set DEST_FOLDER="..\FMtest\lib"

REM Créer le dossier build s'il n'existe pas
if exist %BUILD_DIR% rd /s /q %BUILD_DIR%
mkdir %BUILD_DIR%

REM Construire le classpath
set CLASSPATH="%SERVLET_JAR%"
for %%f in (%LIB_DIR%\*.jar) do (
    set CLASSPATH=!CLASSPATH!;"%%f"
)

REM Compilation des fichiers Java
echo Compilation des fichiers Java...
set SOURCES=
for /r %SRC_DIR% %%f in (*.java) do (
    set SOURCES=!SOURCES! "%%f"
)

javac -d %BUILD_DIR% -sourcepath %SRC_DIR% -cp !CLASSPATH! %SOURCES%
if %errorlevel% neq 0 (
    echo Erreur de compilation.
    exit /b %errorlevel%
)

REM Création du jar
echo Création du jar : %JAR_FILE%...
jar -cvf %JAR_FILE% -C %BUILD_DIR% .

echo Jar créé avec succès : %JAR_FILE%


REM Déploiement du fichier WAR dans lib
echo Déploiement de %JAR_FILE% dans lib...
xcopy %JAR_FILE% %DEST_FOLDER% /Y