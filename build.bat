@echo off
setlocal
cd /d "%~dp0"

rem Builds:
rem   dist\GuessMarket.war                - deploy into tomcat\webapps (context path /GuessMarket)
rem   dist\GuessMarketClient\             - JavaFX client: jars + lib\ (JavaFX) + run.bat

set GSON=build-lib\gson-2.11.0.jar
set SERVLET=build-lib\jakarta.servlet-api-6.0.0.jar

where javac >nul 2>nul
if errorlevel 1 (
    echo ERROR: javac was not found. Install JDK 21+ ^(tested with JDK 25^) and add it to PATH.
    exit /b 1
)
if not exist %GSON% (
    echo ERROR: %GSON% is missing.
    exit /b 1
)
if not exist %SERVLET% (
    echo ERROR: %SERVLET% is missing.
    exit /b 1
)
if not exist lib\javafx.controls.jar (
    echo ERROR: JavaFX jars are missing in lib\  - run download-javafx.bat first.
    exit /b 1
)

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\engine build\dto build\server build\client build\war\WEB-INF\classes build\war\WEB-INF\lib
mkdir dist\GuessMarketClient\lib

echo === Engine ===
for /r GuessMarketEngine\src %%f in (*.java) do echo %%f>>build\engine-sources.txt
javac -encoding UTF-8 --release 21 -d build\engine @build\engine-sources.txt
if errorlevel 1 goto fail
jar --create --file build\GuessMarketEngine.jar -C build\engine .

echo === DTO ===
for /r GuessMarketDto\src %%f in (*.java) do echo %%f>>build\dto-sources.txt
javac -encoding UTF-8 --release 21 -d build\dto @build\dto-sources.txt
if errorlevel 1 goto fail
jar --create --file build\GuessMarketDto.jar -C build\dto .

echo === Server (WAR) ===
for /r GuessMarketServer\src %%f in (*.java) do echo %%f>>build\server-sources.txt
javac -encoding UTF-8 --release 21 -cp "%SERVLET%;build\GuessMarketEngine.jar;build\GuessMarketDto.jar;%GSON%" -d build\war\WEB-INF\classes @build\server-sources.txt
if errorlevel 1 goto fail
xcopy /e /i /q /y GuessMarketServer\web build\war >nul
copy /y build\GuessMarketEngine.jar build\war\WEB-INF\lib\ >nul
copy /y build\GuessMarketDto.jar build\war\WEB-INF\lib\ >nul
copy /y %GSON% build\war\WEB-INF\lib\ >nul
jar --create --file dist\GuessMarket.war -C build\war .
if errorlevel 1 goto fail

echo === Client ===
for /r GuessMarketClient\src %%f in (*.java) do echo %%f>>build\client-sources.txt
javac -encoding UTF-8 --release 21 --module-path lib --add-modules javafx.controls -cp "build\GuessMarketDto.jar;%GSON%" -d build\client @build\client-sources.txt
if errorlevel 1 goto fail
copy /y GuessMarketClient\src\guessmarket\client\ui\app.css build\client\guessmarket\client\ui\ >nul
(
  echo Manifest-Version: 1.0
  echo Main-Class: guessmarket.client.GuessMarketClientApp
  echo Class-Path: GuessMarketDto.jar gson-2.11.0.jar
  echo.
)>build\client-manifest.mf
jar --create --file dist\GuessMarketClient\GuessMarketClient.jar --manifest build\client-manifest.mf -C build\client .
if errorlevel 1 goto fail
copy /y build\GuessMarketDto.jar dist\GuessMarketClient\ >nul
copy /y %GSON% dist\GuessMarketClient\ >nul
copy /y lib\javafx.*.jar dist\GuessMarketClient\lib\ >nul
copy /y client-run.bat dist\GuessMarketClient\run.bat >nul

echo.
echo Build completed:
echo   dist\GuessMarket.war
echo   dist\GuessMarketClient\run.bat
exit /b 0

:fail
echo.
echo *** BUILD FAILED ***
exit /b 1
