@echo off
setlocal enabledelayedexpansion

REM Build Debug APK (Windows)
REM Usage: double-click or run from cmd/PowerShell.

cd /d %~dp0

if exist gradlew.bat (
  call gradlew.bat clean assembleDebug
) else (
  echo gradlew.bat not found. Make sure you are in the eMRTDReader root.
  exit /b 1
)

if %ERRORLEVEL% NEQ 0 (
  echo Build failed.
  exit /b %ERRORLEVEL%
)

echo.
echo Build OK. APK:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
endlocal
