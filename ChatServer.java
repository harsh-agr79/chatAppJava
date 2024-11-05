import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;

class DatabaseHelper {

    private Connection connection;

    public DatabaseHelper() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:chat.db");
            initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void initializeDatabase() throws SQLException {
        String userTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "recipient_name TEXT, " +
                "group_name TEXT" +
                ");";
    
        String messageTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT NOT NULL, " +
                "recipient TEXT, " +
                "groupname TEXT, " +
                "content TEXT NOT NULL, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";

        String groupTable = "CREATE TABLE IF NOT EXISTS groups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "groupname TEXT, " +
                "groupmembers TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";
    
    
        String mediaTable = "CREATE TABLE IF NOT EXISTS media (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT NOT NULL, " +
                "recipient TEXT, " +
                "groupname TEXT, " +
                "filename TEXT NOT NULL, " +
                "file_data BLOB, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";
    
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(userTable);
            stmt.execute(messageTable);
            stmt.execute(mediaTable);
            stmt.execute(groupTable);  // Create media table
        }
    }
    

    public boolean addUser(String username, String password) {
        String query = "INSERT OR IGNORE INTO users (username, password) VALUES (?, ?);";
        String hashedPassword = hashPassword(password);

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    public boolean saveMedia(String sender, String recipient, String groupname, String filename, byte[] fileData) {
        String query = "INSERT INTO media (sender, recipient, groupname, filename, file_data) VALUES (?, ?, ?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, groupname);
            pstmt.setString(4, filename);
            pstmt.setBytes(5, fileData);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    public ResultSet getMedia(String username) {
        String query = "SELECT * FROM media WHERE sender = ? OR recipient = ? ORDER BY timestamp;";
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, username);
            pstmt.setString(2, username);
            return pstmt.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
    

    public boolean authenticateUser(String username, String password) {
        String query = "SELECT password FROM users WHERE username = ?;";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, username);
            ResultSet result = pstmt.executeQuery();

            if (result.next()) {
                String storedPassword = result.getString("password");
                return storedPassword.equals(hashPassword(password));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }
    public List<String> getGroupNames() {
        List<String> groupNames = new ArrayList<>();
        String query = "SELECT groupname FROM groups"; // Adjust table/column names to match your database schema

        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String groupName = resultSet.getString("groupname");
                groupNames.add(groupName);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            // Handle exceptions (e.g., logging)
        }

        return groupNames;
    }
    public List<String> getUserGroups(String username) {
    List<String> userGroups = new ArrayList<>();
    String query = "SELECT groupname, groupmembers FROM groups";

    try (Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(query)) {

        while (resultSet.next()) {
            String groupName = resultSet.getString("groupname");
            String members = resultSet.getString("groupmembers");

            // Check if the username is in the comma-separated list of members
            List<String> memberList = Arrays.asList(members.split(","));
            if (memberList.contains(username)) {
                userGroups.add(groupName);
            }
        }
    } catch (SQLException e) {
        e.printStackTrace();
        // Handle exceptions, e.g., logging
    }
    return userGroups;
    }


    public void saveMessage(String sender, String recipient, String groupname, String content) {
        String query = "INSERT INTO messages (sender, recipient, groupname, content) VALUES (?, ?, ?, ?);";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, sender);
            pstmt.setString(2, recipient);
            pstmt.setString(3, groupname);
            pstmt.setString(4, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

   
    public ResultSet getUserMessages(String username) {
    // Retrieve the groups the user has joined
    List<String> userGroups = getUserGroups(username);
    String groupPlaceholders = String.join(",", Collections.nCopies(userGroups.size(), "?"));

    // Modify query to include the restriction and load messages accordingly
    String query = "SELECT * FROM messages " +
                   "WHERE ((sender = ? OR recipient = ?) " +
                   "OR (groupname IS NOT NULL AND groupname IN (" + groupPlaceholders + "))) " +
                   "AND ((recipient IS NOT NULL AND groupname IS NULL) " +
                   "OR (recipient IS NULL AND groupname IS NOT NULL)) " +
                   "ORDER BY timestamp;";

    try {
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, username);
        pstmt.setString(2, username);

        // Set group names in the placeholders dynamically
        int index = 3; // Start setting group names from the 3rd placeholder
        for (String group : userGroups) {
            pstmt.setString(index++, group);
        }

        return pstmt.executeQuery();
    } catch (SQLException e) {
        e.printStackTrace();
        return null;
    }
}

public ResultSet getGroupMessages(String username) {
    // Retrieve the groups the user has joined
    List<String> userGroups = getUserGroups(username);
    
    if (userGroups.isEmpty()) {
        return null; // If the user is not part of any groups, return null or handle accordingly
    }
    
    // Create placeholders for the groups in the SQL query
    String groupPlaceholders = String.join(",", Collections.nCopies(userGroups.size(), "?"));
    
    // SQL query to retrieve only group messages for the groups the user is part of
    String query = "SELECT * FROM messages " +
                   "WHERE groupname IS NOT NULL " +
                   "AND groupname IN (" + groupPlaceholders + ") " +
                   "ORDER BY timestamp;";
    
    try {
        PreparedStatement pstmt = connection.prepareStatement(query);
        
        // Set group names dynamically in the placeholders
        int index = 1;
        for (String group : userGroups) {
            pstmt.setString(index++, group);
        }
        
        return pstmt.executeQuery();
    } catch (SQLException e) {
        e.printStackTrace();
        return null;
    }
}



    public List<String> getAllUsers() {
    List<String> users = new ArrayList<>();
    String query = "SELECT username FROM users;";
    try (Statement stmt = connection.createStatement();
         ResultSet result = stmt.executeQuery(query)) {
        while (result.next()) {
            users.add(result.getString("username"));
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return users;
}


     public boolean createGroup(String groupName) {
            String query = "INSERT OR IGNORE INTO groups (groupname, groupmembers) VALUES (?, ?);";
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, groupName);
                pstmt.setString(2, "");  // Initially no members in the group
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
    }

        // Add a member to a group
        public boolean addMemberToGroup(String groupName, String username) {
            String selectQuery = "SELECT groupmembers FROM groups WHERE groupname = ?;";
            String updateQuery = "UPDATE groups SET groupmembers = ? WHERE groupname = ?;";
            try {
                // Get current members
                String currentMembers;
                try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery)) {
                    selectStmt.setString(1, groupName);
                    ResultSet rs = selectStmt.executeQuery();
                    if (rs.next()) {
                        currentMembers = rs.getString("groupmembers");
                    } else {
                        return false; // Group does not exist
                    }
                }

                // Update members list
                String updatedMembers = currentMembers.isEmpty() ? username : currentMembers + "," + username;
                try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                    updateStmt.setString(1, updatedMembers);
                    updateStmt.setString(2, groupName);
                    updateStmt.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        // Remove a member from a group
        public boolean removeMemberFromGroup(String groupName, String username) {
            String selectQuery = "SELECT groupmembers FROM groups WHERE groupname = ?;";
            String updateQuery = "UPDATE groups SET groupmembers = ? WHERE groupname = ?;";
            try {
                // Get current members
                String currentMembers;
                try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery)) {
                    selectStmt.setString(1, groupName);
                    ResultSet rs = selectStmt.executeQuery();
                    if (rs.next()) {
                        currentMembers = rs.getString("groupmembers");
                    } else {
                        return false; // Group does not exist
                    }
                }

                // Update members list
                List<String> membersList = new ArrayList<>(Arrays.asList(currentMembers.split(",")));
                membersList.remove(username);
                String updatedMembers = String.join(",", membersList);

                try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                    updateStmt.setString(1, updatedMembers);
                    updateStmt.setString(2, groupName);
                    updateStmt.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

    public void close() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}


public class ChatServer {

    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static Map<String, Set<ClientHandler>> groups = new HashMap<>();
    private static DatabaseHelper dbHelper = new DatabaseHelper();

  public static void main(String[] args) {
    int port = 8000;
    String localIPAddress = "10.17.235.2";
    
    // Initialize clientHandlers from the database
    List<String> users = dbHelper.getAllUsers();
    System.out.println(users);
    for (String username : users) {
        ClientHandler dummyClientHandler = new ClientHandler(null, clientHandlers, groups, dbHelper);
        dummyClientHandler.setClientName(username);  // Set the client name
        clientHandlers.add(dummyClientHandler);
    }
    List<String> groupNames = dbHelper.getGroupNames(); // Fetch group names from the database

    for (String groupName : groupNames) {
        groups.put(groupName, new HashSet<>()); // Initialize each group with an empty set of ClientHandlers
    }

    try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(localIPAddress))) {
        System.out.println("Server is listening on IP " + localIPAddress + " and port " + port);

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("New client connected");
            ClientHandler clientHandler = new ClientHandler(socket, clientHandlers, groups, dbHelper);
            clientHandler.setClientName(" ");
            clientHandlers.add(clientHandler);
            new Thread(clientHandler).start();
        }
    } catch (IOException e) {
        e.printStackTrace();
    }
}

    static void removeClient(String username) {
    Iterator<ClientHandler> iterator = clientHandlers.iterator();
    while (iterator.hasNext()) {
        ClientHandler client = iterator.next();
        if (client.getClientName() != null && client.getClientName().equals(username)) {
            iterator.remove(); // Safely remove the client
            break; // Exit after finding and removing the client
        }
    }
}



    // Broadcast updated user list to all clients
    static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("/userlist ");
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) {
                if (client.socket == null) {
                    // System.out.println("offline,");
                    userList.append(client.getClientName() + ":offline").append(",");
                } else if(client.socket.isConnected() && !client.socket.isClosed()){
                    // System.out.println("online,");
                    userList.append(client.getClientName() + ":online").append(",");
                }
                else{
                    // System.out.println("offline,");
                    userList.append(client.getClientName() + ":offline").append(",");
                }
                // userList.append(client.getClientName()).append(",");
            }
        }
        String userListMessage = userList.toString();
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) {
                client.sendMessage(userListMessage);
            }
        }
    }

    // Broadcast updated group list to all clients
    static void broadcastGroupList() {
        StringBuilder groupList = new StringBuilder("/grouplist ");
        for (String groupName : groups.keySet()) {
            groupList.append(groupName).append(",");
        }
        String groupListMessage = groupList.toString();
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) {
                client.sendMessage(groupListMessage);
            }
        }
    }
}

class ClientHandler implements Runnable {

    public Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private Set<ClientHandler> clientHandlers;
    private Map<String, Set<ClientHandler>> groups;
    private DatabaseHelper dbHelper;

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public ClientHandler(Socket socket, Set<ClientHandler> clientHandlers, Map<String, Set<ClientHandler>> groups, DatabaseHelper dbHelper) {
        this.socket = socket;
        this.clientHandlers = clientHandlers;
        this.groups = groups;
        this.dbHelper = dbHelper;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Ask for username and password
            out.println("Enter your username:");
            String username = in.readLine();
            out.println("Enter your password:");
            String password = in.readLine();

            // ChatServer.removeClient(username);

            // Check user authentication or register if new user
            if (dbHelper.authenticateUser(username, password)) {
                ChatServer.removeClient(username);
                clientName = username;
                out.println("Welcome back, " + clientName + "!");
                // Retrieve and display chat history
                joinUserToGroups();
                displayChatHistory(username);
            } else if (dbHelper.addUser(username, password)) {
                clientName = username;
                out.println("Account created. Welcome, " + clientName + "!");
            } else {
                out.println("Invalid login or registration error.");
                closeConnection();
                return;
            }
            
            

            ChatServer.broadcastUserList();
            broadcast(clientName + " has joined the chat", null);
            ChatServer.broadcastGroupList();

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("main handler: "+ message);
                if (message.startsWith("Private to")) {
                    String fullMessage = clientName + ": " + message;
                    handlePrivateMessage(fullMessage);
                } else if (message.startsWith("/group")) {
                    handleGroupCommand(message);
                } else if (message.startsWith("Group")){
                    sendGroupMessage(message, this);
                } else {
                    String fullMessage = clientName + ": " + message;
                    broadcast(fullMessage, this);
                    dbHelper.saveMessage(clientName, null, null, fullMessage); // Save public message
                }
                // Inside the run() method or message-handling logic
                if (message.startsWith("/sendfile")) {
                    String[] tokens = message.split(" ", 2);
                    String fileName = tokens[1];
                    out.println("Ready to receive file: " + fileName);
                    handleFileTransfer(fileName);
}

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }
    public void joinUserToGroups() {
        List<String> userGroups = dbHelper.getUserGroups(clientName); // Retrieve the groups for this user
        System.out.println("usergrps: "+ userGroups);
        for (String group : userGroups) {
            // Check if the group exists in the groups map, then add the user to it
            groups.computeIfPresent(group, (key, members) -> {
                members.add(this); // Add the ClientHandler instance to the set
                return members;
            });
            // System.out.println("System groups: "+ groups);
            // groups.get(group).add(this);
        }
        out.println("/joinedGroups "+ String.join(", ", userGroups));
    }
    private void displayChatHistory(String username) {
        try {
            ResultSet chatHistory = dbHelper.getUserMessages(username);
            while (chatHistory != null && chatHistory.next()) {
                String sender = chatHistory.getString("sender");
                String recipient = chatHistory.getString("recipient");
                String group = chatHistory.getString("groupname");
                String content = chatHistory.getString("content");
                out.println("History: "+content);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    private void handleFileTransfer(String fileName) {
        try {
            // Create an output stream to save the file
            FileOutputStream fileOutputStream = new FileOutputStream("uploads/" + fileName);
            byte[] buffer = new byte[4096];
            int bytesRead;
    
            // Read the incoming file data
            while ((bytesRead = socket.getInputStream().read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, bytesRead);
                if (bytesRead < buffer.length) {
                    break; // Break when file reading is complete
                }
            }
    
            fileOutputStream.close();
            System.out.println("File " + fileName + " received successfully.");
            broadcast("File " + fileName + " uploaded by " + clientName, null);
            
        } catch (IOException e) {
            e.printStackTrace();
            out.println("Error receiving the file.");
        }
    }
    

    private String formatMessage(String sender, String recipient, String group, String content) {
        if (group != null) {
            return "[Group " + group + "] " + sender + ": " + content;
        } else if (recipient != null) {
            return "Private from " + sender + ": " + content;
        } else {
            return sender + ": " + content;
        }
    }

    private void handlePrivateMessage(String message) {
        System.out.println(message);
        String[] senderSplit = message.split(":", 2); 
        String remainingMessage = senderSplit[1].trim(); // "Private to dev: sdfkjnfds redo this"

        // Step 2: Split by "Private to" to isolate recipient and message
        String[] privateSplit = remainingMessage.split("Private to", 2);
        String recipientPart = privateSplit[1].trim(); // "dev: sdfkjnfds redo this"

        // Step 3: Split by ":" to get recipient and actual message
        String[] recipientAndMessage = recipientPart.split(":", 2);

        String recipientName = recipientAndMessage[0].trim(); // "dev"
        String privateMessage = recipientAndMessage[1].trim(); // "sdfkjnfds redo this"
        System.out.println(recipientName+privateMessage);
        sendPrivateMessage(recipientName, message);
    }

    private void handleGroupCommand(String message) {
        String[] tokens = message.split(" ", 3);
        String command = tokens[1];

        switch (command) {
            case "create":
                createGroup(tokens[2]);
                break;
            case "join":
                joinGroup(tokens[2]);
                break;
            case "leave":
                leaveGroup(tokens[2]);
                break;
            default:
                out.println("Invalid group command");
        }

        System.out.println("System groups: "+ groups);
    }

    private void createGroup(String groupName) {
        if (!groups.containsKey(groupName)) {
            groups.put(groupName, new HashSet<>());
            out.println("Group " + groupName + " created.");
            ChatServer.broadcastGroupList();
            dbHelper.createGroup(groupName);
        } else {
            out.println("Group " + groupName + " already exists.");
        }
    }

    private void joinGroup(String groupName) {
        if (groups.containsKey(groupName)) {
            groups.get(groupName).add(this);
            out.println("You joined group " + groupName);
            ChatServer.broadcastGroupList();
            dbHelper.addMemberToGroup(groupName, this.clientName);
             try {
                ResultSet chatHistory = dbHelper.getGroupMessages(this.clientName);
                while (chatHistory != null && chatHistory.next()) {
                    String sender = chatHistory.getString("sender");
                    String recipient = chatHistory.getString("recipient");
                    String group = chatHistory.getString("groupname");
                    String content = chatHistory.getString("content");
                    out.println("History: "+content);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            out.println("Group " + groupName + " does not exist.");
        }
    }

    private void leaveGroup(String groupName) {
        if (groups.containsKey(groupName) && groups.get(groupName).contains(this)) {
            groups.get(groupName).remove(this);
            out.println("You left group " + groupName);
            ChatServer.broadcastGroupList();
            dbHelper.removeMemberFromGroup(groupName, this.clientName);
        } else {
            out.println("You are not a member of group " + groupName);
        }
    }

    private void sendGroupMessage(String groupMessage, ClientHandler sender) {
        String[] splitMessage = groupMessage.split(" ", 3);
        String[] group = splitMessage[1].split(":",2);
        String groupName = group[0];
        String message = splitMessage[2];

        if (groups.containsKey(groupName)) {
            for (ClientHandler client : groups.get(groupName)) {
                if(client != sender){
                    client.out.println(sender.clientName+": " + groupMessage);
                }
            }
            String msg = sender.clientName+": " + groupMessage;
            dbHelper.saveMessage(sender.clientName, null, groupName, msg); // Save group message
        } else {
            out.println("Group " + groupName + " does not exist.");
        }
    }

    private void sendPrivateMessage(String recipientName, String message) {
        for (ClientHandler client : clientHandlers) {
            if (client.clientName.equals(recipientName)) {
                client.out.println(message);
                dbHelper.saveMessage(clientName, recipientName, null, message); // Save private message
                return;
            }
        }
        out.println("User " + recipientName + " not found.");
    }

  public void broadcast(String message, ClientHandler excludeClient) {
    for (ClientHandler clientHandler : clientHandlers) {
        if (clientHandler.out != null) {  // Only broadcast to active clients
            clientHandler.out.println(message);
            clientHandler.out.flush();
        }
    }
}


    private void closeConnection() {
        try {
            // clientHandlers.remove(this);
            // this.socket
            socket.close();
            broadcast(clientName + " has left the chat", null);
            ChatServer.broadcastUserList();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getClientName() {
        return clientName;
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}