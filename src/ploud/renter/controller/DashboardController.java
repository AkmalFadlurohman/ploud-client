package ploud.renter.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Pair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ploud.renter.model.Vault;
import ploud.renter.util.Wallet;
import ploud.rentor.model.Rentor;
import ploud.util.AlertHelper;
import ploud.renter.model.Renter;
import ploud.renter.model.RenterFile;
import ploud.renter.util.RenterSocket;
import ploud.renter.util.ComposerConnection;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DashboardController implements Initializable {
    @FXML
    private MenuButton profileMenu;
    @FXML
    private Text vaultIDText;
    @FXML
    private Text accountBalanceText;
    @FXML
    private Label spaceUsageLabel;
    @FXML
    private TableView renterFileTable;
    @FXML
    private VBox bodyContainer;
    @FXML
    private VBox progressIndicator;

    private Renter renter;

    private final int port = 8089;
    private RenterSocket renterSocket = null;

    private ComposerConnection composerConnection;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        //Create table view of renter files
        setRenterFileTable();
        bodyContainer.setDisable(true);
        progressIndicator.setVisible(true);
    }

    public void loadRenterData(ComposerConnection composerConnection, String email) {
        this.composerConnection = composerConnection;
        CompletableFuture<String> renterDataLoadTask = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                //Load renter data
                String renterData = composerConnection.getRenterData(email);
                System.out.println("Loaded renter data: " + renterData);
                return renterData;
            }
        }).thenApply(new Function<String, String>() {
            @Override
            public String apply(String renterData) {
                renter = new Renter((renterData));
                String vaultData = composerConnection.getVaultData(email);
                return vaultData;
            }
        });

        renterDataLoadTask.thenAccept(new Consumer<String>() {
            @Override
            public void accept(String vaultData) {
                renter.setVault(new Vault(vaultData));

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        setProfileMenu();
                        vaultIDText.setText(vaultIDText.getText().substring(0, vaultIDText.getText().indexOf(":")+1) + " " + renter.getVault().getID());
                        accountBalanceText.setText("Balance: " + String.format("%.8f",renter.getVault().getBalance()));

                        String renderSpaceUsage = renter.getRenderSpaceUsage();
                        String spaceUsageText = spaceUsageLabel.getText().substring(0, spaceUsageLabel.getText().indexOf(":")+1);
                        spaceUsageLabel.setText(spaceUsageText + " " + renderSpaceUsage);

                        progressIndicator.setVisible(false);
                        bodyContainer.setDisable(false);
                    }
                });
            }
        });
    }


    @FXML
    protected void doDepositBalance(ActionEvent event) {
        Button depositBalanceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) depositBalanceButton.getScene().getWindow();

        String dialogTitle = "Balance Deposit";

        TextInputDialog depositDialog = new TextInputDialog();
        depositDialog.setTitle(dialogTitle);
        depositDialog.setHeaderText(null);
        depositDialog.setContentText("Please enter the amount to deposit:");

        Optional<String> result = depositDialog.showAndWait();
        if (result.isPresent()) {
            if (result.get().length() > 1 && result.get().charAt(1) == '.') {
                String[] depositDecimal = result.get().split("\\.");
                if (depositDecimal[1].length() > 8) {
                    String message = "The deposit amount can not be less than 0.00000001";
                    AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, dialogTitle, message);
                    return;
                }
            }
            double depositAmount = Double.parseDouble(result.get());
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);
            CompletableFuture<Integer> depositBalanceTask = depositBalance(depositAmount);
            depositBalanceTask.thenAccept(new Consumer<Integer>() {
                @Override
                public void accept(Integer depositBalanceResponse) {
                    if (depositBalanceResponse == HttpURLConnection.HTTP_OK) {
                        double currentBalance = renter.getVault().getBalance();
                        double newBalance = currentBalance + depositAmount;
                        renter.getVault().setBalance(newBalance);
                        accountBalanceText.setText("Balance: " + String.format("%.8f", renter.getVault().getBalance()));
                        String message = String.format("%.8f", depositAmount) + " coin successfully added to your vault (ID: " + renter.getVault().getID() + ").";
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                bodyContainer.setDisable(false);
                                progressIndicator.setVisible(false);
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, dialogTitle, message);
                            }
                        });
                    } else {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                bodyContainer.setDisable(false);
                                progressIndicator.setVisible(false);
                                String message = "Your request can not be processed at the moment. Please try again later.";
                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, dialogTitle, message);
                            }
                        });
                    }
                }
            });
        }
    }

    private CompletableFuture<Integer> depositBalance(double amount) {
        System.out.println(amount + " will be added to vault balance");
        String vaultID = renter.getVault().getID();
        CompletableFuture<Integer> depositBalanceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int depositBalanceResponse = composerConnection.depositCoin(vaultID, amount);
                return depositBalanceResponse;
            }
        });

        return depositBalanceTask;
    }

    @FXML
    protected void doWithdrawBalance(ActionEvent event) {
        Button withdrawBalanceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) withdrawBalanceButton.getScene().getWindow();

        String dialogTitle = "Balance Withdrawal";

        TextInputDialog withdrawDialog = new TextInputDialog();
        withdrawDialog.setTitle(dialogTitle);
        withdrawDialog.setHeaderText(null);
        withdrawDialog.setContentText("Please enter the amount to withdraw:");

        Optional<String> result = withdrawDialog.showAndWait();
        if (result.isPresent()) {
            if (result.get().length() > 1 && result.get().charAt(1) == '.') {
                String[] depositDecimal = result.get().split("\\.");
                if (depositDecimal[1].length() > 8) {
                    String message = "The withdrawal amount can not be less than 0.00000001";
                    AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, dialogTitle, message);
                    return;
                }
            }
            double withdrawAmount = Double.parseDouble(result.get());
            double currentBalance = renter.getVault().getBalance();
            if (withdrawAmount > currentBalance) {
                String message = "Insufficient balance.";
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, dialogTitle, message);
                return;
            }
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);
            CompletableFuture<Integer> withdrawBalanceTask = withdrawBalance(withdrawAmount);
            withdrawBalanceTask.thenAccept(new Consumer<Integer>() {
                @Override
                public void accept(Integer withdrawBalanceResponse) {
                    if (withdrawBalanceResponse == HttpURLConnection.HTTP_OK) {
                        double currentBalance = renter.getVault().getBalance();
                        double newBalance = currentBalance - withdrawAmount;
                        renter.getVault().setBalance(newBalance);
                        accountBalanceText.setText("Balance: " + String.format("%.8f", renter.getVault().getBalance()));
                        String message = String.format("%.8f", withdrawAmount) + " coin successfully withdrew from your vault vault (ID: " + renter.getVault().getID() + ").";
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                bodyContainer.setDisable(false);
                                progressIndicator.setVisible(false);
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, dialogTitle, message);
                            }
                        });
                    } else {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                bodyContainer.setDisable(false);
                                progressIndicator.setVisible(false);
                                String message = "Your request can not be processed at the moment. Please try again later.";
                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, dialogTitle, message);
                            }
                        });
                    }
                }
            });
        }
    }
    private CompletableFuture<Integer> withdrawBalance(double amount) {
        System.out.println(amount + " will be subtracted from vault balance");
        String vaultID = renter.getVault().getID();
        CompletableFuture<Integer> withdrawBalanceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int withdrawBalanceResponse = composerConnection.withdrawCoin(vaultID, amount);
                return withdrawBalanceResponse;
            }
        });
        return withdrawBalanceTask;
    }


    @FXML
    protected void doUpload(ActionEvent event) throws Exception {
        Button uploadButton = (Button) event.getSource();
        Stage primaryStage = (Stage) uploadButton.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        File selectedFile = fileChooser.showOpenDialog(primaryStage);

        if (selectedFile != null && selectedFile.exists()) {
            if (!selectedFile.isFile()) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", selectedFile.getName() + " is not a file.");
                return;
            }
            if (selectedFile.length() > (100*1024*1024)) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", "File size limit. " + selectedFile.getName() + " is over 100 MB.");
                return;
            }
            if (isFileDuplicated(selectedFile)) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", selectedFile.getName() + " has already existed in your storage.");
                return;
            }
            long fileSize = selectedFile.length();
            double price = (Long.valueOf(fileSize).doubleValue() * 2 * 2) / 100000000;
            if (renter.getVault().getBalance() < price) {
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", "Insufficient balance! Please deposit the approximate price: " + String.format("%.8f", price) + " for RentSpace transaction to upload the file.");
                return;
            }
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);


            CompletableFuture<ArrayList<Rentor>> buildHostListTask = buildCandidateHostList(fileSize);
            ArrayList<Rentor> candidateHostList = buildHostListTask.get();

            if (candidateHostList == null || candidateHostList.isEmpty()) {
                progressIndicator.setVisible(false);
                bodyContainer.setDisable(false);
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", "There are currently no available rentors in the network to host the file. Please try again later.");
                return;
            }
            System.out.println("Candidate host list count: " + candidateHostList.size());

            RenterFile renterFile = new RenterFile();
            renterFile.setName(selectedFile.getName());
            renterFile.setHash(selectedFile);
            renterFile.setSize(selectedFile.length());
            renterFile.setRenderSize(selectedFile.length());

            DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            renterFile.setUploadDate(simpleDateFormat.format(new Date()));

            System.out.println("File: " + renterFile.getName() + " Hash: " + renterFile.getHash() + " will be uploaded to network.");

            CompletableFuture<ArrayList<Rentor>> fileUploadTask = uploadFile(renterFile, selectedFile, candidateHostList);
            ArrayList<Rentor> hostList = fileUploadTask.get();
            if (hostList == null || hostList.isEmpty()) {
                progressIndicator.setVisible(false);
                bodyContainer.setDisable(false);
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", "Failed to upload " + selectedFile.getName() + " to the discovered rentor peers. Please try again later.");
                return;
            }
            System.out.println("Host list count: " + hostList.size());
            CompletableFuture<Integer> submitRentSpaceTask = submitRentSpace(renterFile, hostList);
            int submitRentSpaceResponse = submitRentSpaceTask.get();
            if (submitRentSpaceResponse != HttpURLConnection.HTTP_OK) {
                //To do: Delete the already uploaded file in rentor peers

                progressIndicator.setVisible(false);
                bodyContainer.setDisable(false);
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Transaction Error", "Failed to submit RentSpace transaction to the network. Rolling back uploaded file in rentor peers...");
            }

            CompletableFuture<ArrayList<Rentor>> transferCoinOnRentSpace = transferCoinOnRentSpace(hostList, fileSize);
            transferCoinOnRentSpace.thenAccept(new Consumer<ArrayList<Rentor>>() {
                @Override
                public void accept(ArrayList<Rentor> transferCoinFailedHostList) {
                    System.out.println("Failed TransferCoin host list count: " + candidateHostList.size());
                    if (transferCoinFailedHostList.isEmpty()) {
                        renter.getRenterFiles().add(renterFile);
                        renterFileTable.getItems().add(renterFile);

                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                long newSpaceUsage = renter.getSpaceUsage()+renterFile.getSize();
                                renter.setSpaceUsage(newSpaceUsage);

                                String renderSpaceUsage = renter.getRenderSpaceUsage();
                                String spaceUsageText = spaceUsageLabel.getText().substring(0, spaceUsageLabel.getText().indexOf(":")+1);
                                spaceUsageLabel.setText(spaceUsageText + renderSpaceUsage);

                                progressIndicator.setVisible(false);
                                bodyContainer.setDisable(false);
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "File Upload", renterFile.getName() + "(" + renterFile.getRenderSize()+ ") successfully stored in network.");
                            }
                        });
                    } else {
                        //To do: Invoke transfer coin again
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                progressIndicator.setVisible(false);
                                bodyContainer.setDisable(false);
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "Transfer Coin Failed", "Failed to transfer coin to one or more rentor peers. Please do not close log out or close the application window.");
                            }
                        });
                    }
                }
            });
        }
    }

    private CompletableFuture<ArrayList<Rentor>> buildCandidateHostList(long fileSize) {
        CompletableFuture<ArrayList<Rentor>> buildCandidateHostListTask = CompletableFuture.supplyAsync(new Supplier<ArrayList<Rentor>>() {
            @Override
            public ArrayList<Rentor> get() {
                try {
                    int candidateHostCount = 0;
                    ArrayList<Rentor>  candidateHostList = new ArrayList<>();
                    Date currentTime = new Date();
                    String availableRentorData = composerConnection.getAvailableRentor(fileSize);
                    JSONArray availableRentor = (JSONArray) new JSONParser().parse(availableRentorData);

                    Iterator iterator = availableRentor.iterator();
                    while (iterator.hasNext() && candidateHostCount < 3) {
                        JSONObject rentorObject = (JSONObject) iterator.next();
                        String rentorData = rentorObject.toJSONString();
                        Rentor rentor = new Rentor(rentorData);

                        if (rentor.getLastOnline().toInstant().plusSeconds((long) 5*60).isAfter(currentTime.toInstant())) {
                            candidateHostCount++;
                            candidateHostList.add(rentor);
                        }
                    }
                    return candidateHostList;
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                return null;
            }
        });
        return buildCandidateHostListTask;
    }

    //0 = Upload failed
    //1 = Upload successful
    private CompletableFuture<ArrayList<Rentor>> uploadFile(RenterFile renterFile, File selectedFile, ArrayList<Rentor> candidateHostList) {
        ///Send file to peer
        CompletableFuture<ArrayList<Rentor>> fileUploadTask = CompletableFuture.supplyAsync(new Supplier<ArrayList<Rentor>>() {
            @Override
            public ArrayList<Rentor> get() {
                HashMap<String, CompletableFuture<String>> socketFileUploadTaskMap = new HashMap<>();
                int successCount = 0;
                int successThreshold = 1; // File upload success threshold

                for (Rentor candidateHost : candidateHostList) {
                    String candidateHostAddress = candidateHost.getIpAddress();
                    renterSocket = new RenterSocket(candidateHostAddress, port);
                    renterSocket.start();

                    try {
                        Future<String> socketListenerResult= renterSocket.sendMessage("fileUpload");
                        String socketResponse = socketListenerResult.get();
                        if (socketResponse.equals("OK")) {
                            socketListenerResult = renterSocket.sendMessage("owner=" + renter.getEmail() + "&file=" + renterFile.toJSON());
                            socketResponse = socketListenerResult.get();
                            if (socketResponse.equals("prepareFileUpload")) {
                                System.out.println("Uploading File: " + renterFile.getName() + " Hash: " + renterFile.getHash() + " to " + candidateHostAddress);
                                //Create file upload task on socket
                                CompletableFuture<String> socketFileUploadTask  = renterSocket.sendFile(selectedFile);
                                socketFileUploadTaskMap.put(candidateHost.getEmail(), socketFileUploadTask);
                            }
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    } catch (ExecutionException ex) {
                        ex.printStackTrace();
                    }
                }

                ArrayList<Rentor> hostList = new ArrayList<>();
                for (Rentor candidateHost : candidateHostList) {
                    String candidateHostEmail = candidateHost.getEmail();
                    CompletableFuture<String> socketFileUploadTask = socketFileUploadTaskMap.get(candidateHostEmail);
                    try {
                        String fileUploadResponse = socketFileUploadTask.get();
                        if (fileUploadResponse.equals("Success")) {
                            successCount++;
                            String rentorVaultData = composerConnection.getRentorVaultData(candidateHostEmail);
                            if (rentorVaultData != null) {
                                candidateHost.setVault(new ploud.rentor.model.Vault(rentorVaultData));
                                hostList.add(candidateHost);
                            }
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    } catch (ExecutionException ex) {
                        ex.printStackTrace();
                    }
                }
                System.out.println("File upload success count: " + successCount);
                return hostList;
//                if (successCount >= successThreshold) {
//                    return 1;
//                } else {
//                    return 0;
//                }
            }
        });
        return fileUploadTask;
    }

    private CompletableFuture<Integer> submitRentSpace(RenterFile renterFile, ArrayList<Rentor> hostList) {
        CompletableFuture<Integer> submitRentSpaceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                String email = renter.getEmail();
                int rentSpaceResponse = composerConnection.rentSpace(email, renterFile, hostList);
                return rentSpaceResponse;
            }
        });
        return submitRentSpaceTask;
    }

    private CompletableFuture<ArrayList<Rentor>> transferCoinOnRentSpace(ArrayList<Rentor> hostList, long fileSize) {
        CompletableFuture<ArrayList<Rentor>> transferCoinTask = CompletableFuture.supplyAsync(new Supplier<ArrayList<Rentor>>() {
            @Override
            public ArrayList<Rentor> get() {
                ArrayList<Rentor> transferCoinFailedHostList = new ArrayList<>();
                double hostCount = Integer.valueOf(hostList.size()).doubleValue();
                double hostReward = fileSize * 2 * hostCount / 100000000;
                String senderVaultID = renter.getVault().getID();
                for (Rentor host : hostList) {
                    String receiverVaultID = host.getVault().getID();
                    int transferCoinResponse = composerConnection.transferCoin(senderVaultID, receiverVaultID, hostReward);
                    if (transferCoinResponse != HttpURLConnection.HTTP_OK) {
                        transferCoinFailedHostList.add(host);
                    }
                }
                return transferCoinFailedHostList;
            }
        });
        return transferCoinTask;
    }

    private void doDownload(RenterFile selectedFile, Stage primaryStage) {
        long freeSpace = new File("/").getFreeSpace();
        if (freeSpace < selectedFile.getSize()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Download Error", "There is not enough space left in you local storage.");
            return;
        }

        System.out.println("File: " + selectedFile.getName() + " Hash: " + selectedFile.getHash() + " will be downloaded to local.");
        CompletableFuture<File> dashboardFileDownloadTask = downloadFile(selectedFile);
        dashboardFileDownloadTask.thenAccept(new Consumer<File>() {
            @Override
            public void accept(File receivedFile) {
                if (receivedFile != null) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            FileChooser fileChooser = new FileChooser();
                            fileChooser.setTitle("Save Downloaded File");
                            fileChooser.setInitialFileName(selectedFile.getName());
                            File savedFile = fileChooser.showSaveDialog(primaryStage);

                            if (savedFile != null && receivedFile != null) {
                                try {
                                    Files.copy(receivedFile.toPath(), savedFile.toPath());
                                    AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "File Download", selectedFile.getName() + "(" + selectedFile.getRenderSize()+ ") successfully downloaded to local storage.");
                                } catch (IOException ex) {
                                    ex.printStackTrace();
                                }
                            } else {
                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Download Error", selectedFile.getName() + " can not be downloaded at the moment. Please try again later.");
                            }
                        }
                    });
                } else {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Download Error", selectedFile.getName() + " can not be downloaded at the moment. Please try again later.");
                        }
                    });
                }
                renterSocket.close();
            }
        });
    }


    private CompletableFuture<File> downloadFile(RenterFile selectedFile) {
        CompletableFuture<File> dashboardFileDownloadTask = CompletableFuture.supplyAsync(new Supplier<File>() {
            @Override
            public File get() {
                for (String host : selectedFile.getHostList()) {
                    String rentorAddress = host;
                    int port = 8089;
                    renterSocket = new RenterSocket(rentorAddress, port);
                    renterSocket.start();

                    try {
                        Future<String> socketListenerTask = renterSocket.sendMessage("fileDownload");
                        String socketResponse = socketListenerTask.get();
                        if (socketResponse.equals("OK")) {
                            socketListenerTask = renterSocket.sendMessage("owner=" + renter.getEmail() + "&file=" + selectedFile.toJSON());
                            socketResponse = socketListenerTask.get();
                            if (socketResponse.equals("prepareFileReceive")) {
                                System.out.println("Downloading File: " + selectedFile.getName() + " Hash: " + selectedFile.getHash() + " from " + rentorAddress);
                                //Create file receive task on socket
                                CompletableFuture<File> socketFileDownloadTask  = renterSocket.receiveFile(selectedFile);
                                File receivedFile = socketFileDownloadTask.get();
                                if (receivedFile != null) {
                                    System.out.println("File: " + selectedFile.getName() + " Hash: " + selectedFile.getHash() + " successfully downloaded");
                                    return receivedFile;
                                }
                            }
                        }
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    } catch (ExecutionException ex) {
                        ex.printStackTrace();
                    }
                }
                System.out.println("Error! No file received");
                return null;
            }
        });
        return dashboardFileDownloadTask;
    }

    //TO DO
    private int deleteFile(RenterFile renterFile) {
        System.out.println("File: " + renterFile.getName() + " Hash: " + renterFile.getHash() + " deleted from the network");
        return 1;
    }

    private boolean isFileDuplicated(File file) throws Exception{
        for (int i=0;i<renterFileTable.getItems().size();i++) {
            RenterFile renterFile = (RenterFile) renterFileTable.getItems().get(i);
            if (file.getName().equals(renterFile.getName())) {
                if (file.length() == renterFile.getSize()) {
                    String fileHash = RenterFile.calculateHash(file);
                    if (fileHash.equals(renterFile.getHash())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void setProfileMenu() {
        MenuItem emailMenu = new MenuItem(renter.getEmail());
        emailMenu.setDisable(true);
        MenuItem walletMenu = new MenuItem("Wallet");
        walletMenu.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Wallet wallet = new Wallet(composerConnection);
                wallet.loadData();
                wallet.show();
            }
        });

        MenuItem logoutMenu = new MenuItem("Log Out");
        logoutMenu.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Stage primaryStage = (Stage) profileMenu.getScene().getWindow();
                try {
                    int logoutResponse = composerConnection.logOut();
                    if (logoutResponse != HttpURLConnection.HTTP_OK) {
                        AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Log Out Error", "Unable to log out at the moment. Please try again later.");
                        return;
                    }
                    System.out.println("Logged Out Successfully");
                    renter = null;
                    closeSocket();
                    renterFileTable.getItems().removeAll();
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/Login.fxml"));
                    loader.setController(new LoginController());
                    Parent root = loader.load();
                    primaryStage.setScene(new Scene(root));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        profileMenu.getItems().addAll(emailMenu, walletMenu, logoutMenu);
    }

    private void setRenterFileTable() {
        renterFileTable.setPlaceholder(new Label("No File Stored in Network"));
        TableColumn<RenterFile, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        //nameColumn.setMinWidth(200);

        TableColumn<RenterFile, Float> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("renderSize"));

        TableColumn<RenterFile, String> uploadDateColumn = new TableColumn<>("Upload Date");
        uploadDateColumn.setCellValueFactory(new PropertyValueFactory<>("uploadDate"));

        TableColumn<RenterFile, String> hostListColumn = new TableColumn<>("Host List");
        hostListColumn.setCellValueFactory(new PropertyValueFactory<>("hostList"));

        TableColumn<RenterFile, Void> actionColumn = new TableColumn<>("Action");
        actionColumn.setPrefWidth(20);

        //Add action button
        Callback<TableColumn<RenterFile, Void>, TableCell<RenterFile, Void>> cellFactory = new Callback<TableColumn<RenterFile, Void>, TableCell<RenterFile, Void>>() {
            @Override
            public TableCell<RenterFile, Void> call(TableColumn<RenterFile, Void> renterFileVoidTableColumn) {
                final TableCell<RenterFile, Void> cell = new TableCell<RenterFile, Void>() {
                    private HBox actionButtonBox = new HBox();
                    private Button downloadButton = new Button("↓");
                    private Button deleteButton = new Button("\uD83D\uDDD1");

                    @Override
                    protected void updateItem(Void item, boolean isEmpty) {
                        super.updateItem(item, isEmpty);
                        if (isEmpty) {
                            setGraphic(null);
                        } else {
                            RenterFile selectedFile = getTableView().getItems().get(getIndex());

                            downloadButton.setTextAlignment(TextAlignment.CENTER);
                            downloadButton.setTooltip(new Tooltip("Download"));
                            downloadButton.setFont(Font.font(12));
                            downloadButton.setStyle("-fx-background-color: #f6f6f6; -fx-background-radius: 5em; -fx-border-color: #d3d3d3; -fx-border-radius: 5em; -fx-min-width: 30; -fx-max-width: 30; -fx-min-height: 30; -fx-max-height: 30;");

                            downloadButton.setOnAction(new EventHandler<ActionEvent>() {
                                @Override
                                public void handle(ActionEvent event) {
                                    Stage primaryStage = (Stage) ((Button) event.getSource()).getScene().getWindow();
                                    //AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "File Download", selectedFile.getName() + "(" + selectedFile.getRenderSize()+ ") will be downloaded to local storage");
                                    doDownload(selectedFile, primaryStage);
                                }
                            });

                            deleteButton.setTextAlignment(TextAlignment.CENTER);
                            deleteButton.setTooltip(new Tooltip("Delete"));
                            deleteButton.setFont(Font.font(12));
                            deleteButton.setStyle("-fx-background-color: #f6f6f6; -fx-background-radius: 5em; -fx-border-color: #d3d3d3; -fx-border-radius: 5em; -fx-border-radius: 5em; -fx-min-width: 30; -fx-max-width: 30; -fx-min-height: 30; -fx-max-height: 30;");

                            deleteButton.setOnAction(new EventHandler<ActionEvent>() {
                                @Override
                                public void handle(ActionEvent event) {
                                    Stage primaryStage = (Stage) ((Button) event.getSource()).getScene().getWindow();
                                    //AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "File Delete", selectedFile.getName() + "(" + selectedFile.getRenderSize()+ ") will be deleted from the network");
                                    ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "File Delete", selectedFile.getName() + "(" + selectedFile.getRenderSize()+ ") will be deleted from the network. Are you sure?");
                                    if (confirmationResponse == ButtonType.OK) {
                                        int deleteResponse = deleteFile(selectedFile);
                                        if (deleteResponse == 1) {
                                            long newSpaceUsage = renter.getSpaceUsage()-selectedFile.getSize();
                                            renter.setSpaceUsage(newSpaceUsage);

                                            String renderSpaceUsage = renter.getRenderSpaceUsage();
                                            String spaceUsageText = spaceUsageLabel.getText().substring(0, spaceUsageLabel.getText().indexOf(":")+1);
                                            spaceUsageLabel.setText(spaceUsageText + renderSpaceUsage);

                                            renterFileTable.getItems().remove(getIndex());
                                        }
                                    }
                                }
                            });

                            actionButtonBox.setSpacing(7.0);
                            actionButtonBox.setAlignment(Pos.CENTER);
                            actionButtonBox.getChildren().removeAll(downloadButton, deleteButton);
                            actionButtonBox.getChildren().addAll(downloadButton, deleteButton);
                            setGraphic(actionButtonBox);
                        }
                    }
                };
                return cell;
            }
        };
        actionColumn.setCellFactory(cellFactory);

        renterFileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        renterFileTable.getColumns().addAll(nameColumn, sizeColumn, uploadDateColumn, hostListColumn, actionColumn);
    }

    public void closeSocket() {
        if (renterSocket != null) {
            renterSocket.close();
        }
    }
}
