@echo off
javac --module-path libs/javafx-sdk-23/lib --add-modules javafx.controls,javafx.fxml -d build src/HelloJavaFX.java
java --module-path libs/javafx-sdk-23/lib --add-modules javafx.controls,javafx.fxml -cp build HelloJavaFX
pause