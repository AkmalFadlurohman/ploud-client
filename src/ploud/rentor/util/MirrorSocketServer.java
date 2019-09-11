package ploud.rentor.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public abstract class MirrorSocketServer implements Runnable, MirrorSocketTask {
    private ServerSocket serverSocket = null;
    private Socket clientSocket = null;
    private Thread serverThread = null;
    private final int port = 8900;
    private volatile boolean running = true;

    public MirrorSocketServer() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Mirror socket server started: " + serverSocket);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void start() {
        if (serverThread == null) {
            serverThread = new Thread(this);
            serverThread.start();
        }
    }

    @Override
    public void run() {
        System.out.println("Mirror socket server is running...");
        while(running) {
            if (!serverSocket.isClosed()) {
                try {
                    System.out.println("Waiting for mirror client...");
                    clientSocket = serverSocket.accept();
                    InetSocketAddress clientAddress = (InetSocketAddress) clientSocket.getRemoteSocketAddress();
                    String clientIP = clientAddress.getAddress().getHostAddress().trim();
                    int clientPublicPort = clientSocket.getPort();
                    int clientLocalPort = clientSocket.getLocalPort();
                    System.out.println("Mirror client accepted: " + clientIP + " ( " + clientLocalPort + " -> " + clientPublicPort + " )");
                    setPublicPort(clientPublicPort);
                } catch (IOException ex) {
                    break;
                }
            } else {
                break;
            }
        }
        System.out.println("Mirror socket server finished running...");
    }

    public void terminate() {
        running = false;
    }

    public void close() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            System.out.println("Closing mirror socket server...");
            try {
                serverSocket.close();
                serverThread = null;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.out.println("Mirror socket server closed...");
        if (clientSocket != null && !clientSocket.isClosed()) {
            System.out.println("Closing any connected socket client...");
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
