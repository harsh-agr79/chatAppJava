## Chat application in Java

# link to github repository <a href="https://github.com/harsh-agr79/chatAppJava/tree/img2"> Link </a>

https://github.com/harsh-agr79/chatAppJava/tree/img2

# How to run the server

First to Start Our Application a server must be created and run in the local wired or wireless network
for which the IPV4 address of the computer on that network should be added in the Local IP Address in the chatserver main class

to compile following statement is to be used because of the use of package jar files

```javac -cp .;sqlite-jdbc-3.47.0.0.jar;slf4j-api-2.0.9.jar;slf4j-simple-2.0.9.jar ChatServer.java```

after compilation the following statement is to be used to run the server

```java -cp .;sqlite-jdbc-3.47.0.0.jar;slf4j-api-2.0.9.jar;slf4j-simple-2.0.9.jar ChatServer```

to make this whole process easier you can run the cs.bat file by clicking on it or using the terminal compilationa and execution of the server will automatically start.

Alternatively

```./cs.bat```

=========================================================================

# How to run the client
Before running the main client side application it is mandatory to change ip addresses and match the ip addresses in the client and the server side and the server should be started before the client because the client will throw an error and not run incase it is not able to connect to the server which will occur in case

To run client on any device after downloading the code. We need to replace the IP address in the chatClient Class function run with the ip address of the same ip address the chat server is running on.

after which to compile the chatclient from the root directory we have to use the statement.

```javac --module-path libs/javafx-sdk-23/lib --add-modules javafx.controls,javafx.fxml -d build src/ChatClient.java```

after compilation we have to copy the css file from the src directory to build directory as the application will require the direct access of the css file to be able to read it

run this command from the root directory itself to copy the file.

```copy src\styles.css build\styles.css```

Now to run the program run the following command

```java --module-path libs/javafx-sdk-23/lib --add-modules javafx.controls,javafx.fxml -cp build ChatClient```

To make all of this process easy we have created a batch file run.bat to execute all the compilation and running commands we can run it by clicking on the run.bat or we can execute it in the terminal.

Alternatively

```./run.bat```
