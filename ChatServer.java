import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.Base64;

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
    
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(userTable);
            stmt.execute(messageTable);
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
        String query = "SELECT groupname FROM groups";

        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String groupName = resultSet.getString("groupname");
                groupNames.add(groupName);
            }

        } catch (SQLException e) {
            e.printStackTrace();
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

                List<String> memberList = Arrays.asList(members.split(","));
                if (memberList.contains(username)) {
                    userGroups.add(groupName);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        List<String> userGroups = getUserGroups(username);
        String groupPlaceholders = String.join(",", Collections.nCopies(userGroups.size(), "?"));

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

            int index = 3;
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
        List<String> userGroups = getUserGroups(username);
        if (userGroups.isEmpty()) {
            return null;
        }
        String groupPlaceholders = String.join(",", Collections.nCopies(userGroups.size(), "?"));
        String query = "SELECT * FROM messages " +
                    "WHERE groupname IS NOT NULL " +
                    "AND groupname IN (" + groupPlaceholders + ") " +
                    "ORDER BY timestamp;";
        
        try {
            PreparedStatement pstmt = connection.prepareStatement(query);
            
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
                pstmt.setString(2, "");
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
    }

    public boolean addMemberToGroup(String groupName, String username) {
        String selectQuery = "SELECT groupmembers FROM groups WHERE groupname = ?;";
        String updateQuery = "UPDATE groups SET groupmembers = ? WHERE groupname = ?;";
        try {
            String currentMembers;
            try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery)) {
                selectStmt.setString(1, groupName);
                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    currentMembers = rs.getString("groupmembers");
                } else {
                    return false;
                }
            }
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
    public boolean removeMemberFromGroup(String groupName, String username) {
        String selectQuery = "SELECT groupmembers FROM groups WHERE groupname = ?;";
        String updateQuery = "UPDATE groups SET groupmembers = ? WHERE groupname = ?;";
        try {
            String currentMembers;
            try (PreparedStatement selectStmt = connection.prepareStatement(selectQuery)) {
                selectStmt.setString(1, groupName);
                ResultSet rs = selectStmt.executeQuery();
                if (rs.next()) {
                    currentMembers = rs.getString("groupmembers");
                } else {
                    return false;
                }
            }
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
      
        List<String> users = dbHelper.getAllUsers();
        // System.out.println(users);
        for (String username : users) {
            ClientHandler dummyClientHandler = new ClientHandler(null, clientHandlers, groups, dbHelper);
            dummyClientHandler.setClientName(username);
            clientHandlers.add(dummyClientHandler);
        }
        List<String> groupNames = dbHelper.getGroupNames();

        for (String groupName : groupNames) {
            groups.put(groupName, new HashSet<>());
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
                iterator.remove();
                break;
            }
        }
    }

    static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("/userlist ");
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) {
                if (client.socket == null) {
                    userList.append(client.getClientName() + ":offline").append(",");
                } else if(client.socket.isConnected() && !client.socket.isClosed()){
                    userList.append(client.getClientName() + ":online").append(",");
                }
                else{
                    userList.append(client.getClientName() + ":offline").append(",");
                }
            }
        }
        String userListMessage = userList.toString();
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) {
                client.sendMessage(userListMessage);
            }
        }
    }

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

            out.println("Enter your username:");
            String username = in.readLine();
            out.println("Enter your password:");
            String password = in.readLine();

            if (dbHelper.authenticateUser(username, password)) {
                ChatServer.removeClient(username);
                clientName = username;
                out.println("Welcome back, " + clientName + "!");
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
                // System.out.println("main handler: "+ message);
                if (message.startsWith("Private to")) {
                    String fullMessage = clientName + ": " + message;
                    handlePrivateMessage(fullMessage);
                } else if (message.startsWith("/privateimage")) {
                    handleImageReception(message);
                } else if (message.startsWith("/groupimage")) {
                    handleImageReceptionGroup(message);
                }else if (message.startsWith("/group")) {
                    handleGroupCommand(message);
                } else if (message.startsWith("Group")){
                    sendGroupMessage(message, this);
                } else {
                    String fullMessage = message;
                    broadcast(fullMessage, this);
                    dbHelper.saveMessage(clientName, null, null, fullMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }
    public void joinUserToGroups() {
        List<String> userGroups = dbHelper.getUserGroups(clientName);
        // System.out.println("usergrps: "+ userGroups);
        for (String group : userGroups) {
            groups.computeIfPresent(group, (key, members) -> {
                members.add(this);
                return members;
            });
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
    
    private void handleImageReceptionGroup(String message) {
        try {
            String[] parts = message.split(":", 2);
            String header = parts[0];
            String base64ImageData = parts[1];

            String[] tokens = header.split(" ");
            String sender = tokens[1];
            String recipient = tokens[2];
            String fileName = tokens[3];

            if (groups.containsKey(recipient)) {
                for (ClientHandler client : groups.get(recipient)) {
                    if(client != this){
                        ClientHandler recipientHandler = client;
                        if (recipientHandler == null || recipientHandler.socket == null) {
                            // System.out.println("Recipient not connected.");
                            continue;
                        }

                        String fullMessage = header + ":" + base64ImageData;

                        PrintWriter recipientOut = new PrintWriter(recipientHandler.socket.getOutputStream(), true);
                        recipientOut.println(fullMessage);
                        recipientOut.flush();

                        // System.out.println("Image forwarded to recipient: " + recipient);
                    }
                }
            } else {
                out.println("Group " + recipient + " does not exist.");
            }

            String msg = sender + ": Group "+recipient+": @imagedata|"+base64ImageData;

            dbHelper.saveMessage(clientName, null, recipient, msg);
        } catch (IOException e) {
            // System.out.println("Error receiving or forwarding base64 image.");
            e.printStackTrace();
        }

    }

    private void handleImageReception(String message) {
        try {
            String[] parts = message.split(":", 2);
            String header = parts[0];
            String base64ImageData = parts[1];

            String[] tokens = header.split(" ");
            String sender = tokens[1];
            String recipient = tokens[2];
            String fileName = tokens[3];

            ClientHandler recipientHandler = findClientByName(recipient);
            if (recipientHandler == null || recipientHandler.socket == null) {
                // System.out.println("Recipient not connected.");
                return;
            }

            String msg = sender + ": Private to "+recipient+": @imagedata|"+base64ImageData;

            dbHelper.saveMessage(clientName, recipient, null, msg);
            String fullMessage = header + ":" + base64ImageData;
            PrintWriter recipientOut = new PrintWriter(recipientHandler.socket.getOutputStream(), true);
            recipientOut.println(fullMessage);
            recipientOut.flush();
            // System.out.println("Image forwarded to recipient: " + recipient); 
        } catch (IOException e) {
            // System.out.println("Error receiving or forwarding base64 image.");
            e.printStackTrace();
        }

    }


    private ClientHandler findClientByName(String name) {
        for (ClientHandler client : clientHandlers) {
            if (client.clientName.equals(name)) {
                return client;
            }
        }
        return null;
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
        // System.out.println(message);
        String[] senderSplit = message.split(":", 2); 
        String remainingMessage = senderSplit[1].trim();

        String[] privateSplit = remainingMessage.split("Private to", 2);
        String recipientPart = privateSplit[1].trim();

        String[] recipientAndMessage = recipientPart.split(":", 2);

        String recipientName = recipientAndMessage[0].trim();
        String privateMessage = recipientAndMessage[1].trim();
        // System.out.println(recipientName+privateMessage);
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

        // System.out.println("System groups: "+ groups);
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