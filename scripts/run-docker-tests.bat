@echo off
title OMS-WEB Docker Container Test Runner
echo ==============================================================================
echo        LAUNCHING OMS-WEB TESTS IN DOCKER CONTAINER
echo ==============================================================================

cd /d "D:\OMS-WEB"

if not exist exported-trades mkdir exported-trades

echo [1/2] Building Docker Image...
docker compose build oms-tests

echo [2/2] Running Container...
docker compose run --rm oms-tests

echo [COMPLETE] Container finished. Exported trade files saved in D:\OMS-WEB\exported-trades
pause
