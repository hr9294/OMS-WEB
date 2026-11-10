@echo off
title OMS-WEB Manual Desktop Mode (Visible Chrome)
echo ==============================================================================
echo        LAUNCHING OMS-WEB MANUAL DESKTOP MODE (VISIBLE CHROME)
echo ==============================================================================
echo Environment: Local Windows Desktop (No Docker, No CI servers needed)
echo Browser: Google Chrome will open maximized on your desktop screen.
echo.

cd /d "D:\OMS-WEB"

:: Ensure CI environment variable is NOT set so browser opens visible and session stays active
set CI=
set HEADLESS=

mvn exec:java -Dexec.mainClass="OMSwebmain"

pause
