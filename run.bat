@echo off
javac --module-path libs/javafx-sdk-23/lib --add-modules javafx.controls,javafx.fxml -d build src/ChatClient.java
copy src\styles.css build\styles.css
java --module-path libs/javafx-sdk-23/lib --add-modules javafx.controls,javafx.fxml -cp build ChatClient
pause
