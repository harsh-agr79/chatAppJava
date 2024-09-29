
import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {

    private static Set<ClientHandler> clientHandlers = new HashSet<>();
    private static Map<String, Set<ClientHandler>> groups = new HashMap<>();

    public static void main(String[] args) {
        int port = 8000;

        // Replace with your local IP address on the WiFi network
        String localIPAddress = "10.17.235.2";

        try (ServerSocket serverSocket = new ServerSocket(port, 50, InetAddress.getByName(localIPAddress))) {
            System.out.println("Server is listening on IP " + localIPAddress + " and port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");
                ClientHandler clientHandler = new ClientHandler(socket, clientHandlers, groups);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start(); // Start the client handler thread, initialize before broadcasting user list
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Broadcast updated user list to all clients
    static void broadcastUserList() {
        StringBuilder userList = new StringBuilder("/userlist ");
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) { // Ensure clientName is set
                userList.append(client.getClientName()).append(",");
            }
        }
        String userListMessage = userList.toString();
        for (ClientHandler client : clientHandlers) {
            if (client.getClientName() != null) { // Ensure clientName is set
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
            if (client.getClientName() != null) { // Ensure clientName is set
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

    public ClientHandler(Socket socket, Set<ClientHandler> clientHandlers, Map<String, Set<ClientHandler>> groups) {
        this.socket = socket;
        this.clientHandlers = clientHandlers;
        this.groups = groups;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out.println("Enter your name:");
            clientName = in.readLine();

            broadcast(clientName + " has joined the chat", null);
            ChatServer.broadcastUserList(); // Broadcast updated user list after initialization is complete

            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("@")) {
                    handlePrivateMessage(message);
                } else if (message.startsWith("/group")) {
                    handleGroupCommand(message);
                } else {
                    broadcast(clientName + ": " + message, this);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection();
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
            ChatServer.broadcastGroupList(); // Broadcast updated group list
        } else {
            out.println("Group " + groupName + " already exists.");
        }
    }

    private void joinGroup(String groupName) {
        if (groups.containsKey(groupName)) {
            groups.get(groupName).add(this);
            out.println("You joined group " + groupName);
            ChatServer.broadcastGroupList(); // Broadcast updated group list
        } else {
            out.println("Group " + groupName + " does not exist.");
        }
    }

    private void leaveGroup(String groupName) {
        if (groups.containsKey(groupName) && groups.get(groupName).contains(this)) {
            groups.get(groupName).remove(this);
            out.println("You left group " + groupName);
            ChatServer.broadcastGroupList(); // Broadcast updated group list
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
        } else {
            out.println("Group " + groupName + " does not exist.");
        }
    }

    private void sendPrivateMessage(String recipientName, String message) {
        for (ClientHandler client : clientHandlers) {
            if (client.clientName.equals(recipientName)) {
                client.out.println("Private from " + clientName + ": " + message);
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
            ChatServer.broadcastUserList(); // Broadcast updated user list
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
