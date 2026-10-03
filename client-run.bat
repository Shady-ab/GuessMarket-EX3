@echo off
setlocal
cd /d "%~dp0"

rem Guess Market JavaFX client. Expects the server at http://localhost:8080/GuessMarket
rem (GuessMarket.war deployed in Tomcat). Another address can be passed as the first argument.

java --module-path lib --add-modules javafx.controls --enable-native-access=javafx.graphics -cp "GuessMarketClient.jar;GuessMarketDto.jar;gson-2.11.0.jar" guessmarket.client.GuessMarketClientApp %*
if errorlevel 1 pause
endlocal
