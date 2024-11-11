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

    private Map<String, StringBuilder> userChats = new HashMap<>();
    private Map<String, StringBuilder> groupChats = new HashMap<>();
    
    private String currentChatType;
    private String currentChatName;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        VBox chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));

        chatArea = new VBox(5);
        chatArea.setPadding(new Insets(10));
        chatArea.setStyle("-fx-background-color: #f4f4f4;");

        scrollPane = new ScrollPane(chatArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background-color: transparent;");

        messageInput = new TextField();

        messageInput.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                sendMessage();
                event.consume();
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
            }
        });

        HBox messageBox = new HBox(10, messageInput, sendButton, sendImageButton);
        chatBox.getChildren().addAll(chatArea, scrollPane, messageBox);

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
            primaryStage.close();
            System.exit(0);
        });

        HBox topBar = new HBox();
        topBar.setPadding(new Insets(10, 10, 0, 10));
        topBar.setAlignment(Pos.TOP_RIGHT);
        topBar.getChildren().add(logoutButton);

        VBox rootLayout = new VBox(topBar, mainLayout);

        Scene scene = new Scene(rootLayout, 700, 500);
        scene.getStylesheets().add("styles.css");

        userGroupBox.prefWidthProperty().bind(scene.widthProperty().multiply(0.25)); 
        userListView.prefHeightProperty().bind(scene.heightProperty().multiply(0.35));
        groupListView.prefHeightProperty().bind(scene.heightProperty().multiply(0.35));

        chatBox.prefWidthProperty().bind(scene.widthProperty().multiply(0.75));
        chatArea.prefHeightProperty().bind(scene.heightProperty().subtract(messageBox.heightProperty()).multiply(0.8));
        messageInput.prefWidthProperty().bind(chatBox.widthProperty().subtract(sendButton.widthProperty()).multiply(0.85));

        primaryStage.setScene(scene);
        primaryStage.setTitle("Chat Client");
        primaryStage.show();

        connectToServer("10.17.235.2", 8000);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.5));
        scrollPane.setVvalue(1.5);
    }



     private void connectToServer(String hostname, int port) {
        try {
            socket = new Socket(hostname, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            Pair<String, String> credentials = promptLoginAndPassword();
            String login = credentials.getKey();
            String password = credentials.getValue();

            out.println(login);
            out.println(password);
            clientName = login;

            new Thread(new Listener()).start();

        } catch (IOException e) {
            showErrorDialog("Connection Error", "Unable to connect to the server. Please try again later.");
            e.printStackTrace();
        }
    }

    private Pair<String, String> promptLoginAndPassword() {
        Dialog<Pair<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Login");
        dialog.setHeaderText("Enter your login and password");

        ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

        TextField loginField = new TextField();
        loginField.setPromptText("Login");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Login:"), 0, 0);
        grid.add(loginField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == loginButtonType) {
                return new Pair<>(loginField.getText(), passwordField.getText());
            }
            return null;
        });

        Optional<Pair<String, String>> result = dialog.showAndWait();
        return result.orElse(new Pair<>("Anonymous", ""));
    }

    private void sendMessage() {
        String message = messageInput.getText();
        if (!message.isEmpty()) {
            if (currentChatType != null && currentChatName != null) {
                if (currentChatType.equals("user")) {
                    out.println("Private to " + currentChatName + ": " + message);
                    appendToUserChat(currentChatName, message, true);  // Just "You: message"
                    scrollToBottom();
                } else if (currentChatType.equals("group")) {
                    if (joinedGroups.contains(currentChatName)) {
                        out.println("Group " + currentChatName + ": " + message);
                        appendToGroupChat(currentChatName, message, true);  // Just "You: message"
                    } else {
                        Text messageText = new Text("You need to join the group " + currentChatName + " to send messages.\n");
                        chatArea.getChildren().add(messageText);
                    }
                }
            } 
            else {
                out.println(message);
                Text messageText = new Text("You: " + message);
                chatArea.getChildren().add(messageText);
            }
            messageInput.clear();
            scrollToBottom();
        }
    }
    private void applyChatBubbleStyle(VBox messageBox, String sender) {
        if (sender.equals("You") || sender.contains("You")) {
            messageBox.setStyle("-fx-alignment: center-right; "
                                + "-fx-background-color: #A8D5BA; "
                                + "-fx-background-radius: 15px; "
                                + "-fx-padding: 10px; "
                                + "-fx-text-fill: white;");
        } else {
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
                ? "You: " + message
                : userName + ": " + message;

        chatHistory.append(formattedMessage).append("\n");
        // System.out.println("Appended to " + userName + "'s chat: " + formattedMessage);
        scrollToBottom();
        if (currentChatType != null && currentChatType.equals("user") && currentChatName.equals(userName)) {
            VBox messageBox = new VBox();
            messageBox.setSpacing(5);

            Text senderTextNode = new Text(formattedMessage);
            senderTextNode.setStyle("-fx-font-weight: bold;");
            messageBox.getChildren().add(senderTextNode);

            applyChatBubbleStyle(messageBox, sender);

            Platform.runLater(() -> {
                chatArea.getChildren().add(messageBox);
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
                byte[] imageData = Files.readAllBytes(file.toPath());
                String base64Image = Base64.getEncoder().encodeToString(imageData);
                String header = (isGroup ? "/groupimage " : "/privateimage ") + clientName + " " + recipient + " " + file.getName() + " " + base64Image.length();
                String message = header + ":" + base64Image;
                if(isGroup){
                    StringBuilder chatHistory = groupChats.computeIfAbsent(recipient, k -> new StringBuilder());
                    String formattedMessage = "[" + recipient + "] "+" You: @imagedata|" + base64Image;
                    chatHistory.append(formattedMessage).append("\n");
                }
                else{
                    StringBuilder chatHistory = userChats.computeIfAbsent(recipient, k -> new StringBuilder());
                    String formattedMessage = "You: @imagedata|" + base64Image;
                    chatHistory.append(formattedMessage).append("\n");
                }
                // System.out.println(message);
                out.println(message);
                // System.out.println("Image sent as base64.");

                appendImageToChat(recipient, file.getName(), imageData, true);
                scrollToBottom();

            } 
            catch (IOException e) {
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

        VBox messageBox = new VBox();
        messageBox.setSpacing(5);

        Text senderTextNode = new Text(messageText);
        senderTextNode.setStyle("-fx-font-weight: bold;");
        messageBox.getChildren().add(senderTextNode);

        Image image = decodeImageData(imageData);
        if (image == null) return;

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(200);
        imageView.setPreserveRatio(true);
        imageView.setOnMouseClicked(event -> openImageInNewWindow(image, fileName));

        messageBox.getChildren().add(imageView);

        applyChatBubbleStyle(messageBox, senderText);
        scrollToBottom();

        Platform.runLater(() -> {
            chatArea.getChildren().add(messageBox);
            scrollToBottom();
            scrollToBottom();
            scrollToBottom();
        });
        scrollToBottom();
    }


    private void openImageInNewWindow(Image image, String fileName) {
        Stage imageStage = new Stage();
        imageStage.setTitle("Image - " + fileName);

        ImageView fullSizeImageView = new ImageView(image);
        fullSizeImageView.setPreserveRatio(true);
        fullSizeImageView.setFitWidth(600);
        fullSizeImageView.setFitHeight(400);

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

        String sender = isSent ? "You" : "[" + groupName + "]";

        String formattedMessage = isSent
                ? "You: " + message // Format sent messages
                : "[" + groupName + "] " + message;

        chatHistory.append(formattedMessage).append("\n");
        // System.out.println("Appended to " + groupName + " chat: " + formattedMessage);

        if (currentChatType != null && currentChatType.equals("group") && currentChatName.equals(groupName)) {
            VBox messageBox = new VBox();
            messageBox.setSpacing(5);

            Text senderTextNode = new Text(formattedMessage);
            senderTextNode.setStyle("-fx-font-weight: bold;");
            messageBox.getChildren().add(senderTextNode);

            applyChatBubbleStyle(messageBox, sender);

            Platform.runLater(() -> {
                chatArea.getChildren().add(messageBox);
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
                joinedGroups.add(groupName);
            }
            Text messageText = new Text("You joined group: " + groupName + "\n");
            chatArea.getChildren().add(messageText);
        } else {
            Text messageText = new Text("No group selected to join.\n");
            chatArea.getChildren().add(messageText);
        }
    }

    private void leaveGroup() {
        String groupName = groupListView.getSelectionModel().getSelectedItem();
        if (groupName != null) {
            out.println("/group leave " + groupName);
            Text messageText = new Text("You left group: " + groupName + "\n");
            chatArea.getChildren().add(messageText);
        } else {
            Text messageText = new Text("No group selected to leave.\n");
            chatArea.getChildren().add(messageText);
        }
    }

    private void displayUserChat(String userName) {
        chatArea.getChildren().clear();
        StringBuilder chatHistory = userChats.getOrDefault(userName, new StringBuilder());
        String chatContent = chatHistory.toString();
        String[] messages = chatContent.split("\n");

        for (String message : messages) {
            if (message.contains("@imagedata|")) {
                String[] parts = message.split("@imagedata\\|");
                if (parts.length > 1) {
                    String sender = message.split(":")[0];
                    String base64ImageData = parts[1];

                    VBox messageBox = new VBox();
                    messageBox.setSpacing(5);

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
                        // System.out.println("Error decoding base64 image.");
                    }
                    chatArea.getChildren().add(messageBox);
                }
            } else {
                String sender = message.split(":")[0];
                String messageText = message.substring(message.indexOf(":") + 1).trim();

                VBox messageBox = new VBox();
                messageBox.setSpacing(5);

                Text senderText = new Text(sender + ": ");
                senderText.setStyle("-fx-font-weight: bold;");
                messageBox.getChildren().add(senderText);

                Text messageTextNode = new Text(messageText);
                messageBox.getChildren().add(messageTextNode);

                applyChatBubbleStyle(messageBox, sender);

                chatArea.getChildren().add(messageBox);
            }
        }
        scrollToBottom();
    }

    private void displayGroupChat(String groupName) {
        if (!joinedGroups.contains(groupName)) {
            Platform.runLater(() -> {
                chatArea.getChildren().clear();
                Text messageText = new Text("You need to join the group to view messages.");
                chatArea.getChildren().add(messageText);
            });
            return;
        }

        chatArea.getChildren().clear();
        StringBuilder groupChatHistory = groupChats.getOrDefault(groupName, new StringBuilder());
        String chatContent = groupChatHistory.toString();
        String[] messages = chatContent.split("\n");

        for (String message : messages) {
            if (message.contains("@imagedata|")) {
                String[] parts = message.split("@imagedata\\|");
                if (parts.length > 1) {
                    String sender = message.split(":")[0];
                    String base64ImageData = parts[1];

                    VBox messageBox = new VBox();
                    messageBox.setSpacing(5);

                    Text senderText = new Text(sender + ": ");
                    senderText.setStyle("-fx-font-weight: bold;");
                    messageBox.getChildren().add(senderText);

                    applyChatBubbleStyle(messageBox, sender);

                    try {
                        byte[] imageData = Base64.getDecoder().decode(base64ImageData);
                        ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
                        Image image = new Image(bis);
                        ImageView imageView = new ImageView(image);
                        imageView.setFitWidth(200);
                        imageView.setPreserveRatio(true);
                        imageView.setOnMouseClicked(event -> openImageInNewWindow(image, "image"));
                        messageBox.getChildren().add(imageView);
                    } catch (Exception e) {
                        e.printStackTrace();
                        // System.out.println("Error decoding base64 image.");
                    }

                    chatArea.getChildren().add(messageBox);
                }
            } else {
                String sender = message.split(":")[0];
                String messageText = message.substring(message.indexOf(":") + 1).trim();

                VBox messageBox = new VBox();
                messageBox.setSpacing(5);

                Text senderText = new Text(sender + ": ");
                senderText.setStyle("-fx-font-weight: bold;");
                messageBox.getChildren().add(senderText);

                Text messageTextNode = new Text(messageText);
                messageBox.getChildren().add(messageTextNode);

                applyChatBubbleStyle(messageBox, sender);

                chatArea.getChildren().add(messageBox);
            }
        }
        scrollToBottom();
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
                    // System.out.println(message);
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
                            receiveImage(message);
                            scrollToBottom();
                        } else if (message.startsWith("/groupimage ")) {
                            receiveImageGroup(message);
                        } else {
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
            String[] parts = message.split(":", 2);
            String header = parts[0];
            String base64ImageData = parts[1];

            String[] tokens = header.split(" ");
            String sender = tokens[1];
            String fileName = tokens[3];

            StringBuilder chatHistory = userChats.computeIfAbsent(sender, k -> new StringBuilder());
            String formattedMessage = sender+": @imagedata|" + base64ImageData;
            chatHistory.append(formattedMessage).append("\n");
            byte[] imageData = Base64.getDecoder().decode(base64ImageData);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            ImageView imageView = new ImageView(image);

            appendImageToChat(sender, fileName, imageData, false);

            // System.out.println("Image received from " + sender);

        } catch (Exception e) {
            e.printStackTrace();
            // System.out.println("Error decoding base64 image.");
        }
    }

    private void receiveImageGroup(String message) {
        try {
            String[] parts = message.split(":", 2);
            String header = parts[0];
            String base64ImageData = parts[1];

            String[] tokens = header.split(" ");
            String sender = tokens[1];
            String group = tokens[2];
            String fileName = tokens[3];

            StringBuilder chatHistory = groupChats.computeIfAbsent(group, k -> new StringBuilder());
        
            String formattedMessage = "[" + group + "] "+ sender +": @imagedata|" + base64ImageData;

            chatHistory.append(formattedMessage).append("\n");

            byte[] imageData = Base64.getDecoder().decode(base64ImageData);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            Image image = new Image(bis);
            ImageView imageView = new ImageView(image);

            appendImageToChat(sender, fileName, imageData, false);

            // System.out.println("Image received from " + sender);

        } catch (Exception e) {
            e.printStackTrace();
            // System.out.println("Error decoding base64 image.");
        }
    }



    private void updateUserList(String message) {
        userListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String[] userParts = item.split(":");
                    String username = userParts[0].trim();
                    String status = userParts[1].trim();

                    setText(username);
                    if (status.equalsIgnoreCase("online")) {
                        setTextFill(javafx.scene.paint.Color.GREEN);
                    } else {
                        setTextFill(javafx.scene.paint.Color.GRAY);
                    }
                }
            }
        });

        userListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                String[] userParts = newValue.split(":");
                String selectedUsername = userParts[0].trim();
                
                currentChatType = "user";
                currentChatName = selectedUsername;

                displayUserChat(selectedUsername);
            }
        });

        String[] users = message.substring(10).split(",");
        Platform.runLater(() -> {
            userListView.getItems().clear();

            for (String user : users) {
                String[] userParts = user.split(":");
                String username = userParts[0].trim();

                if (!username.equals(clientName)) {
                    userListView.getItems().add(user);
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

        if(message.startsWith("History:")){
                String[] parts = message.split(": ", 4);

                String senderName = parts[1];
                String[] recipientPart = parts[2].split(" ", 3);
                String recipientName = recipientPart[2];
                String msg = parts[3];

                String chatKey = senderName.equals(clientName) ? recipientName : senderName;

                String formattedMessage;
                if (senderName.equals(clientName)) {
                    formattedMessage = "You: " + msg;
                } else {
                    formattedMessage = senderName + ": " + msg;
                }
                userChats.computeIfAbsent(chatKey, k -> new StringBuilder()).append(formattedMessage).append("\n");
                return;
        }

        int senderEndIndex = message.indexOf(":");
        if (senderEndIndex == -1) {
            // System.out.println("Invalid message format: No sender found.");
            return;
        }
        String sender = message.substring(0, senderEndIndex).trim();

        String privateMessageIndicator = "Private to ";
        int privateMessageIndex = message.indexOf(privateMessageIndicator);
        if (privateMessageIndex == -1) {
            // System.out.println("Invalid message format: No 'Private to' indicator found.");
            return;
        }

        int recipientStartIndex = privateMessageIndex + privateMessageIndicator.length();
        int recipientEndIndex = message.indexOf(":", recipientStartIndex);
        if (recipientEndIndex == -1) {
            // System.out.println("Invalid message format: No recipient found.");
            return;
        }
        String recipient = message.substring(recipientStartIndex, recipientEndIndex).trim();

        String actualMessage = message.substring(recipientEndIndex + 1).trim();

        if (recipient.equals(clientName)) {
            appendToUserChat(sender, actualMessage, false);
            scrollToBottom();
        }
    }
    private void handleGroupMessage(String message) {   
        if(message.startsWith("History:")){
                String[] parts = message.split(": ", 4);

                String senderName = parts[1];
                String[] rec = parts[2].split(" ", 2);
                String grpname = rec[1];
                String msg = parts[3]; 

                String chatKey = grpname;
                String formattedMessage;
                if (senderName.equals(clientName)) {
                    formattedMessage = "[" + grpname + "] " + "You: " + msg;
                } else {
                    formattedMessage = "[" + grpname + "] " + senderName + ": " + msg;
                }
                
                groupChats.computeIfAbsent(chatKey, k -> new StringBuilder()).append(formattedMessage).append("\n");
                return;
        }

        int senderEndIndex = message.indexOf(":");
        if (senderEndIndex == -1) {
            // System.out.println("Invalid message format: No sender found.");
            return;
        }
        String sender = message.substring(0, senderEndIndex).trim();

        int groupStartIndex = message.indexOf("Group ") + "Group ".length();
        int groupEndIndex = message.indexOf(":", groupStartIndex);
        if (groupEndIndex == -1) {
            // System.out.println("Invalid message format: No group name found.");
            return;
        }
        String group = message.substring(groupStartIndex, groupEndIndex).trim();

        if (!joinedGroups.contains(group)) {
            // System.out.println("User has not joined the group: " + group);
            return;
        }

        String actualMessage = message.substring(groupEndIndex + 1).trim();

        appendToGroupChat(group, sender + ": " + actualMessage, false);
    }
}