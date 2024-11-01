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


import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ChatClient extends Application {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private TextArea chatArea;
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

    chatArea = new TextArea();
    chatArea.setEditable(false);

    messageInput = new TextField();

    messageInput.setOnKeyPressed(event -> {
        if (event.getCode() == KeyCode.ENTER) {
            sendMessage();
            event.consume();  // Prevents adding a new line in the text field
        }
    });

    Button sendButton = new Button("Send");
    sendButton.setOnAction(e -> sendMessage());

    HBox messageBox = new HBox(10, messageInput, sendButton);
    chatBox.getChildren().addAll(chatArea, messageBox);

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

    Scene scene = new Scene(mainLayout, 700, 500);
    scene.getStylesheets().add("styles.css");

    // Make layout responsive by binding the sizes
    // Adjust the width of user and group lists based on window size
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
                    chatArea.appendText("You need to join the group " + currentChatName + " to send messages.\n");
                }
            }
        } else {
            // Broadcast message
            out.println(message);
            chatArea.appendText("You: " + message + "\n");
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
        Platform.runLater(() -> chatArea.appendText(formattedMessage + "\n"));
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
        Platform.runLater(() -> chatArea.appendText(formattedMessage + "\n"));
    }
}



    private void createGroup() {
        TextInputDialog groupDialog = new TextInputDialog();
        groupDialog.setHeaderText("Enter group name");
        String groupName = groupDialog.showAndWait().orElse(null);

        if (groupName != null && !groupName.isEmpty()) {
            out.println("/group create " + groupName);
        } else {
            chatArea.appendText("Group name cannot be empty.\n");
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
            chatArea.appendText("You joined group: " + groupName + "\n");
        } else {
            chatArea.appendText("No group selected to join.\n");
        }
    }

    private void leaveGroup() {
        String groupName = groupListView.getSelectionModel().getSelectedItem();
        if (groupName != null) {
            out.println("/group leave " + groupName);
            chatArea.appendText("You left group: " + groupName + "\n");
        } else {
            chatArea.appendText("No group selected to leave.\n");
        }
    }

    private void displayUserChat(String userName) {
        chatArea.clear();
        StringBuilder chatHistory = userChats.getOrDefault(userName, new StringBuilder());
        chatArea.appendText(chatHistory.toString());
    }

    private void displayGroupChat(String groupName) {
    // Check if the user has joined the group
    if (!joinedGroups.contains(groupName)) {
        // Show a message indicating they need to join the group first
        Platform.runLater(() -> {
            chatArea.clear();
            chatArea.appendText("You need to join the group to view messages.");
        });
        return;
    }

    // If the user is part of the group, display the chat
    StringBuilder chatHistory = groupChats.getOrDefault(groupName, new StringBuilder());
    
    currentChatType = "group";
    currentChatName = groupName;

    Platform.runLater(() -> {
        chatArea.clear();
        chatArea.appendText(chatHistory.toString());
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
                        } else if (message.contains("Private to")) {
                            handlePrivateMessage(message);
                        } else if (message.contains(": Group ")) {
                            handleGroupMessage(message);
                        } else {
                            chatArea.appendText(message + "\n");
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateUserList(String message) {
        String[] userArray = message.substring(10).split(",");
        users.clear();
        for (String user : userArray) {
            users.add(user.trim());
        }
        updateUserListView();
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
    System.out.println("Received message: " + message);

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
    System.out.println("Received message: " + message);

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