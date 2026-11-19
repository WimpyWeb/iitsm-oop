@echo off
setlocal
set "DOC_DIR=%~dp0"
if "%DOC_DIR:~-1%"=="\" set "DOC_DIR=%DOC_DIR:~0,-1%"

del /q "%DOC_DIR%\REPORT.pdf" 2>nul
echo Building Group83_Report.pdf from REPORT.md ...
call npx -y md-to-pdf "%DOC_DIR%\REPORT.md" --config-file "%DOC_DIR%\md-to-pdf.config.js"
if errorlevel 1 exit /b 1

if exist "%DOC_DIR%\REPORT.pdf" move /y "%DOC_DIR%\REPORT.pdf" "%DOC_DIR%\Group83_Report.pdf" >nul

if exist "%DOC_DIR%\Group83_Report.pdf" (
  echo Created "%DOC_DIR%\Group83_Report.pdf"
) else (
  echo ERROR: Group83_Report.pdf was not created.
  exit /b 1
)

where pdftoppm >nul 2>nul
if not errorlevel 1 (
  if not exist "%DOC_DIR%\pdf-pages" mkdir "%DOC_DIR%\pdf-pages"
  del /q "%DOC_DIR%\pdf-pages\page-*.png" 2>nul
  pdftoppm -png -r 150 "%DOC_DIR%\Group83_Report.pdf" "%DOC_DIR%\pdf-pages\page"
  if not errorlevel 1 echo Rasterized PDF pages to "%DOC_DIR%\pdf-pages\page-*.png"
) else (
  echo Note: pdftoppm not found on PATH; skipping PNG rasterization.
)
