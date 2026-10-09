@echo off
setlocal EnableExtensions
cd /d "%~dp0"

rem Builds the Windows (NVGT) version of Chess and runs the engine self-test.
rem Requires NVGT installed (https://nvgt.gg) - C:\nvgt by default.

set "NVGT=C:\nvgt\nvgt.exe"
if not exist "%NVGT%" (
    set "NVGT="
    for /f "delims=" %%v in ('where nvgt.exe 2^>nul') do if not defined NVGT set "NVGT=%%v"
)
if not defined NVGT (
    echo ERROR: nvgt.exe not found. Install NVGT or set its path in this script.
    exit /b 1
)
echo NVGT: %NVGT%
echo.

echo [1/3] Compiling chess_selftest...
"%NVGT%" -c chess_selftest.nvgt
if errorlevel 1 goto :Fail
powershell -NoProfile -Command "Expand-Archive -Force chess_selftest.zip ."
if errorlevel 1 goto :Fail

echo [2/3] Compiling chess...
"%NVGT%" -c chess.nvgt
if errorlevel 1 goto :Fail
powershell -NoProfile -Command "Expand-Archive -Force chess.zip ."
if errorlevel 1 goto :Fail

echo [3/3] Running engine self-test...
if exist chess_selftest.exe (
    .\chess_selftest.exe
    if errorlevel 1 goto :SelftestFail
) else (
    echo WARNING: chess_selftest.exe was not produced; skipping the run.
)

echo.
echo ============================================
echo   BUILD OK
echo   Game:   %CD%\chess.exe
echo   Selftest: passed
echo ============================================
exit /b 0

:SelftestFail
echo.
echo SELFTEST FAILED - see the output above.
exit /b 1

:Fail
echo.
echo BUILD FAILED - see the compiler errors above.
exit /b 1
