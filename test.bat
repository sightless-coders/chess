@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

rem Runs the desktop test suite for the chess engine (perft, rules, AI).
rem Needs any JDK 11+; finds it the same way build.bat does.

set "JDK="
rem Android Studio's bundled JBR is always recent enough for --release 8.
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\javac.exe" set "JDK=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JDK if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "JDK=%JAVA_HOME%"
if not defined JDK (
    for /f "delims=" %%j in ('where javac 2^>nul') do (
        if not defined JDK (
            pushd "%%~dpj.."
            set "JDK=!CD!"
            popd
        )
    )
)
if not defined JDK (
    echo ERROR: no JDK found. Install a JDK or set JAVA_HOME.
    exit /b 1
)

if exist build_test rmdir /s /q build_test
mkdir build_test

dir /s /b src\org\sightlesscoders\chess\core\*.java > build_test\sources.txt
dir /s /b test\*.java >> build_test\sources.txt

echo Compiling...
"%JDK%\bin\javac.exe" --release 8 -encoding UTF-8 -d build_test @build_test\sources.txt
if errorlevel 1 exit /b 1

echo.
"%JDK%\bin\java.exe" -cp build_test org.sightlesscoders.chess.core.PerftTest
exit /b %ERRORLEVEL%
