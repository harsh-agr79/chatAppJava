import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

    private TextArea chatArea;
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
        // Initialize the UI components first
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(300);

        messageInput = new TextField();
        messageInput.setPrefWidth(300);

        Button sendButton = new Button("Send");
        sendButton.setOnAction(e -> sendMessage());

        HBox messageBox = new HBox(10, messageInput, sendButton);

        userListView = new ListView<>();
        userListView.setPrefWidth(150);
        userListView.setPrefHeight(150);
        userListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                currentChatType = "user";
                currentChatName = newValue;
                displayUserChat(newValue);
            }
        });

        groupListView = new ListView<>();
        groupListView.setPrefWidth(150);
        groupListView.setPrefHeight(150);
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

        HBox groupControls = new HBox(10, createGroupButton, joinGroupButton, leaveGroupButton);

        VBox userGroupBox = new VBox(10, new Label("Users"), userListView, new Label("Groups"), groupListView, groupControls);

        root.getChildren().addAll(chatArea, messageBox, userGroupBox);

        Scene scene = new Scene(root, 600, 500);
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

            // Ask for the client's name
            clientName = promptClientName();
            out.println(clientName); // Send the client's name to the server

            // Start a thread to listen to messages from the server AFTER UI is initialized
            new Thread(new Listener()).start();

        } catch (IOException e) {
            showErrorDialog("Connection Error", "Unable to connect to the server. Please try again later.");
            e.printStackTrace();
        }
    }

    private String promptClientName() {
        TextInputDialog nameDialog = new TextInputDialog("YourName");
        nameDialog.setHeaderText("Enter your name");
        return nameDialog.showAndWait().orElse("Anonymous");
    }

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
                out.println("Group " + currentChatName + ": " + message);
                appendToGroupChat(currentChatName, message, true);  // Just "You: message"
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
        chatArea.clear();
        StringBuilder chatHistory = groupChats.getOrDefault(groupName, new StringBuilder());
        chatArea.appendText(chatHistory.toString());
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

    // Extract the sender before the first colon (i.e., "hello")
    int senderEndIndex = message.indexOf(":");
    if (senderEndIndex == -1) {
        System.out.println("Invalid message format: No sender found.");
        return;
    }
    
    String sender = message.substring(0, senderEndIndex).trim();

    // Extract the actual message after the second colon (after "Private to <user>:")
    int messageStartIndex = message.indexOf(":", senderEndIndex + 1); // Find second colon
    if (messageStartIndex == -1) {
        System.out.println("Invalid message format: No message found.");
        return;
    }
    
    String actualMessage = message.substring(messageStartIndex + 1).trim();

    // Store the received message in the sender's chat history
    appendToUserChat(sender, actualMessage, false); // false indicates this is a received message

    // Display the message in the active user chat if the chat matches the sender
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

    // Extract the actual message after the second colon
    String actualMessage = message.substring(groupEndIndex + 1).trim();

    // Store the received group message
    appendToGroupChat(group, sender + ": " + actualMessage, false); // False indicates this is a received message

    // Display the message in the active group chat if the chat matches the group name
    if (currentChatType.equals("group") && currentChatName.equals(group)) {
        //displayGroupChat(group);
    }
}


}
    