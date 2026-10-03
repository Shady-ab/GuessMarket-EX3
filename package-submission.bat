@echo off
setlocal
cd /d "%~dp0"

set SUB=submission\GuessMarket-EX3-325556835-213726094
set ZIP=GuessMarket-EX3-325556835-213726094.zip

echo === 1/4 building ===
call build.bat
if errorlevel 1 goto fail
if not exist dist\GuessMarket.war goto nobuild
if not exist dist\GuessMarketClient\GuessMarketClient.jar goto nobuild
if not exist dist\GuessMarketClient\lib\javafx.controls.jar (
  echo *** STOP: JavaFX jars are missing. Run download-javafx.bat first.
  goto fail
)

echo === 2/4 readme ===
if not exist readme.pdf (
  powershell -NoProfile -ExecutionPolicy Bypass -File make-readme.ps1
)
if not exist readme.pdf if not exist readme.docx (
  echo *** STOP: readme.docx / readme.pdf are missing. Run make-readme.ps1 on a machine with Word.
  goto fail
)

echo === 3/4 collecting files ===
if exist submission rmdir /s /q submission
mkdir "%SUB%"
copy /y dist\GuessMarket.war "%SUB%\" >nul
xcopy /e /i /q /y dist\GuessMarketClient "%SUB%\GuessMarketClient" >nul
xcopy /e /i /q /y samples "%SUB%\samples" >nul
if exist readme.docx copy /y readme.docx "%SUB%\" >nul
if exist readme.pdf copy /y readme.pdf "%SUB%\" >nul

echo === 4/4 zipping ===
if exist "%ZIP%" del "%ZIP%"
powershell -NoProfile -Command "Compress-Archive -Path '%SUB%' -DestinationPath '%ZIP%' -Force"
if not exist "%ZIP%" goto fail

echo.
echo Done. Upload this file:
echo   %~dp0%ZIP%
goto end

:nobuild
echo *** STOP: the build did not produce GuessMarket.war and the client jars.
goto fail

:fail
echo.
echo Packaging aborted.
pause
exit /b 1

:end
endlocal
