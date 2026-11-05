@echo off
setlocal
echo =========================================================================
echo UFTFAST OMS - Background Processing Engine
echo =========================================================================

if "%1"=="" (
    echo Available Background Tasks:
    echo   1. active-client-sync          (ActiveClientsDataSync)
    echo   2. incremental-transfer        (OMSweb_AutonomousIncrementalTransfer)
    echo   3. db-marketdata               (OMSweb_DBMarketData)
    echo   4. dual-file-transfer          (OMSweb_DualFileTransfer)
    echo   5. export-data                 (OMSweb_ExportData)
    echo   6. multidb-sync                (OMSweb_MultiDBSync)
    echo   7. upload-data                 (OMSweb_UploadData)
    echo   8. sync-dual-tables            (Direct DB Dual Sync)
    echo   9. audit-file-manager          (Live File Manager Audit)
    echo  10. all                         (Run Complete Background Pipeline)
    echo.
    echo Running default: all
    python "%~dp0background_processor.py" all
) else (
    python "%~dp0background_processor.py" %1
)
endlocal
