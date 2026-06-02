@echo off
title Smart Queue Web System v4.0
echo Smart Queue Management System v4.0 - Web Edition
echo =================================================
echo Starting XAMPP MySQL? Run setup.sql in phpMyAdmin first!
echo.
echo Compiling Java...
javac -cp ".;mysql-connector-j-9.6.0.jar" *.java
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Compile failed! Check javac output.
    pause
    exit /b %ERRORLEVEL%
)
echo [OK] Compiled successfully.
echo.
echo Starting web server on http://localhost:8080/ ...
java -cp ".;mysql-connector-j-9.6.0.jar" QueueApp
echo.
echo Server stopped. Press any key to exit.
pause >nul

