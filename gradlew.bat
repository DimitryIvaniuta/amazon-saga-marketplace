@echo off
setlocal
set VERSION=9.5.1
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
set BASE=%USERPROFILE%\.gradle\bootstrap\gradle-%VERSION%
if not exist "%BASE%\bin\gradle.bat" (
  if not exist "%USERPROFILE%\.gradle\bootstrap" mkdir "%USERPROFILE%\.gradle\bootstrap"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $zip='%USERPROFILE%\.gradle\bootstrap\gradle-%VERSION%-bin.zip'; Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile $zip; Expand-Archive -Path $zip -DestinationPath '%USERPROFILE%\.gradle\bootstrap' -Force"
  if errorlevel 1 exit /b 1
)
call "%BASE%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
