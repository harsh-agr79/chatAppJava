import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

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

    private VBox chatDisplay; // Replaces chatArea TextArea
    private TextField messageInput;
    private ListView<String> userListView;
    private ListView<String> groupListView;
    private Set<String> users = new HashSet<>();
    private Set<String> groups = new HashSet<>();
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
        // Root layout
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root"); // Assign style class to root

        // Header
        // Label header = new Label("Chat Client");
        // header.setId("header"); // Assign ID for header styling
        // BorderPane.setAlignment(header, Pos.CENTER);
        // root.setTop(header);

        // Sidebar for Users and Groups
        VBox sidebar = new VBox(20);
        sidebar.setId("sidebar"); // Assign ID for sidebar styling
        sidebar.setPadding(new Insets(10, 10, 10, 10));
        sidebar.setPrefWidth(200); // Set fixed width as per CSS

        // Users List
        Label usersLabel = new Label("Users");
        userListView = new ListView<>();
        userListView.setId("contacts-list"); // Assign ID for contacts list styling
        userListView.setPrefWidth(180); // Adjusted width as per CSS
        userListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                currentChatType = "user";
                currentChatName = newValue;
                displayUserChat(newValue);
            }
        });

        // Groups List
        Label groupsLabel = new Label("Groups");
        groupListView = new ListView<>();
        groupListView.setId("groups-list"); // Assign ID for groups list styling
        groupListView.setPrefWidth(180); // Adjusted width as per CSS
        groupListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        groupListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                currentChatType = "group";
                currentChatName = newValue;
                displayGroupChat(newValue);
            }
        });

        // Group Control Buttons
        Button createGroupButton = new Button("Create");
        Button joinGroupButton = new Button("Join");
        Button leaveGroupButton = new Button("Leave");

        // Assign style classes for group buttons
        createGroupButton.getStyleClass().add("button");
        joinGroupButton.getStyleClass().add("button");
        leaveGroupButton.getStyleClass().add("button");

        // Alternatively, use style classes for multiple buttons
        HBox groupButtons = new HBox(10, createGroupButton, joinGroupButton, leaveGroupButton);
        groupButtons.setAlignment(Pos.CENTER_LEFT);
        groupButtons.getStyleClass().add("group-buttons"); // Assign style class for group buttons

        // Set actions for group buttons
        createGroupButton.setOnAction(e -> createGroup());
        joinGroupButton.setOnAction(e -> joinGroup());
        leaveGroupButton.setOnAction(e -> leaveGroup());

        // Assemble Sidebar
        sidebar.getChildren().addAll(
                usersLabel,
                userListView,
                groupsLabel,
                groupListView,
                groupButtons
        );

        // Chat Area
        VBox chatContainer = new VBox(10);
        chatContainer.setId("chat-container"); // Assign ID for chat container styling
        chatContainer.setPadding(new Insets(10, 10, 10, 10));
        chatContainer.setPrefWidth(700); // Set fixed width as per CSS

        // Chat Display Area using ScrollPane and VBox
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setId("chat-display"); // Assign ID for chat display styling
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);

        chatDisplay = new VBox(10);
        chatDisplay.setPadding(new Insets(10));
        chatDisplay.setAlignment(Pos.TOP_LEFT);

        scrollPane.setContent(chatDisplay);

        // Message Input and Send Button
        HBox messageBox = new HBox(10);
        messageBox.setAlignment(Pos.CENTER_LEFT);

        messageInput = new TextField();
        messageInput.setId("message-input"); // Assign ID for message input styling
        messageInput.setPromptText("Type your message here...");
        messageInput.setPrefWidth(600); // Adjusted width as per CSS
        HBox.setHgrow(messageInput, Priority.ALWAYS);

        Button sendButton = new Button("Send");
        sendButton.setId("send-button"); // Assign ID for send button styling
        sendButton.setOnAction(e -> sendMessage());

        messageBox.getChildren().addAll(messageInput, sendButton);

        // Assemble Chat Container
        chatContainer.getChildren().addAll(scrollPane, messageBox);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Set Sidebar and Chat Area in the Root Layout
        root.setLeft(sidebar);
        root.setCenter(chatContainer);

        // Scene and Stage setup
        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm()); // Add CSS to scene
        primaryStage.setScene(scene);
        primaryStage.setTitle("Chat Client");
        primaryStage.show();

        // Connect to server
        connectToServer("10.106.87.74", 8000); // Replace with your server's IP address
    }

    private void connectToServer(String hostname, int port) {
        try {
            socket = new Socket(hostname, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Ask for the client's name
            clientName = promptClientName();
            out.println(clientName); // Send the client's name to the server

            // Start a thread to listen to messages from the server
            new Thread(new Listener()).start();

        } catch (IOException e) {
            showErrorDialog("Connection Error", "Unable to connect to the server. Please try again later.");
            e.printStackTrace();
        }
    }

    private String promptClientName() {
        TextInputDialog nameDialog = new TextInputDialog("YourName");
        nameDialog.setHeaderText("Enter your name");
        nameDialog.setTitle("Username");
        nameDialog.setContentText("Please enter your name:");
        return nameDialog.showAndWait().orElse("Anonymous");
    }

    private void sendMessage() {
        String message = messageInput.getText();
        if (!message.isEmpty()) {
            if (currentChatType != null && currentChatName != null) {
                if (currentChatType.equals("user")) {
                    // Send a private message
                    out.println("Private to " + currentChatName + ": " + message);
                    appendMessage("You: " + message, true);  // Indicate sent message
                } else if (currentChatType.equals("group")) {
                    // Send a group message
                    out.println("Group " + currentChatName + ": " + message);
                    appendMessage("You: " + message, true);  // Indicate sent message
                }
            } else {
                // Broadcast message
                out.println(message);
                appendMessage("You: " + message, true);
            }
            messageInput.clear();
        }
    }

    /**
     * Appends a message to the chat display area.
     *
     * @param message The message text.
     * @param isSent  True if the message is sent by the user, false if received.
     */
    private void appendMessage(String message, boolean isSent) {
    HBox messageBox = new HBox();
    messageBox.setPadding(new Insets(5, 10, 5, 10));
    messageBox.setMaxWidth(chatDisplay.getWidth() - 20); // Prevent overflow

    Label messageLabel = new Label(message);
    messageLabel.setWrapText(true);
    messageLabel.getStyleClass().add("message-label");

    // Adjust the alignment and padding for sent and received messages
    if (isSent) {
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        messageBox.setPadding(new Insets(5, 50, 5, 10)); // Extra padding on left for sent messages
        messageLabel.getStyleClass().add("message-sent");
    } else {
        messageBox.setAlignment(Pos.CENTER_LEFT);
        messageBox.setPadding(new Insets(5, 10, 5, 50)); // Extra padding on right for received messages
        messageLabel.getStyleClass().add("message-received");
    }

    messageBox.getChildren().add(messageLabel);
    chatDisplay.getChildren().add(messageBox);

    // Scroll to the bottom after adding a message
    Platform.runLater(() -> {
        ScrollPane scrollPane = (ScrollPane) chatDisplay.getParent();
        scrollPane.setVvalue(1.0);
    });
}



    private void createGroup() {
        TextInputDialog groupDialog = new TextInputDialog();
        groupDialog.setHeaderText("Create New Group");
        groupDialog.setTitle("Create Group");
        groupDialog.setContentText("Enter group name:");
        String groupName = groupDialog.showAndWait().orElse(null);

        if (groupName != null && !groupName.trim().isEmpty()) {
            out.println("/group create " + groupName.trim());
        } else {
            appendMessage("Group name cannot be empty.", true);
        }
    }

    private void joinGroup() {
        String groupName = groupListView.getSelectionModel().getSelectedItem();
        if (groupName != null) {
            out.println("/group join " + groupName);
            groupChats.putIfAbsent(groupName, new StringBuilder());
            appendMessage("You joined group: " + groupName, true);
        } else {
            appendMessage("No group selected to join.", true);
        }
    }

    private void leaveGroup() {
        String groupName = groupListView.getSelectionModel().getSelectedItem();
        if (groupName != null) {
            out.println("/group leave " + groupName);
            appendMessage("You left group: " + groupName, true);
        } else {
            appendMessage("No group selected to leave.", true);
        }
    }

    private void displayUserChat(String userName) {
        chatDisplay.getChildren().clear();
        StringBuilder chatHistory = userChats.getOrDefault(userName, new StringBuilder());
        String[] messages = chatHistory.toString().split("\n");
        for (String msg : messages) {
            if (msg.startsWith("You: ")) {
                appendMessage(msg, true);
            } else {
                appendMessage(msg, false);
            }
        }
    }

    private void displayGroupChat(String groupName) {
        chatDisplay.getChildren().clear();
        StringBuilder chatHistory = groupChats.getOrDefault(groupName, new StringBuilder());
        String[] messages = chatHistory.toString().split("\n");
        for (String msg : messages) {
            if (msg.startsWith("You: ")) {
                appendMessage(msg, true);
            } else {
                appendMessage(msg, false);
            }
        }
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
            alert.setHeaderText(null);
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
                    System.out.println("Server: " + message);
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
                            appendMessage(message, false); // Treat as received message
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                showErrorDialog("Connection Lost", "The connection to the server was lost.");
            }
        }
    }

    private void updateUserList(String message) {
        String[] userArray = message.substring(10).split(",");
        users.clear();
        for (String user : userArray) {
            if (!user.trim().isEmpty()) {
                users.add(user.trim());
            }
        }
        updateUserListView();
    }

    private void updateGroupList(String message) {
        String[] groupArray = message.substring(11).split(",");
        groups.clear();
        for (String group : groupArray) {
            if (!group.trim().isEmpty()) {
                groups.add(group.trim());
            }
        }
        updateGroupListView();
    }

    private void handlePrivateMessage(String message) {
        System.out.println("Handling private message: " + message);

        // Expected format: "Sender: Private to Recipient: Message"
        int firstColon = message.indexOf(":");
        if (firstColon == -1) {
            System.out.println("Invalid private message format.");
            return;
        }
        String sender = message.substring(0, firstColon).trim();

        int secondColon = message.indexOf(":", firstColon + 1);
        if (secondColon == -1) {
            System.out.println("Invalid private message format.");
            return;
        }

        String actualMessage = message.substring(secondColon + 1).trim();

        // Store and display the message
        appendMessage(sender + ": " + actualMessage, false);

        // If the current chat is with the sender, display the message
        if ("user".equals(currentChatType) && sender.equals(currentChatName)) {
            // Already appended in appendMessage
        }
    }

    private void handleGroupMessage(String message) {
        System.out.println("Handling group message: " + message);

        // Expected format: "Sender: Group GroupName: Message"
        int firstColon = message.indexOf(":");
        if (firstColon == -1) {
            System.out.println("Invalid group message format.");
            return;
        }
        String sender = message.substring(0, firstColon).trim();

        int groupKeywordIndex = message.indexOf("Group ", firstColon + 1);
        if (groupKeywordIndex == -1) {
            System.out.println("Invalid group message format: 'Group ' keyword missing.");
            return;
        }

        int groupNameStart = groupKeywordIndex + "Group ".length();
        int secondColon = message.indexOf(":", groupNameStart);
        if (secondColon == -1) {
            System.out.println("Invalid group message format: No message found.");
            return;
        }

        String groupName = message.substring(groupNameStart, secondColon).trim();
        String actualMessage = message.substring(secondColon + 1).trim();

        // Store and display the message
        appendMessage("[" + groupName + "] " + sender + ": " + actualMessage, false);

        // If the current chat is with the group, display the message
        if ("group".equals(currentChatType) && groupName.equals(currentChatName)) {
            // Already appended in appendMessage
        }
    }
}
