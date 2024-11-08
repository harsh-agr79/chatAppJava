import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.util.Pair;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;
import java.util.Optional;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.control.ListCell;
import javafx.scene.text.Text;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.paint.Color;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.stage.FileChooser;
import java.util.Timer;
import java.util.TimerTask;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChatClient extends Application {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private VBox chatArea;
    private TextField messageInput;
    private ListView<String> userListView;
    private ListView<String> groupListView;
    private Set<String> users = new HashSet<>();
    private Set<String> groups = new HashSet<>();
    private Set<String> joinedGroups = new HashSet<>();
    private String clientName;

    // Maps to store chat histories for users and groups
    private Map<String, StringBuilder> userChats = new HashMap<>();
    private Map<String, StringBuilder> groupChats = new HashMap<>();
    
    private String currentChatType; // "user" or "group"
    private String currentChatName;  // User or Group name currently in chat

    public static void main(String[] args) {
        launch(args);
    }

    @Override
public void start(Stage primaryStage) {
    // Initialize the UI components first
    VBox chatBox = new VBox(10);
    chatBox.setPadding(new Insets(10));

    chatArea = new VBox(5);
    chatArea.setPadding(new Insets(10));
    chatArea.setStyle("-fx-background-color: #f4f4f4;");

    ScrollPane scrollPane = new ScrollPane(chatArea);
    scrollPane.setFitToWidth(true); // Makes the chatArea fit the width of the ScrollPane
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Show scrollbar only when needed
    scrollPane.setStyle("-fx-background-color: transparent;");

    messageInput = new TextField();

    messageInput.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ENTER) {
            sendMessage();
            event.consume();  // Prevents adding a new line in the text field
        }
    });

    Button sendButton = new Button("Send");
    sendButton.setOnAction(e -> sendMessage());

    Button sendImageButton = new Button("Send Image");
    sendImageButton.setOnAction(e -> {
        if (currentChatName != null) {
            sendImage(currentChatName, currentChatType.equals("group"));
        } else {
            Text messageText = new Text("Select a user or group to send an image.\n");
            chatArea.getChildren().add(messageText);
            // chatArea.appendText("Select a user or group to send an image.\n");
        }
    });

    HBox messageBox = new HBox(10, messageInput, sendButton, sendImageButton);
    chatBox.getChildren().addAll(chatArea, scrollPane, messageBox);

    // User and Group lists
    userListView = new ListView<>();
    userListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue != null) {
            currentChatType = "user";
            currentChatName = newValue;
            displayUserChat(newValue);
        }
    });

    groupListView = new ListView<>();
    groupListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    groupListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
        if (newValue != null) {
            currentChatType = "group";
            currentChatName = newValue;
            displayGroupChat(newValue);
        }
    });

    Button createGroupButton = new Button("Create Group");
    createGroupButton.setOnAction(e -> createGroup());

    Button joinGroupButton = new Button("Join Group");
    joinGroupButton.setOnAction(e -> joinGroup());

    Button leaveGroupButton = new Button("Leave Group");
    leaveGroupButton.setOnAction(e -> leaveGroup());

    VBox userGroupBox = new VBox(10, new Label("Users"), userListView, new Label("Groups"), groupListView, createGroupButton, joinGroupButton, leaveGroupButton);

    // Main layout - split horizontally
    HBox mainLayout = new HBox(10);
    mainLayout.setPadding(new Insets(10));
    mainLayout.getChildren().addAll(userGroupBox, chatBox);

    // Add the Logout button
    Button logoutButton = new Button("Logout");
    logoutButton.setOnAction(e -> {
        primaryStage.close();  // Close the application window
        System.exit(0);        // Terminate the program
    });

    // Top bar layout with logout button aligned to the right
    HBox topBar = new HBox();
    topBar.setPadding(new Insets(10, 10, 0, 10));
    topBar.setAlignment(Pos.TOP_RIGHT);
    topBar.getChildren().add(logoutButton);

    // Combine top bar and main layout
    VBox rootLayout = new VBox(topBar, mainLayout);

    Scene scene = new Scene(rootLayout, 700, 500);
    scene.getStylesheets().add("styles.css");

    // Make layout responsive by binding the sizes
    userGroupBox.prefWidthProperty().bind(scene.widthProperty().multiply(0.25)); // 25% of window width
    userListView.prefHeightProperty().bind(scene.heightProperty().multiply(0.35)); // 35% of window height
    groupListView.prefHeightProperty().bind(scene.heightProperty().multiply(0.35)); // 35% of window height

    // Bind chat box to take the remaining width and height
    chatBox.prefWidthProperty().bind(scene.widthProperty().multiply(0.75)); // 75% of window width
    chatArea.prefHeightProperty().bind(scene.heightProperty().subtract(messageBox.heightProperty()).multiply(0.8)); // Adjust chat area height
    messageInput.prefWidthProperty().bind(chatBox.widthProperty().subtract(sendButton.widthProperty()).multiply(0.85));

    primaryStage.setScene(scene);
    primaryStage.setTitle("Chat Client");
    primaryStage.show();

    // Now connect to the server and start the listener thread
    connectToServer("10.17.235.2", 8000); // Use your server's IP address here
}



     private void connectToServer(String hostname, int port) {
        try {
            socket = new Socket(hostname, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Ask for the client's login and password
            Pair<String, String> credentials = promptLoginAndPassword();
            String login = credentials.getKey();
            String password = credentials.getValue();

            // Send login and password to the server
            out.println(login);
            out.println(password);
            clientName = login;

            // Start a thread to listen to messages from the server AFTER UI is initialized
            new Thread(new Listener()).start();

        } catch (IOException e) {
            showErrorDialog("Connection Error", "Unable to connect to the server. Please try again later.");
            e.printStackTrace();
        }
    }

    private Pair<String, String> promptLoginAndPassword() {
        // Create a new dialog for login and password
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your login and password");

        // Set the button types
        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        // Create the login and password fields
        TextField loginField = new TextField();
        loginField.setPromptText("Login");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        // Organize fields in a grid
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Login:"), 0, 0);
        grid.add(loginField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Convert the result to a Pair of login and password
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(loginField.getText(), passwordField.getText());
            }
            return null;
        });

        // Show dialog and return result or empty if cancelled
        Optional<Pair<String, String>> result = dialog.showAndWait();
        return result.orElse(new Pair<>("Anonymous", ""));
    }

    // private void showErrorDialog(String title, String message) {
    //     Alert alert = new Alert(Alert.AlertType.ERROR);
    //     alert.setTitle(title);
    //     alert.setHeaderText(null);
    //     alert.setContentText(message);
    //     alert.showAndWait();
    // }

   private void sendMessage() {
    String message = messageInput.getText();
    if (!message.isEmpty()) {
        if (currentChatType != null && currentChatName != null) {
            if (currentChatType.equals("user")) {
                // Send a private message
                out.println("Private to " + currentChatName + ": " + message);
                appendToUserChat(currentChatName, message, true);  // Just "You: message"
            } else if (currentChatType.equals("group")) {
                // Send a group message
               if (joinedGroups.contains(currentChatName)) {
                    // Send a group message
                    out.println("Group " + currentChatName + ": " + message);
                    appendToGroupChat(currentChatName, message, true);  // Just "You: message"
                } else {
                    // Notify the user that they need to join the group
                    // chatArea.appendText("You need to join the group " + currentChatName + " to send messages.\n");
                    Text messageText = new Text("You need to join the group " + currentChatName + " to send messages.\n");
                    chatArea.getChildren().add(messageText);
                }
            }
        } else {
            // Broadcast message
            out.println(message);
            // chatArea.appendText("You: " + message + "\n");
            Text messageText = new Text("You: " + message);
            chatArea.getChildren().add(messageText);
        }
        messageInput.clear();
    }
}

    private void appendToUserChat(String userName, String message, boolean isSent) {
    StringBuilder chatHistory = userChats.computeIfAbsent(userName, k -> new StringBuilder());
    
    String formattedMessage = isSent
            ? "You: " + message // Fix the formatting for sent messages
            : userName + ": " + message;

    chatHistory.append(formattedMessage).append("\n");
    System.out.println("Appended to " + userName + "'s chat: " + formattedMessage); // Debug statement

    if (currentChatType != null && currentChatType.equals("user") && currentChatName.equals(userName)) {
        Text messageText = new Text(formattedMessage + "\n");
        Platform.runLater(() ->  chatArea.getChildren().add(messageText));
    }
}

public void sendImage(String recipient, boolean isGroup) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Image");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

    File file = fileChooser.showOpenDialog(null);
    if (file != null) {
        try {
            byte[] imageData = Files.readAllBytes(file.toPath());

            // Prepare the message header
            String header = (isGroup ? "/groupimage " : "/privateimage ")+clientName+" " + recipient + " " + file.getName() + " " + imageData.length;
            out.println(header);  // Send header first

            // Send the image data
            socket.getOutputStream().write(imageData);
            socket.getOutputStream().flush();
            
            out.println("\ndone sending image\n");

            appendImageToChat(recipient, file.getName(), imageData, true); // Show image in sender's chat
            Timer timer = new Timer();
            TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                       sendImageDummy(recipient,isGroup, header, imageData);
                    }
            };
            timer.schedule(task, 0000);
            
        } catch (IOException e) {
            showErrorDialog("Image Sending Error", "Could not send the image. Try again.");
            e.printStackTrace();
        }
    }
}

private void sendImageDummy(String recipient, boolean isGroup, String header, byte[] imageData){
    try {
            out.println(header);  // Send header first

            // Send the image data
            socket.getOutputStream().write(imageData);
            socket.getOutputStream().flush();

            // socket.flush();
            
            out.println("\ndone sending image\n");

        } catch (IOException e) {
            showErrorDialog("Image Sending Error", "Could not send the image. Try again.");
            e.printStackTrace();
        }
}

public void appendImageToChat(String chatName, String fileName, byte[] imageData, boolean isSent) {
    String senderText = isSent ? "You" : chatName;
    String messageText = senderText + " sent an image: " + fileName;

    // Create a text label to describe the image
    Label textLabel = new Label(messageText);
    textLabel.setWrapText(true);
    textLabel.setStyle("-fx-text-fill: blue;");

    // Decode the image data and create an ImageView for it
    Image image = decodeImageData(imageData);
    if (image == null) return; // Stop if image decoding failed

    ImageView imageView = new ImageView(image);
    imageView.setFitWidth(100);  // Set a smaller width for thumbnail view
    imageView.setPreserveRatio(true);

    // Add click event to open the image in a new window when clicked
    imageView.setOnMouseClicked(event -> openImageInNewWindow(image, fileName));

    // Use Platform.runLater to update the UI on the JavaFX Application Thread
    Platform.runLater(() -> {
        VBox chatItem = new VBox(textLabel, imageView);
        chatItem.setSpacing(5);
        chatArea.getChildren().add(chatItem);  // Add both label and thumbnail to VBox
    });
}

private void openImageInNewWindow(Image image, String fileName) {
    Stage imageStage = new Stage();
    imageStage.setTitle("Image - " + fileName);

    // Full-sized ImageView in the new window
    ImageView fullSizeImageView = new ImageView(image);
    fullSizeImageView.setPreserveRatio(true);
    fullSizeImageView.setFitWidth(600);  // Adjust width
    fullSizeImageView.setFitHeight(400); // Adjust height

    VBox imageLayout = new VBox(fullSizeImageView);
    imageLayout.setAlignment(Pos.CENTER);
    imageLayout.setPadding(new Insets(10));

    Scene imageScene = new Scene(imageLayout);
    imageStage.setScene(imageScene);
    imageStage.show();
}

private Image decodeImageData(byte[] imageData) {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(imageData)) {
        return new Image(bais);
    } catch (Exception e) {
        showErrorDialog("Image Display Error", "Could not display the image.");
        e.printStackTrace();
        return null;
    }
}


private void appendToGroupChat(String groupName, String message, boolean isSent) {
    StringBuilder chatHistory = groupChats.computeIfAbsent(groupName, k -> new StringBuilder());
    
    String formattedMessage = isSent
            ? "You: " + message // Fix the formatting for sent messages
            : "[" + groupName + "] " + message;

    chatHistory.append(formattedMessage).append("\n");
    System.out.println("Appended to " + groupName + " chat: " + formattedMessage); // Debug statement

    if (currentChatType != null && currentChatType.equals("group") && currentChatName.equals(groupName)) {
         Text messageText = new Text(formattedMessage + "\n");
        Platform.runLater(() ->  chatArea.getChildren().add(messageText));
    }
}



    private void createGroup() {
        TextInputDialog groupDialog = new TextInputDialog();
        groupDialog.setHeaderText("Enter group name");
        String groupName = groupDialog.showAndWait().orElse(null);

        if (groupName != null && !groupName.isEmpty()) {
            out.println("/group create " + groupName);
        } else {
            // chatArea.appendText("Group name cannot be empty.\n");
            Text messageText = new Text("Group name cannot be empty.\n");
            chatArea.getChildren().add(messageText);

        }
    }

    private void joinGroup() {
        String groupName = groupListView.getSelectionModel().getSelectedItem();
        if (groupName != null) {
            out.println("/group join " + groupName);
            groupChats.putIfAbsent(groupName, new StringBuilder());
            if (!joinedGroups.contains(groupName)) {
                joinedGroups.add(groupName);  // Track that the user joined the group
                // Notify the server about the group join event if needed
            }
            // chatArea.appendText("You joined group: " + groupName + "\n");
            Text messageText = new Text("You joined group: " + groupName + "\n");
            chatArea.getChildren().add(messageText);
        } else {
            // chatArea.appendText("No group selected to join.\n");
            Text messageText = new Text("No group selected to join.\n");
            chatArea.getChildren().add(messageText);
        }
    }

    private void leaveGroup() {
        String groupName = groupListView.getSelectionModel().getSelectedItem();
        if (groupName != null) {
            out.println("/group leave " + groupName);
            // chatArea.appendText("You left group: " + groupName + "\n");
            Text messageText = new Text("You left group: " + groupName + "\n");
            chatArea.getChildren().add(messageText);
        } else {
            // chatArea.appendText("No group selected to leave.\n");
            Text messageText = new Text("No group selected to leave.\n");
            chatArea.getChildren().add(messageText);
        }
    }

    private void displayUserChat(String userName) {
        chatArea.getChildren().clear();
        StringBuilder chatHistory = userChats.getOrDefault(userName, new StringBuilder());
        // chatArea.appendText(chatHistory.toString());
        Text messageText = new Text(chatHistory.toString());
        chatArea.getChildren().add(messageText);
    }

    private void displayGroupChat(String groupName) {
    // Check if the user has joined the group
    if (!joinedGroups.contains(groupName)) {
        // Show a message indicating they need to join the group first
        Platform.runLater(() -> {
            // chatArea.clear();
            chatArea.getChildren().clear();
            // chatArea.appendText("You need to join the group to view messages.");
            Text messageText = new Text("You need to join the group to view messages.");
            chatArea.getChildren().add(messageText);
        });
        return;
    }

    // If the user is part of the group, display the chat
    StringBuilder chatHistory = groupChats.getOrDefault(groupName, new StringBuilder());
    
    currentChatType = "group";
    currentChatName = groupName;

    Platform.runLater(() -> {
        chatArea.getChildren().clear();
        Text messageText = new Text(chatHistory.toString());
        chatArea.getChildren().add(messageText);
    });
}


    private void updateUserListView() {
        Platform.runLater(() -> userListView.getItems().setAll(users));
    }

    private void updateGroupListView() {
        Platform.runLater(() -> groupListView.getItems().setAll(groups));
    }

    private void showErrorDialog(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private class Listener implements Runnable {
        @Override
        public void run() {
            try {
                String serverMessage;
                while ((serverMessage = in.readLine()) != null) {
                    final String message = serverMessage;
                    System.out.println(message);
                    Platform.runLater(() -> {
                        if (message.startsWith("/userlist")) {
                            updateUserList(message);
                        } else if (message.startsWith("/grouplist")) {
                            updateGroupList(message);
                        } else if (message.startsWith("/joinedGroups")) {
                            updateJoinedGroupList(message);
                        } else if (message.contains("Private to")) {
                            handlePrivateMessage(message);
                        } else if (message.contains(": Group ")) {
                            handleGroupMessage(message);
                        } else if (message.startsWith("/privateimage ")) {
                            receiveImage(message);  // Call to handle image message
                        } else {
                            // chatArea.appendText(message + "\n");
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void receiveImage(String messageHeader) {
    try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // String messageHeader = reader.readLine(); // Read header line
        String[] tokens = messageHeader.split(" ");
        
        String sender = tokens[1];
        String fileName = tokens[3];
        int fileSize = Integer.parseInt(tokens[4]);

        System.out.println("Receiving image from " + sender + ": " + fileName + " (" + fileSize + " bytes)");

        // Step 2: Read the binary image data based on fileSize
        byte[] imageData = new byte[fileSize];
        InputStream inputStream = socket.getInputStream();

        int bytesRead = 0;
        while (bytesRead < fileSize) {
            int result = inputStream.read(imageData, bytesRead, fileSize - bytesRead);
            if (result == -1) break; // If end of stream is reached unexpectedly
            bytesRead += result;
        }

        // Step 3: Check if the entire image data is received
        if (bytesRead == fileSize) {
            System.out.println("Image received successfully from " + sender);
            appendImageToChat(sender, fileName, imageData, false); // Display the received image
        } else {
            System.out.println("Error: Incomplete image received.");
        }
    } catch (Exception e) {
        // showErrorDialog("Image Reception Error", "Could not receive the image.");
        e.printStackTrace();
    }
}

    private void updateUserList(String message) {
       // Set cell factory for userListView to customize the display of items
        userListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Assuming items are in the format "username:status"
                    String[] userParts = item.split(":");
                    String username = userParts[0].trim();
                    String status = userParts[1].trim();

                    setText(username);  // Set the username as the text
                    if (status.equalsIgnoreCase("online")) {
                        setTextFill(javafx.scene.paint.Color.GREEN);  // Set text color for online
                    } else {
                        setTextFill(javafx.scene.paint.Color.GRAY);  // Set text color for offline
                    }
                }
            }
        });

        // Listener for item selection in userListView
        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Extract the username from the selected item
                String[] userParts = newValue.split(":");
                String selectedUsername = userParts[0].trim(); // Get the username
                
                // Now you can use the selectedUsername to handle chat logic
                currentChatType = "user"; // Set the chat type
                currentChatName = selectedUsername; // Set the chat name

                displayUserChat(selectedUsername); // Call the method to display chat
            }
        });

        String[] users = message.substring(10).split(",");
        Platform.runLater(() -> {
            userListView.getItems().clear();  // Clear existing items first

            for (String user : users) {
                // Assuming the format is "username:status"
                String[] userParts = user.split(":");
                String username = userParts[0].trim(); // Extract the username

                // Check if the username is the same as clientName; if so, skip it
                if (!username.equals(clientName)) {
                    userListView.getItems().add(user);  // Add username and status as a single string
                }
            }
        });

    }

    private void updateJoinedGroupList(String message){
        String[] cont = message.split(" ", 2);
        String[] groups = cont[1].split(", ");
        for(String group : groups){
            joinedGroups.add(group);
        }
    }

    private void updateGroupList(String message) {
        String[] groupArray = message.substring(11).split(",");
        groups.clear();
        for (String group : groupArray) {
            groups.add(group.trim());
        }
        updateGroupListView();
    }

    private void handlePrivateMessage(String message) {
    // System.out.println("Received message: " + message);

    if(message.startsWith("History:")){
            String[] parts = message.split(": ", 4);
            // for(int i = 0; i < 4; i++){
            //     System.out.println(parts[i]);
            // }
            String senderName = parts[1];          // Get the sender's name
            String[] recipientPart = parts[2].split(" ", 3);       // Contains "recipient: message"
            // String[] recipientAndMessage = recipientPart.split(":", 2);
            String recipientName = recipientPart[2]; // Get the recipient name
            String msg = parts[3];       // Get the message

            // Determine the chat key (the user you're chatting with)
            String chatKey = senderName.equals(clientName) ? recipientName : senderName;

            // Create the message format based on the sender
            String formattedMessage;
            if (senderName.equals(clientName)) {
                formattedMessage = "You: " + msg;
            } else {
                formattedMessage = senderName + ": " + msg;
            }

            // Append the message to the chat history in userChats
            userChats.computeIfAbsent(chatKey, k -> new StringBuilder()).append(formattedMessage).append("\n");
            return;
    }



    // Extract sender before the first colon (i.e., "dev")
    int senderEndIndex = message.indexOf(":");
    if (senderEndIndex == -1) {
        System.out.println("Invalid message format: No sender found.");
        return;
    }
    String sender = message.substring(0, senderEndIndex).trim();

    // Check if the message follows the "Private to" format
    String privateMessageIndicator = "Private to ";
    int privateMessageIndex = message.indexOf(privateMessageIndicator);
    if (privateMessageIndex == -1) {
        System.out.println("Invalid message format: No 'Private to' indicator found.");
        return;
    }

    // Extract recipient name (i.e., "hemanth")
    int recipientStartIndex = privateMessageIndex + privateMessageIndicator.length();
    int recipientEndIndex = message.indexOf(":", recipientStartIndex);
    if (recipientEndIndex == -1) {
        System.out.println("Invalid message format: No recipient found.");
        return;
    }
    String recipient = message.substring(recipientStartIndex, recipientEndIndex).trim();

    // Extract the actual message after the second colon
    String actualMessage = message.substring(recipientEndIndex + 1).trim();

    // Check if the message is for the current user
    if (recipient.equals(clientName)) {
        // Store the received message in the sender's chat history
        appendToUserChat(sender, actualMessage, false); // false indicates this is a received message
    }

    // If the current chat is with the sender, append to the chat area
    if (currentChatType.equals("user") && currentChatName.equals(sender)) {
        // displayUserChat(sender);
    }
}
private void handleGroupMessage(String message) {
    // System.out.println("Received message: " + message);
    if(message.startsWith("History:")){
            String[] parts = message.split(": ", 4);
            // for(int i = 0; i < 4; i++){
            //     System.out.println(parts[i]);
            // }
            String senderName = parts[1];          // Get the sender's name
            // if(parts[2].startsWith("Group")){
                String[] rec = parts[2].split(" ", 2);
                String grpname = rec[1];
                String msg = parts[3]; 

                String chatKey = grpname;

                    // Create the message format based on the sender
                String formattedMessage;
                if (senderName.equals(clientName)) {
                    formattedMessage = "[" + grpname + "] " + "You: " + msg;
                } else {
                    formattedMessage = "[" + grpname + "] " + senderName + ": " + msg;
                }
                // Append the message to the chat history in userChats
                groupChats.computeIfAbsent(chatKey, k -> new StringBuilder()).append(formattedMessage).append("\n");
                return;
            // }
    }

    // Extract sender before the first colon (i.e., "dev")
    int senderEndIndex = message.indexOf(":");
    if (senderEndIndex == -1) {
        System.out.println("Invalid message format: No sender found.");
        return;
    }
    String sender = message.substring(0, senderEndIndex).trim();

    // Extract group name after "Group" and before the second colon
    int groupStartIndex = message.indexOf("Group ") + "Group ".length();
    int groupEndIndex = message.indexOf(":", groupStartIndex);
    if (groupEndIndex == -1) {
        System.out.println("Invalid message format: No group name found.");
        return;
    }
    String group = message.substring(groupStartIndex, groupEndIndex).trim();

    // Ensure the user has joined the group
    if (!joinedGroups.contains(group)) {
        System.out.println("User has not joined the group: " + group);
        return;
    }

    // Extract the actual message after the second colon
    String actualMessage = message.substring(groupEndIndex + 1).trim();

    // Store the received group message
    appendToGroupChat(group, sender + ": " + actualMessage, false);  // False indicates this is a received message

    // Display the message in the active group chat if the chat matches the group name
    if (currentChatType.equals("group") && currentChatName.equals(group)) {
        // Update the display of the active chat
        displayGroupChat(group);
    }
}
}