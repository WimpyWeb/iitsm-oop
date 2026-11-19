@echo off
setlocal
set "BROWSER_EXE="
if not defined BROWSER_EXE if exist "C:\Program Files\Google\Chrome\Application\chrome.exe" set "BROWSER_EXE=C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
if not defined BROWSER_EXE if exist "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" set "BROWSER_EXE=C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
if not defined BROWSER_EXE if exist "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" set "BROWSER_EXE=C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
if not defined BROWSER_EXE if exist "C:\Program Files\Microsoft\Edge\Application\msedge.exe" set "BROWSER_EXE=C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
set "PPTR_CFG=%TEMP%\iitsm-puppeteer.json"
if defined BROWSER_EXE > "%PPTR_CFG%" echo {"headless":true,"executablePath":"%BROWSER_EXE%"}
if not defined BROWSER_EXE > "%PPTR_CFG%" echo {}
for %%f in (architecture use-case class-domain class-state class-strategy class-observer-notifier er-diagram) do (
  echo Rendering docs\img\%%f.png ...
  call npx -y @mermaid-js/mermaid-cli --size 1600 -b white --puppeteerConfigFile "%PPTR_CFG%" -i "%~dp0img\%%f.mmd" -o "%~dp0img\%%f.png"
  if errorlevel 1 (
    del /q "%PPTR_CFG%" 2>nul
    exit /b 1
  )
)
del /q "%PPTR_CFG%" 2>nul
echo All 7 Mermaid diagrams rendered to docs\img\*.png
