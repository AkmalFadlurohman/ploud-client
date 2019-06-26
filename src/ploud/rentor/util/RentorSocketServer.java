package ploud.rentor.util;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class RentorSocketServer implements RentorTask, Runnable {
    private ServerSocket serverSocket = null;
    private Socket clientSocket = null;
    private Thread serverThread = null;
    private int clientCount = 0;
    private int maxClient = 15;
    private final ExecutorService clientPool = Executors.newFixedThreadPool(maxClient);
    private volatile boolean running = true;

    public RentorSocketServer(int port) {
        try {
            System.out.println("Binding to port " + port);
            serverSocket = new ServerSocket(port);
            System.out.println("Server started: " + serverSocket);
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
        System.out.println("Rentor socket server is running...");
        while(running) {
            if (!serverSocket.isClosed()) {
                try {
                    System.out.println("Waiting for a client...");
                    clientSocket = serverSocket.accept();
                    acceptClient(clientSocket);
                } catch (IOException ex) {
                    break;
                }
            } else {
                break;
            }
        }
        System.out.println("Rentor socket server finished running...");
    }

    public void terminate() {
        running = false;
    }

    public void stop() {
        if (serverThread != null) {
            System.out.println("Stopping socket server thread...");
            terminate();
        }
    }

    public void close() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            System.out.println("Closing rentor socket server...");
            try {
                serverSocket.close();
                serverThread = null;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        System.out.println("Rentor socket server closed...");
        if (clientSocket != null && !clientSocket.isClosed()) {
            System.out.println("Closing any connected socket client...");
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        clientPool.shutdown();
    }

    private void acceptClient(Socket clientSocket) {
        if (clientCount < maxClient) {
            clientCount++;
            System.out.println("Client accepted: " + clientSocket);
            clientPool.submit(new ClientTask(clientSocket));
        } else {
            System.out.println("Client refused: maximum " + maxClient + " clients reached.");
        }
    }


    private class ClientTask implements Runnable {
        private final Socket clientSocket;
        private DataInputStream streamIn = null;
        private BufferedReader readerIn = null;
        private DataOutputStream streamOut = null;
        private String errorMessage = null;
        private volatile boolean running = true;
        //private PrintWriter out = null;

        private ClientTask(Socket clientSocket) {
            this.clientSocket = clientSocket;

        }

        @Override
        public void run() {
            System.out.println("Connected client port: " + clientSocket.getPort());
            try {
                //streamIn = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                readerIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                streamOut = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                //out = new PrintWriter(clientSocket.getOutputStream(), false);
                while (running) {
                    try {
                        String clientMessage = readerIn.readLine();//streamIn.readUTF();
                        if (clientMessage != null) {
                            if (clientMessage.equals("fileUpload")) {
                                //Prepare file upload
                                sendMessage("OK");
                                clientMessage = readerIn.readLine();//streamIn.readUTF();
                                //File metadata
                                String fileHostingRequest = clientMessage;
                                System.out.println("New file hosting request: " + fileHostingRequest);
                                prepareFileReceive(fileHostingRequest);

                                sendMessage("prepareFileUpload");
                                //Receive file from renter
                                streamIn = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                                receiveFile(streamIn);
                                completeFileReceive();
                                sendMessage("Success");
                                terminate();
                            } else if (clientMessage.equals("fileDownload")) {
                                sendMessage("OK");
                                clientMessage = readerIn.readLine();//streamIn.readUTF();
                                String fileDownloadRequest = clientMessage;
                                System.out.println("New file download request: " + clientMessage);
                                //String preapareResult =
//                                if (!preapareResult.equals("Ready")) {
//                                    errorMessage = preapareResult;
//                                    sendMessage(errorMessage);
//                                    break;
//                                }
                                prepareRequestedFile(fileDownloadRequest);
                                sendMessage("prepareFileReceive");
                                boolean requestedFileSent = sendRequestedFile(streamOut);
                                if (requestedFileSent) {
                                    terminate();
                                } else {
                                    System.out.println("Failed to send the requested file...");
                                }
                            } else if (clientMessage.equals("fileDelete")) {
                                sendMessage("OK");
                                clientMessage = readerIn.readLine();
                                String fileDeleteRequest = clientMessage;
                                boolean fileDeleted = deleteFile(fileDeleteRequest);
                                if (fileDeleted) {
                                    System.out.println("Deleted a file from request: " + fileDeleteRequest);
                                    sendMessage("Success");
                                } else {
                                    System.out.println("Failed to delete requested file: " + fileDeleteRequest);
                                    sendMessage("Failed");
                                }
                            } else if (clientMessage.equals("doneFileUpload")) {
                                reloadWallet();
                                sendMessage("OK");
                            } else if (clientMessage.equals("getError")) {
                                sendMessage(errorMessage);
                            } else {
                                sendMessage("Unknown Request");
                                terminate();
                            }
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        terminate();
                    }
                }
                System.out.println("Client socket finished running...");
                close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        private void sendMessage(String message) {
            try {
                System.out.println("Sending message: " + message + " to: " + clientSocket);
                streamOut.writeUTF(message);
                streamOut.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            //out.println(message);
        }

        private void terminate() {
            running = false;
        }

        private void close() {
            System.out.println("Closing client connection: " + clientSocket);
            clientCount--;
            try {
                streamIn.close();
                readerIn.close();
                streamOut.close();
                //out.close();
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        }
    }
}
