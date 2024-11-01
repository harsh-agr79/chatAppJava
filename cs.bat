@echo off
echo Compiling ChatServer.java...
javac -cp .;sqlite-jdbc-3.47.0.0.jar;slf4j-api-2.0.9.jar;slf4j-simple-2.0.9.jar ChatServer.java

if %errorlevel% neq 0 (
    echo Compilation failed.
    pause
    exit /b %errorlevel%
)

echo Running ChatServer...
java -cp .;sqlite-jdbc-3.47.0.0.jar;slf4j-api-2.0.9.jar;slf4j-simple-2.0.9.jar ChatServer

pause
