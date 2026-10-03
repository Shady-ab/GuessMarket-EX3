# Guess Market - Exercise 3 (Client / Server)

Submitters: Shady Aboarya (325556835), Abed Alqader Wattad (213726094).

Implemented bonus: **Chat**.

The full readme (decisions, classes, endpoints) is in `readme.docx` / `readme.pdf`, generated from `readme.html` by `make-readme.ps1`.

## Modules

- `GuessMarketEngine` - multi-user engine (N-option LMSR and order-book events, ledger, deposit).
- `GuessMarketDto` - JSON objects and API constants shared by server and client.
- `GuessMarketServer` - Tomcat servlets, packed into `GuessMarket.war`.
- `GuessMarketClient` - JavaFX client talking to the server over HTTP.

## Build and run

1. `download-javafx.bat` (once) - puts the JavaFX jars in `lib\`.
2. `build.bat` - creates `dist\GuessMarket.war` and `dist\GuessMarketClient\`.
3. Copy `GuessMarket.war` into `tomcat\webapps` and start Tomcat 10.1.
4. Run `dist\GuessMarketClient\run.bat` (server: `http://localhost:8080/GuessMarket`).

`package-submission.bat` builds everything and creates the submission zip.
