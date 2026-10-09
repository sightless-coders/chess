@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================
echo   Chess - Android APK build
echo ============================================
echo.

rem ---- 1. Locate the Android SDK ------------------------------------------------
set "SDK="
if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK if exist "%USERPROFILE%\AppData\Local\Android\Sdk" set "SDK=%USERPROFILE%\AppData\Local\Android\Sdk"
if not defined SDK if exist "C:\Android\Sdk" set "SDK=C:\Android\Sdk"
if not defined SDK goto :NoSdk
if not exist "%SDK%" goto :NoSdk

rem Newest platform (android.jar).
set "ANDROID_JAR="
set "BESTMAJ=0"
if exist "%SDK%\platforms" (
    for /f "delims=" %%d in ('dir /b /ad "%SDK%\platforms" 2^>nul') do (
        set "PVER=%%d"
        set "PVER=!PVER:android-=!"
        for /f "tokens=1 delims=." %%n in ("!PVER!") do set "PMAJ=%%n"
        if exist "%SDK%\platforms\%%d\android.jar" (
            if !PMAJ! GTR !BESTMAJ! (
                set "BESTMAJ=!PMAJ!"
                set "ANDROID_JAR=%SDK%\platforms\%%d\android.jar"
            )
        )
    )
)
if not defined ANDROID_JAR goto :NoPlatform

rem Newest build-tools.
set "BT="
set "BESTBT=0"
if exist "%SDK%\build-tools" (
    for /f "delims=" %%d in ('dir /b /ad "%SDK%\build-tools" 2^>nul') do (
        set "BVER=%%d"
        for /f "tokens=1 delims=." %%n in ("!BVER!") do set "BMAJ=%%n"
        if exist "%SDK%\build-tools\%%d\aapt2.exe" (
            if !BMAJ! GTR !BESTBT! (
                set "BESTBT=!BMAJ!"
                set "BT=%SDK%\build-tools\%%d"
            )
        )
    )
)
if not defined BT goto :NoBuildTools

echo SDK:       %SDK%
echo Platform:  !ANDROID_JAR!
echo Build out: !BT!

rem ---- 2. Locate a JDK (11+ is needed for d8 and apksigner) ---------------------
set "JDK="
set "JDKVER=0"
call :PickJava
if not defined JDK goto :NoJdk
if !JDKVER! LSS 11 goto :JdkTooOld
set "JAVA_HOME=!JDK!"
echo JDK:       !JDK! ^(Java !JDKVER!^)
echo.

rem ---- 3. Build -----------------------------------------------------------------
set "OUT=build"
if exist "!OUT!" rmdir /s /q "!OUT!"
mkdir "!OUT!"
mkdir "!OUT!\classes"

echo [1/7] Compiling resources...
if exist "res" (
    "!BT!\aapt2.exe" compile --dir res -o "!OUT!\res.zip" || goto :Fail
)

echo [2/7] Linking AndroidManifest.xml...
set "RESZIP="
if exist "!OUT!\res.zip" set "RESZIP=!OUT!\res.zip"
"!BT!\aapt2.exe" link -o "!OUT!\app-unsigned.apk" -I "!ANDROID_JAR!" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 36 --version-code 1 --version-name "1.0" !RESZIP! || goto :Fail

echo [3/7] Compiling Java sources...
dir /s /b src\*.java > "!OUT!\sources.txt"
"!JDK!\bin\javac.exe" --release 8 -encoding UTF-8 -classpath "!ANDROID_JAR!" -d "!OUT!\classes" @"!OUT!\sources.txt" || goto :Fail

echo [4/7] Creating classes.jar...
"!JDK!\bin\jar.exe" cf "!OUT!\classes.jar" -C "!OUT!\classes" . || goto :Fail

echo [5/7] Converting to DEX...
call "!BT!\d8.bat" --release --min-api 26 --lib "!ANDROID_JAR!" --output "%CD%\!OUT!" "%CD%\!OUT!\classes.jar" || goto :Fail
if not exist "!OUT!\classes.dex" goto :Fail

rem Append the dex file to the unsigned APK.
pushd "!OUT!"
"!JDK!\bin\jar.exe" uf app-unsigned.apk classes.dex
set "JAR_RC=!ERRORLEVEL!"
popd
if not "!JAR_RC!"=="0" goto :Fail

rem Add bundled sound assets (logo + UI cues).
if exist "assets" (
    "!JDK!\bin\jar.exe" uf "!OUT!\app-unsigned.apk" assets || goto :Fail
)

echo [6/7] Aligning and signing...
"!BT!\zipalign.exe" -f -p 4 "!OUT!\app-unsigned.apk" "!OUT!\app-aligned.apk" || goto :Fail

if not exist "!OUT!\debug.keystore" (
    echo        Creating a debug keystore...
    "!JDK!\bin\keytool.exe" -genkeypair -keystore "!OUT!\debug.keystore" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug, O=Android, C=US" || goto :Fail
)
call "!BT!\apksigner.bat" sign --ks "!OUT!\debug.keystore" --ks-pass pass:android --key-pass pass:android --out "!OUT!\chess.apk" "!OUT!\app-aligned.apk" || goto :Fail

echo [7/7] Verifying the signature...
call "!BT!\apksigner.bat" verify --verbose "!OUT!\chess.apk" >nul || goto :Fail

echo.
echo ============================================
echo   BUILD OK
echo   APK: %CD%\build\chess.apk
echo ============================================
echo.
"!BT!\aapt2.exe" dump badging "!OUT!\chess.apk" 2>nul | findstr /i /c:"package:" /c:"application-label:" /c:"launchable-activity:" /c:"sdkVersion:"
echo.
echo Install it with install.bat (device connected via USB/adb).
exit /b 0

rem ---------------------------------------------------------------- errors ----

:Fail
echo.
echo ============================================
echo   BUILD FAILED - see the error above
echo ============================================
exit /b 1

:NoSdk
echo ERROR: Android SDK not found.
echo        Install it via Android Studio, or set ANDROID_HOME to its path.
exit /b 1

:NoPlatform
echo ERROR: No android.jar found under %SDK%\platforms
echo        Install a platform, e.g. "SDK Manager -^> Android 16 (API 36)".
exit /b 1

:NoBuildTools
echo ERROR: No build-tools found under %SDK%\build-tools
echo        Install them via "SDK Manager -^> SDK Tools -^> Android SDK Build-Tools".
exit /b 1

:NoJdk
echo ERROR: No JDK found. Install a JDK 11 or newer (Android Studio's bundled
echo        JBR works), or set JAVA_HOME.
exit /b 1

:JdkTooOld
echo ERROR: The JDK at !JDK! is older than Java 11.
echo        d8 and apksigner need Java 11+. Set JAVA_HOME to a newer JDK.
exit /b 1

rem ------------------------------------------------------------ subroutines ----

rem Pick a JDK >= 11: tries JAVA_HOME, then Android Studio's JBR, then javac on PATH.
rem Sets JDK and JDKVER when successful.
:PickJava
set "JDK="
set "JDKVER=0"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    call :JavaMajor "%JAVA_HOME%\bin\java.exe"
    if !JDKVER! GEQ 11 set "JDK=%JAVA_HOME%"
)
if not defined JDK if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\javac.exe" (
    call :JavaMajor "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
    if !JDKVER! GEQ 11 set "JDK=%ProgramFiles%\Android\Android Studio\jbr"
)
if not defined JDK (
    for /f "delims=" %%j in ('where javac 2^>nul') do (
        if not defined JDK (
            pushd "%%~dpj.."
            set "JDK=!CD!"
            popd
        )
    )
    if defined JDK (
        call :JavaMajor "!JDK!\bin\java.exe"
        if !JDKVER! LSS 11 set "JDK="
    )
)
exit /b 0

rem Read the major Java version of %1 (path to java.exe) into JDKVER.
rem Runs java from its own directory so paths with spaces are safe.
:JavaMajor
set "JDKVER=0"
set "_JV="
if not exist "%~1" exit /b 1
pushd "%~dp1"
for /f "tokens=3" %%v in ('.\java.exe -version 2^>^&1 ^| findstr /i "version"') do if not defined _JV set "_JV=%%v"
popd
if not defined _JV exit /b 1
set "_JV=%_JV:"=%"
for /f "tokens=1,2 delims=." %%a in ("%_JV%") do (
    if "%%a"=="1" (set "JDKVER=%%b") else (set "JDKVER=%%a")
)
exit /b 0
