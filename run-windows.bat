@echo off
setlocal EnableExtensions
cd /d "%~dp0"

REM =========================================================================
REM  GoodBurger (Rewards) JavaFX -- Windows runner
REM  Downloads the Windows JavaFX runtime jars (if missing), compiles the
REM  sources, builds the app jar, and launches it. Needs a JDK 21+ on PATH
REM  (javac + java). No other dependencies.
REM =========================================================================

echo [run-windows] Checking for a Java compiler...
where javac >nul 2>nul
if errorlevel 1 (
    echo [run-windows] ERROR: javac was not found on PATH.
    echo             Install a JDK 21 or newer ^(e.g. Temurin 21^) and put it on PATH,
    echo             then run this batch again.
    exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
    echo [run-windows] ERROR: java was not found on PATH. Install a JDK, then retry.
    exit /b 1
)

echo [run-windows] Ensuring the Windows JavaFX runtime jars are present...
if not exist "lib-win" mkdir "lib-win"
set "BASE=https://repo1.maven.org/maven2/org/openjfx"
set "JARS=base graphics controls fxml"
for %%A in (%JARS%) do (
    if not exist "lib-win\javafx-%%A-26-win.jar" (
        echo [run-windows]   fetching javafx-%%A-26-win.jar ...
        powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing -Uri '%BASE%/javafx-%%A/26/javafx-%%A-26-win.jar' -OutFile 'lib-win\javafx-%%A-26-win.jar'"
        if errorlevel 1 (
            echo [run-windows] ERROR: could not download javafx-%%A-26-win.jar
            echo             (check your internet connection / firewall).
            exit /b 1
        )
    )
)

echo [run-windows] Compiling sources...
if not exist "build\classes" mkdir "build\classes"
javac --module-path "lib-win" --add-modules javafx.controls -encoding UTF-8 ^
  -d "build\classes" "src\main\java\rewards\*.java"
if errorlevel 1 (
    echo [run-windows] ERROR: compilation failed. See messages above.
    exit /b 1
)

echo [run-windows] Building rewards-javafx.jar...
jar --create --file "build\rewards-javafx.jar" --main-class rewards.RewardsApp ^
  -C "build\classes" . -C "src\main\resources" .
if errorlevel 1 (
    echo [run-windows] ERROR: jar creation failed.
    exit /b 1
)

echo [run-windows] Launching the GoodBurger Rewards app...
java --module-path "lib-win" --add-modules javafx.controls ^
  --enable-native-access=javafx.graphics ^
  -jar "build\rewards-javafx.jar" %*
exit /b %errorlevel%
