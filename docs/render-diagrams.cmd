@echo off
setlocal
for %%f in (architecture use-case class-domain class-state class-strategy class-observer-notifier er-diagram) do (
  echo Rendering docs\img\%%f.png ...
  call npx -y @mermaid-js/mermaid-cli -w 1600 -b white -i "%~dp0img\%%f.mmd" -o "%~dp0img\%%f.png"
  if errorlevel 1 exit /b 1
)
echo All 7 Mermaid diagrams rendered to docs\img\*.png
