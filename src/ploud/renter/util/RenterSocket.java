package ploud.renter.util;

import com.dosse.upnp.UPnP;
import ploud.renter.model.RenterFile;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class RenterSocket {
    private Socket socket = null;
    private DataOutputStream streamOut = null;
    private PrintWriter out = null;
    private DataInputStream streamIn = null;
    private SocketListenerTask socketListenerTask = null;
    private Thread socketListenerThread = null;
    private ExecutorService executorService = Executors.newCachedThreadPool();
    private final int port = 8088;


    public RenterSocket(String serverAddress, int serverPort) throws UnknownHostException, IOException {
        System.out.println("Establishing connection to rentor peer");
        UPnP.openPortTCP(serverPort);
        socket = new Socket(serverAddress, serverPort);
//        socket = new Socket();
//        socket.setReuseAddress(true);
//        socket.bind(new InetSocketAddress("localhost", port));
        System.out.println("Initialized renter socket: " + socket);
        //socket.connect(new InetSocketAddress(serverAddress, serverPort));
        System.out.println("Connected to rentor peer: " + serverAddress + " on port: " + serverPort);
    }

    public void start() {
        try {
            System.out.println("Opening i/o stream...");
            streamOut = new DataOutputStream(socket.getOutputStream());
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void stop() {
        if (socketListenerThread != null) {
            try {
                socketListenerThread.join();
                socketListenerThread = null;
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void close() {
        try {
            System.out.println("Closing renter socket...");
            if (out != null) {
                out.close();
            }
            if (streamOut != null) {
                streamOut.close();
            }
            if (streamIn != null) {
                streamIn.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (socketListenerTask != null) {
                socketListenerTask.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Future<String> sendMessage(String message) {
        System.out.println("Send message: " + message + " to: " + socket.getInetAddress());
//        try {
//            streamOut.writeUTF(message);
//            streamOut.flush();
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
        out.println(message);
        FutureTask<String> socketListenerTask = new FutureTask<String>(new SocketMessageCallback(socket));
        new Thread(socketListenerTask).start();
        //Future<String> socketListenerTask = executorService.submit(new SocketMessageCallback(socket));
        return socketListenerTask;
    }

    public CompletableFuture<String> sendFile(File selectedFile) {
        System.out.println("Send file: " + selectedFile.getName() + " to: " + socket.getInetAddress());
        CompletableFuture<String> fileUploadTask = CompletableFuture.supplyAsync(new Supplier<String>() {

            @Override
            public String get() {
                try {
                    FileInputStream fileInputStream = new FileInputStream(selectedFile);
                    BufferedInputStream fileInputStreamBuffer = new BufferedInputStream(fileInputStream);
                    byte[] fileBytes = new byte[(int) selectedFile.length()];
                    fileInputStreamBuffer.read(fileBytes, 0, fileBytes.length);
                    streamOut.write(fileBytes, 0, fileBytes.length);
                    streamOut.flush();
                    System.out.println("File: " + selectedFile.getName() + " sent to: " + socket.getInetAddress());

                    try {
                        System.out.println("Waiting for server response...");
                        streamIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                        //BufferedReader readerIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        String serverResponse = streamIn.readUTF();//readerIn.readLine();//
                        return serverResponse;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return null;
            }
        });
        return fileUploadTask;
    }

    public CompletableFuture<File> receiveFile(RenterFile fileToReceive) {
        System.out.println("Receiving file: " + fileToReceive.getName() + " to: " + socket.getInetAddress());
        CompletableFuture<File> fileDownloadTask = CompletableFuture.supplyAsync(new Supplier<File>() {
            @Override
            public File get() {
                System.out.println("Listening on socket for file: " + socket);
                try {
                    DataInputStream streamIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                    byte[] receivedFileBytes = new byte[(int) fileToReceive.getSize()];
                    int fileToReceiveSize = (int) fileToReceive.getSize();

                    File receivedFile = new File(fileToReceive.getName());
                    FileOutputStream fos = new FileOutputStream(receivedFile);
                    int bytesRead = 0;
                    int totalBytesRead = 0;
                    int remainingBytes = fileToReceiveSize;
                    while((bytesRead = streamIn.read(receivedFileBytes, 0, remainingBytes)) > 0) {
                        totalBytesRead += bytesRead;
                        remainingBytes -= bytesRead;
                        System.out.println("Downloaded " + totalBytesRead + " bytes. Remaining to download: " + remainingBytes);
                        fos.write(receivedFileBytes, 0, bytesRead);
                    }
                    fos.close();
                    System.out.println("File: " + fileToReceive.getName() + " download completed");
                    return receivedFile;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                return null;
            }
        });
        return fileDownloadTask;
    }

    public synchronized void handleServerMessage(String serverMessage) {
        System.out.println("Server message: " + serverMessage);
    }

    private class SocketListenerTask implements Runnable {
        private Socket socket = null;
        private DataInputStream streamIn = null;

        public SocketListenerTask(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            System.out.println("Listening on socket: " + socket);
            try {
                streamIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                String serverMessage = streamIn.readUTF();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        public void close() {
            System.out.println("Closing socket listener: " + socket);
            if (streamIn != null) {
                try {
                    streamIn.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private class SocketMessageCallback implements Callable<String> {
        private Socket socket = null;

        private SocketMessageCallback(Socket socket) {
            this.socket = socket;
        }

        @Override
        public String call() throws Exception {
            System.out.println("Listening on socket for message: " + socket);
            try {
                DataInputStream streamIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                String serverMessage = streamIn.readUTF();
                System.out.println("Server response: " + serverMessage);

                return serverMessage;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return null;
        }
    }
}
