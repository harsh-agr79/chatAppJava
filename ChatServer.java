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
                "password TEXT NOT NULL" +  // Add password column
                ");";

        String messageTable = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sender TEXT NOT NULL, " +
                "recipient TEXT, " +
                "groupname TEXT, " +
                "content TEXT NOT NULL, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(userTable);
            stmt.execute(messageTable);
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
        String query = "SELECT * FROM messages WHERE sender = ? OR recipient = ? ORDER BY timestamp;";
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
        // Replace with your local IP address on the WiFi network
        String localIPAddress = "10.17.235.2";

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(localIPAddress))) {
            System.out.println("Server is listening on IP " + localIPAddress + " and port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");
                ClientHandler clientHandler = new ClientHandler(socket, clientHandlers, groups, dbHelper);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast updated user list to all clients
    static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("/userlist ");
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) {
                userList.append(client.getClientName()).append(",");
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

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String clientName;
    private Set<ClientHandler> clientHandlers;
    private Map<String, Set<ClientHandler>> groups;
    private DatabaseHelper dbHelper;

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

            // Check user authentication or register if new user
            if (dbHelper.authenticateUser(username, password)) {
                clientName = username;
                out.println("Welcome back, " + clientName + "!");
                // Retrieve and display chat history
                displayChatHistory(username);
            } else if (dbHelper.addUser(username, password)) {
                clientName = username;
                out.println("Account created. Welcome, " + clientName + "!");
            } else {
                out.println("Invalid login or registration error.");
                closeConnection();
                return;
            }

            broadcast(clientName + " has joined the chat", null);
            ChatServer.broadcastUserList();

            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("@")) {
                    handlePrivateMessage(message);
                } else if (message.startsWith("/group")) {
                    handleGroupCommand(message);
                } else {
                    String fullMessage = clientName + ": " + message;
                    broadcast(fullMessage, this);
                    dbHelper.saveMessage(clientName, null, null, fullMessage); // Save public message
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private void displayChatHistory(String username) {
        try {
            ResultSet chatHistory = dbHelper.getUserMessages(username);
            while (chatHistory != null && chatHistory.next()) {
                String sender = chatHistory.getString("sender");
                String recipient = chatHistory.getString("recipient");
                String group = chatHistory.getString("groupname");
                String content = chatHistory.getString("content");
                out.println(formatMessage(sender, recipient, group, content));
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        String[] splitMessage = message.split(" ", 2);
        String recipientName = splitMessage[0].substring(1);
        String privateMessage = splitMessage[1];
        sendPrivateMessage(recipientName, privateMessage);
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
            case "msg":
                sendGroupMessage(tokens[2]);
                break;
            default:
                out.println("Invalid group command");
        }
    }

    private void createGroup(String groupName) {
        if (!groups.containsKey(groupName)) {
            groups.put(groupName, new HashSet<>());
            out.println("Group " + groupName + " created.");
            ChatServer.broadcastGroupList();
        } else {
            out.println("Group " + groupName + " already exists.");
        }
    }

    private void joinGroup(String groupName) {
        if (groups.containsKey(groupName)) {
            groups.get(groupName).add(this);
            out.println("You joined group " + groupName);
            ChatServer.broadcastGroupList();
        } else {
            out.println("Group " + groupName + " does not exist.");
        }
    }

    private void leaveGroup(String groupName) {
        if (groups.containsKey(groupName) && groups.get(groupName).contains(this)) {
            groups.get(groupName).remove(this);
            out.println("You left group " + groupName);
            ChatServer.broadcastGroupList();
        } else {
            out.println("You are not a member of group " + groupName);
        }
    }

    private void sendGroupMessage(String groupMessage) {
        String[] splitMessage = groupMessage.split(" ", 2);
        String groupName = splitMessage[0];
        String message = splitMessage[1];

        if (groups.containsKey(groupName)) {
            for (ClientHandler client : groups.get(groupName)) {
                client.out.println("[Group " + groupName + "] " + clientName + ": " + message);
            }
            dbHelper.saveMessage(clientName, null, groupName, message); // Save group message
        } else {
            out.println("Group " + groupName + " does not exist.");
        }
    }

    private void sendPrivateMessage(String recipientName, String message) {
        for (ClientHandler client : clientHandlers) {
            if (client.clientName.equals(recipientName)) {
                client.out.println("Private from " + clientName + ": " + message);
                dbHelper.saveMessage(clientName, recipientName, null, message); // Save private message
                return;
            }
        }
        out.println("User " + recipientName + " not found.");
    }

    private void broadcast(String message, ClientHandler excludeClient) {
        for (ClientHandler client : clientHandlers) {
            if (client != excludeClient) {
                client.out.println(message);
            }
        }
    }

    private void closeConnection() {
        try {
            clientHandlers.remove(this);
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