@echo off
setlocal EnableExtensions DisableDelayedExpansion
title GoodBurger
pushd "%~dp0"
if errorlevel 1 goto location_error

set "GOODBURGER_JAVA=java.exe"
if exist "%JAVA_HOME%\bin\java.exe" set "GOODBURGER_JAVA=%JAVA_HOME%\bin\java.exe"
if exist "windows-runtime\java\bin\java.exe" set "GOODBURGER_JAVA=%CD%\windows-runtime\java\bin\java.exe"

if not exist "windows-runtime\goodburger.jar" goto missing_files
if not exist "windows-runtime\javafx\javafx-fxml-21.0.6-win.jar" goto missing_files
if not exist "data\menu.csv" goto missing_files

"%GOODBURGER_JAVA%" -version >nul 2>&1
if errorlevel 1 goto missing_java

echo Starting GoodBurger...
"%GOODBURGER_JAVA%" --enable-preview --module-path "windows-runtime\javafx" --add-modules javafx.controls,javafx.fxml -cp "windows-runtime\goodburger.jar" edu.utsa.cs3443.goodburger.GoodBurgerApplication
set "APP_EXIT=%ERRORLEVEL%"
if "%APP_EXIT%"=="0" goto finished
echo.
echo GoodBurger stopped with exit code %APP_EXIT%.
echo Check the error above. GoodBurger requires 64-bit Java 21 or newer.
pause
goto finished

:missing_files
echo Extract the entire ZIP, then run this file from the extracted GoodBurger folder.
set "APP_EXIT=1"
pause
goto finished

:missing_java
echo Java was not found. Use 64-bit Java 21 or newer.
echo Set JAVA_HOME to your Java installation or put its bin folder on PATH.
set "APP_EXIT=1"
pause
goto finished

:location_error
echo Unable to open the GoodBurger folder. Extract the ZIP before running this file.
pause
exit /b 1

:finished
popd
exit /b %APP_EXIT%
