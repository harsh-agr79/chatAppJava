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
import java.util.Base64;

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
    private ScrollPane scrollPane;
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

    scrollPane = new ScrollPane(chatArea);
    scrollPane.setFitToWidth(true); // Makes the chatArea fit the width of the ScrollPane
    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); // Show scrollbar only when needed
    scrollPane.setStyle("-fx-background-color: transparent;");

    messageInput = new TextField();

    messageInput.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ENTER) {
            sendMessage();
            event.consume();  // Prevents adding a new line in the text field
            scrollToBottom();
        }
    });

    Button sendButton = new Button("Send");
    sendButton.setOnAction(e -> sendMessage());

    Button sendImageButton = new Button("Send Image");
    sendImageButton.setOnAction(e -> {
        if (currentChatName != null) {
            sendImage(currentChatName, currentChatType.equals("group"));
            scrollToBottom();
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

private void scrollToBottom() {
    // System.out.println("scrolltobottom");
    Platform.runLater(() -> scrollPane.setVvalue(1.5));  // Scroll to the bottom
    scrollPane.setVvalue(1.5);
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
                scrollToBottom();
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
        scrollToBottom();
    }
}
private void applyChatBubbleStyle(VBox messageBox, String sender) {
    // Apply bubble style to sent and received messages
    if (sender.equals("You") || sender.contains("You")) {
        // Sent messages - aligned to the right with a green bubble
        messageBox.setStyle("-fx-alignment: center-right; "
                            + "-fx-background-color: #A8D5BA; "
                            + "-fx-background-radius: 15px; "
                            + "-fx-padding: 10px; "
                            + "-fx-text-fill: white;");
    } else {
        // Received messages - aligned to the left with a white bubble
        messageBox.setStyle("-fx-alignment: center-left; "
                            + "-fx-background-color: white; "
                            + "-fx-background-radius: 15px; "
                            + "-fx-padding: 10px; "
                            + "-fx-border-color: #ccc; "
                            + "-fx-border-radius: 15px; "
                            + "-fx-text-fill: black;");
    }
    scrollToBottom();
}

   public void appendToUserChat(String userName, String message, boolean isSent) {
    StringBuilder chatHistory = userChats.computeIfAbsent(userName, k -> new StringBuilder());

    String sender = isSent ? "You" : userName;

    String formattedMessage = isSent
            ? "You: " + message // Fix the formatting for sent messages
            : userName + ": " + message;

    chatHistory.append(formattedMessage).append("\n");
    System.out.println("Appended to " + userName + "'s chat: " + formattedMessage); // Debug statement
    scrollToBottom();
    if (currentChatType != null && currentChatType.equals("user") && currentChatName.equals(userName)) {
        // Create a VBox to hold the sender's name and the message
        VBox messageBox = new VBox();
        messageBox.setSpacing(5);  // Add space between sender and message text

        // Create the sender's name text
        Text senderTextNode = new Text(formattedMessage);
        senderTextNode.setStyle("-fx-font-weight: bold;");
        messageBox.getChildren().add(senderTextNode);

        // Apply chat bubble style based on sender
        applyChatBubbleStyle(messageBox, sender);

        // Use Platform.runLater to update the UI on the JavaFX Application Thread
        Platform.runLater(() -> {
            chatArea.getChildren().add(messageBox);  // Add the message box to the chat area
            scrollToBottom();
        });
        scrollToBottom();
    }
     scrollToBottom();
}


public void sendImage(String recipient, boolean isGroup) {
  FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select Image");
    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));

    File file = fileChooser.showOpenDialog(null);
    if (file != null) {
        try {
            // Read image file into byte array
            byte[] imageData = Files.readAllBytes(file.toPath());

            // Encode image data to base64
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            // Prepare the message header
            String header = (isGroup ? "/groupimage " : "/privateimage ") + clientName + " " + recipient + " " + file.getName() + " " + base64Image.length();
            
            // Combine header and base64 image data into one message
             String message = header + ":" + base64Image;
            if(isGroup){

                StringBuilder chatHistory = groupChats.computeIfAbsent(recipient, k -> new StringBuilder());
        
                String formattedMessage = "[" + recipient + "] "+" You: @imagedata|" + base64Image; // Fix the formatting for sent messages

                chatHistory.append(formattedMessage).append("\n");
            }
            else{

                StringBuilder chatHistory = userChats.computeIfAbsent(recipient, k -> new StringBuilder());
        
                String formattedMessage = "You: @imagedata|" + base64Image; // Fix the formatting for sent messages

                chatHistory.append(formattedMessage).append("\n");
            }
            

            // Send the complete message (header + base64 image) in one go
            System.out.println(message);
            out.println(message);
            // out.flush();

            System.out.println("Image sent as base64.");

            // Display image in sender's chat
            appendImageToChat(recipient, file.getName(), imageData, true);
            scrollToBottom();
        } catch (IOException e) {
            showErrorDialog("Image Sending Error", "Could not send the image. Try again.");
            e.printStackTrace();
        }
         scrollToBottom();
    }
     scrollToBottom();
}

public void appendImageToChat(String chatName, String fileName, byte[] imageData, boolean isSent) {
    String senderText = isSent ? "You" : chatName;
    String messageText = senderText + ": ";

    // Create a VBox to hold the sender's name and the image
    VBox messageBox = new VBox();
    messageBox.setSpacing(5);  // Add space between sender and image

    // Display the sender's name
    Text senderTextNode = new Text(messageText);
    senderTextNode.setStyle("-fx-font-weight: bold;");
    messageBox.getChildren().add(senderTextNode);

    // Decode the image data and create an ImageView for it
    Image image = decodeImageData(imageData);
    if (image == null) return; // Stop if image decoding failed

    ImageView imageView = new ImageView(image);
    imageView.setFitWidth(200);  // Set max width to 200px
    imageView.setPreserveRatio(true);
    imageView.setOnMouseClicked(event -> openImageInNewWindow(image, fileName));

    // Add the image to the VBox
    messageBox.getChildren().add(imageView);

    // Apply chat bubble style based on sender
    applyChatBubbleStyle(messageBox, senderText);
    scrollToBottom();
    // Use Platform.runLater to update the UI on the JavaFX Application Thread
    Platform.runLater(() -> {
        chatArea.getChildren().add(messageBox);  // Add both label and image to VBox
        scrollToBottom();
         scrollToBottom();
          scrollToBottom();
    });
    scrollToBottom();
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


// private void appendToGroupChat(String groupName, String message, boolean isSent) {
//     StringBuilder chatHistory = groupChats.computeIfAbsent(groupName, k -> new StringBuilder());
    
//     String formattedMessage = isSent
//             ? "You: " + message // Fix the formatting for sent messages
//             : "[" + groupName + "] " + message;

//     chatHistory.append(formattedMessage).append("\n");
//     System.out.println("Appended to " + groupName + " chat: " + formattedMessage); // Debug statement

//     if (currentChatType != null && currentChatType.equals("group") && currentChatName.equals(groupName)) {
//          Text messageText = new Text(formattedMessage + "\n");
//         Platform.runLater(() ->  chatArea.getChildren().add(messageText));
//     }
// }

private void appendToGroupChat(String groupName, String message, boolean isSent) {
    StringBuilder chatHistory = groupChats.computeIfAbsent(groupName, k -> new StringBuilder());

    String sender = isSent ? "You" : "[" + groupName + "]";

    String formattedMessage = isSent
            ? "You: " + message // Format sent messages
            : "[" + groupName + "] " + message;

    chatHistory.append(formattedMessage).append("\n");
    System.out.println("Appended to " + groupName + " chat: " + formattedMessage); // Debug statement

    if (currentChatType != null && currentChatType.equals("group") && currentChatName.equals(groupName)) {
        // Create a VBox to hold the sender's name and the message
        VBox messageBox = new VBox();
        messageBox.setSpacing(5);  // Add space between sender and message text

        // Create the sender's name text
        Text senderTextNode = new Text(formattedMessage);
        senderTextNode.setStyle("-fx-font-weight: bold;");
        messageBox.getChildren().add(senderTextNode);

        // Apply chat bubble style based on sender
        applyChatBubbleStyle(messageBox, sender);

        // Use Platform.runLater to update the UI on the JavaFX Application Thread
        Platform.runLater(() -> {
            chatArea.getChildren().add(messageBox);  // Add the message box to the chat area
            scrollToBottom();
        });
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
    chatArea.getChildren().clear();  // Clear current chat history
    StringBuilder chatHistory = userChats.getOrDefault(userName, new StringBuilder());
    
    // Split the chat content into individual messages
    String chatContent = chatHistory.toString();
    String[] messages = chatContent.split("\n");

    for (String message : messages) {
        if (message.contains("@imagedata|")) {
            // Extract the sender and base64 image data
            String[] parts = message.split("@imagedata\\|");
            if (parts.length > 1) {
                String sender = message.split(":")[0];  // Get the sender's name
                String base64ImageData = parts[1];

                // Create a VBox to hold the sender's name and the image
                VBox messageBox = new VBox();
                messageBox.setSpacing(5);  // Add space between sender and image

                // Display the sender's name
                Text senderText = new Text(sender + ": ");
                senderText.setStyle("-fx-font-weight: bold;");
                messageBox.getChildren().add(senderText);

                applyChatBubbleStyle(messageBox, sender);

                try {
                    // Decode the base64 image data
                    byte[] imageData = Base64.getDecoder().decode(base64ImageData);

                    // Convert the byte array to an image
                    ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                    Image image = new Image(bis);
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(200);  // Set max width to 200px
                    imageView.setPreserveRatio(true);
                    imageView.setOnMouseClicked(event -> openImageInNewWindow(image, "image"));

                    // Add the image to the VBox
                    messageBox.getChildren().add(imageView);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error decoding base64 image.");
                }

                // Check if the message is sent or received
               

                // Add the styled message box to the chat area
                chatArea.getChildren().add(messageBox);
            }
        } else {
            // For normal text messages, display as text
            String sender = message.split(":")[0];  // Extract sender's name
            String messageText = message.substring(message.indexOf(":") + 1).trim();  // Get the message part

            // Create a VBox to hold the sender and the message
            VBox messageBox = new VBox();
            messageBox.setSpacing(5);  // Add space between sender and message text

            // Create the sender's name text
            Text senderText = new Text(sender + ": ");
            senderText.setStyle("-fx-font-weight: bold;");
            messageBox.getChildren().add(senderText);

            // Create the message text
            Text messageTextNode = new Text(messageText);
            messageBox.getChildren().add(messageTextNode);

            // Check if the message is sent or received
            applyChatBubbleStyle(messageBox, sender);

            // Add the styled message box to the chat area
            chatArea.getChildren().add(messageBox);
        }
    }
    scrollToBottom();
}

    private void displayGroupChat(String groupName) {
    // Check if the user has joined the group
    if (!joinedGroups.contains(groupName)) {
        Platform.runLater(() -> {
            chatArea.getChildren().clear();
            Text messageText = new Text("You need to join the group to view messages.");
            chatArea.getChildren().add(messageText);
        });
        return;
    }

    chatArea.getChildren().clear();  // Clear current chat history
    StringBuilder groupChatHistory = groupChats.getOrDefault(groupName, new StringBuilder());

    // Split the chat content into individual messages
    String chatContent = groupChatHistory.toString();
    String[] messages = chatContent.split("\n");

    for (String message : messages) {
        if (message.contains("@imagedata|")) {
            // Extract the sender and base64 image data
            String[] parts = message.split("@imagedata\\|");
            if (parts.length > 1) {
                String sender = message.split(":")[0];  // Get the sender's name
                String base64ImageData = parts[1];

                VBox messageBox = new VBox();
                messageBox.setSpacing(5);  // Add space between sender and image

                // Display the sender's name
                Text senderText = new Text(sender + ": ");
                senderText.setStyle("-fx-font-weight: bold;");
                messageBox.getChildren().add(senderText);

                applyChatBubbleStyle(messageBox, sender);

                try {
                    byte[] imageData = Base64.getDecoder().decode(base64ImageData);
                    ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                    Image image = new Image(bis);
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(200);  // Set max width to 200px
                    imageView.setPreserveRatio(true);
                    imageView.setOnMouseClicked(event -> openImageInNewWindow(image, "image"));
                    messageBox.getChildren().add(imageView);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error decoding base64 image.");
                }

                chatArea.getChildren().add(messageBox);  // Add the styled message box to chat area
            }
        } else {
            // Process regular text messages
            String sender = message.split(":")[0];
            String messageText = message.substring(message.indexOf(":") + 1).trim();

            VBox messageBox = new VBox();
            messageBox.setSpacing(5);  // Add space between sender and message text

            Text senderText = new Text(sender + ": ");
            senderText.setStyle("-fx-font-weight: bold;");
            messageBox.getChildren().add(senderText);

            Text messageTextNode = new Text(messageText);
            messageBox.getChildren().add(messageTextNode);

            applyChatBubbleStyle(messageBox, sender);

            chatArea.getChildren().add(messageBox);  // Add to chat area
        }
    }

    scrollToBottom();  // Scroll to bottom after loading chat
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
                            scrollToBottom();
                        } else if (message.contains(": Group ")) {
                            handleGroupMessage(message);
                        } else if (message.startsWith("/privateimage ")) {
                            receiveImage(message);  // Call to handle image message
                            scrollToBottom();
                        } else if (message.startsWith("/groupimage ")) {
                            receiveImageGroup(message);  // Call to handle image message
                            scrollToBottom();
                        } else {
                            // chatArea.appendText(message + "\n");
                            scrollToBottom();
                        }
                    });
                }
               
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

private void receiveImage(String message) {
    try {
        // Split the message into header and base64 data
        String[] parts = message.split(":", 2); // Split into two parts: header and base64 data
        String header = parts[0];
        String base64ImageData = parts[1];

        // Parse the header for metadata
        String[] tokens = header.split(" ");
        String sender = tokens[1];
        String fileName = tokens[3];

        StringBuilder chatHistory = userChats.computeIfAbsent(sender, k -> new StringBuilder());
    
        String formattedMessage = sender+": @imagedata|" + base64ImageData; // Fix the formatting for sent messages

        chatHistory.append(formattedMessage).append("\n");

        // Decode the base64 string into image data
        byte[] imageData = Base64.getDecoder().decode(base64ImageData);

        // Convert byte array to an image and display it
        ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
        Image image = new Image(bis);
        ImageView imageView = new ImageView(image);

        // Display the image in the chat
        appendImageToChat(sender, fileName, imageData, false);

        System.out.println("Image received from " + sender);

    } catch (Exception e) {
        e.printStackTrace();
        System.out.println("Error decoding base64 image.");
    }
}

private void receiveImageGroup(String message) {
    try {
        // Split the message into header and base64 data
        String[] parts = message.split(":", 2); // Split into two parts: header and base64 data
        String header = parts[0];
        String base64ImageData = parts[1];

        // Parse the header for metadata
        String[] tokens = header.split(" ");
        String sender = tokens[1];
        String group = tokens[2];
        String fileName = tokens[3];

        StringBuilder chatHistory = groupChats.computeIfAbsent(group, k -> new StringBuilder());
    
        String formattedMessage = "[" + group + "] "+ sender +": @imagedata|" + base64ImageData; // Fix the formatting for sent messages

        chatHistory.append(formattedMessage).append("\n");

        // Decode the base64 string into image data
        byte[] imageData = Base64.getDecoder().decode(base64ImageData);

        // Convert byte array to an image and display it
        ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
        Image image = new Image(bis);
        ImageView imageView = new ImageView(image);

        // Display the image in the chat
        appendImageToChat(sender, fileName, imageData, false);

        System.out.println("Image received from " + sender);

    } catch (Exception e) {
        e.printStackTrace();
        System.out.println("Error decoding base64 image.");
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
        scrollToBottom();
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
        // displayGroupChat(group);
    }
}
}