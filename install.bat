@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem Installs build\chess.apk on a connected device/emulator and launches it.

set "SDK="
if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK if exist "C:\Android\Sdk" set "SDK=C:\Android\Sdk"
if not defined SDK (
    echo ERROR: Android SDK not found. Set ANDROID_HOME.
    exit /b 1
)
if not exist build\chess.apk (
    echo ERROR: build\chess.apk not found. Run build.bat first.
    exit /b 1
)

"%SDK%\platform-tools\adb.exe" devices
"%SDK%\platform-tools\adb.exe" install -r build\chess.apk
if errorlevel 1 (
    echo.
    echo Install failed. Is a device connected and USB debugging enabled?
    exit /b 1
)
"%SDK%\platform-tools\adb.exe" shell am start -n org.sightlesscoders.chess/.MainActivity
exit /b 0
