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
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ploud.renter.model.Wallet;
import ploud.rentor.model.Rentor;
import ploud.renter.util.WalletTransaction;
import ploud.util.AlertHelper;
import ploud.renter.model.Renter;
import ploud.renter.model.RenterFile;
import ploud.renter.util.RenterSocket;
import ploud.renter.util.ComposerConnection;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class DashboardController implements Initializable {
    @FXML
    private MenuButton profileMenu;
    @FXML
    private Text walletIDText;
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
    private ExecutorService pendingTaskPool;
    private static int pendingTransferCoinCount = 0;

    private DecimalFormat balanceFormat = new DecimalFormat("#0.00000000");

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
                String walletData = composerConnection.getRenterWalletData(email);
                return walletData;
            }
        });

        renterDataLoadTask.thenAccept(new Consumer<String>() {
            @Override
            public void accept(String walletData) {
                renter.setWallet(new Wallet(walletData));

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        setProfileMenu();
                        walletIDText.setText(walletIDText.getText().substring(0, walletIDText.getText().indexOf(":")+1) + " " + renter.getWallet().getID());
                        accountBalanceText.setText("Balance: " + balanceFormat.format(renter.getWallet().getBalance()));

                        String renderSpaceUsage = renter.getRenderSpaceUsage();
                        String spaceUsageText = spaceUsageLabel.getText().substring(0, spaceUsageLabel.getText().indexOf(":")+1);
                        spaceUsageLabel.setText(spaceUsageText + " " + renderSpaceUsage);

                        renterFileTable.getItems().addAll(renter.getRenterFiles());

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
            BigDecimal depositAmount = new BigDecimal(result.get());
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);
            CompletableFuture<Integer> depositBalanceTask = depositBalance(depositAmount);
            depositBalanceTask.thenAccept(new Consumer<Integer>() {
                @Override
                public void accept(Integer depositBalanceResponse) {
                    if (depositBalanceResponse == HttpURLConnection.HTTP_OK) {
                        BigDecimal currentBalance = renter.getWallet().getBalance();
                        BigDecimal newBalance = currentBalance.add(depositAmount);
                        renter.getWallet().setBalance(newBalance);
                        accountBalanceText.setText("Balance: " + balanceFormat.format(renter.getWallet().getBalance()));
                        String message = balanceFormat.format(depositAmount) + " coin successfully added to your wallet (ID: " + renter.getWallet().getID() + ").";
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

    private CompletableFuture<Integer> depositBalance(BigDecimal amount) {
        System.out.println(amount + " will be added to wallet balance");
        String walletID = renter.getWallet().getID();
        CompletableFuture<Integer> depositBalanceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int depositBalanceResponse = composerConnection.depositCoin(walletID, amount.doubleValue());
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
            BigDecimal withdrawAmount = new BigDecimal(result.get());
            BigDecimal currentBalance = renter.getWallet().getBalance();
            if (withdrawAmount.compareTo(currentBalance) > 0) {
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
                        BigDecimal currentBalance = renter.getWallet().getBalance();
                        BigDecimal newBalance = currentBalance.subtract(withdrawAmount);
                        renter.getWallet().setBalance(newBalance);
                        accountBalanceText.setText("Balance: " + balanceFormat.format(renter.getWallet().getBalance()));
                        String message = balanceFormat.format(withdrawAmount) + " coin successfully withdrew from your wallet (ID: " + renter.getWallet().getID() + ").";
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
    private CompletableFuture<Integer> withdrawBalance(BigDecimal amount) {
        System.out.println(amount + " will be subtracted from wallet balance");
        String walletID = renter.getWallet().getID();
        CompletableFuture<Integer> withdrawBalanceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int withdrawBalanceResponse = composerConnection.withdrawCoin(walletID, amount.doubleValue());
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
            BigDecimal price = BigDecimal.valueOf((Long.valueOf(fileSize).doubleValue() * 2 * 2) / 100000000);
            if (renter.getWallet().getBalance().compareTo(price) < 0) {
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
            renterFile.setHostList(candidateHostList);

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
                for (Rentor host : hostList) {
                    String hostAddress = host.getIpAddress();
                    try {
                        renterSocket = new RenterSocket(hostAddress, port);
                        renterSocket.start();

                        Future<String> socketListenerResult = renterSocket.sendMessage("fileDelete");
                        String socketResponse = socketListenerResult.get();
                        if (socketResponse.equals("OK")) {
                            socketListenerResult = renterSocket.sendMessage(renterFile.toJSON(renter.getEmail()));
                            socketResponse = socketListenerResult.get();
                            if (!socketResponse.equals("Success")) {
                                System.out.println("Failed to delete uploaded file on ailed RentSpace for host: " + host.getEmail());
                            }
                        }
                    } catch (ExecutionException ex) {
                        ex.printStackTrace();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }

                progressIndicator.setVisible(false);
                bodyContainer.setDisable(false);
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Transaction Error", "Failed to submit RentSpace transaction to the network. Rolling back uploaded file in rentor peers...");
                return;
            }

            BigDecimal hostReward = BigDecimal.valueOf(fileSize).multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(100000000));
            System.out.println("Reward/host peer: " + balanceFormat.format(hostReward));
            CompletableFuture<ArrayList<Rentor>> transferCoinOnRentSpace = transferCoinOnRentSpace(hostList, hostReward);
            transferCoinOnRentSpace.thenAccept(new Consumer<ArrayList<Rentor>>() {
                @Override
                public void accept(ArrayList<Rentor> transferCoinFailedHostList) {
                    System.out.println("Failed TransferCoin host list count: " + transferCoinFailedHostList.size());
                    renter.getRenterFiles().add(renterFile);
                    renterFileTable.getItems().add(renterFile);

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            long newSpaceUsage = renter.getSpaceUsage()+renterFile.getSize();
                            renter.setSpaceUsage(newSpaceUsage);

                            String renderSpaceUsage = renter.getRenderSpaceUsage();
                            String spaceUsageText = spaceUsageLabel.getText().substring(0, spaceUsageLabel.getText().indexOf(":")+1);
                            spaceUsageLabel.setText(spaceUsageText + " " +  renderSpaceUsage);

                            progressIndicator.setVisible(false);
                            bodyContainer.setDisable(false);
                            AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "File Upload", renterFile.getName() + "(" + renterFile.getRenderSize()+ ") successfully stored in network.");
                            if (transferCoinFailedHostList.isEmpty()) {
                                System.out.println("Finished file upload process. No failed TransferCoin transaction. Updating wallet balance...");
                                accountBalanceText.setText("Balance: " + balanceFormat.format(renter.getWallet().getBalance()));
                            }
                            if (!transferCoinFailedHostList.isEmpty()) {
                                uploadButton.setDisable(true);
                                AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "Transfer Coin Failed", "Failed to transfer coin to one or more rentor peers. Please do not log out or close the application window.");

                                pendingTaskPool= Executors.newFixedThreadPool(5);
                                pendingTransferCoinCount = transferCoinFailedHostList.size();

                                System.out.println("Creating executor service for resubmitting failed TransferCoin...");
                                for (Rentor transferCoinFailedHost : transferCoinFailedHostList) {
                                    Future<Integer> pendingTransferCoinFuture = pendingTaskPool.submit(transferCoinOnFailed(transferCoinFailedHost, hostReward));

                                    ScheduledExecutorService pendingTransferCoinPollingService = Executors.newSingleThreadScheduledExecutor();
                                    Runnable pendingTransferCoinPollingTask = new Runnable() {
                                        @Override
                                        public void run() {
                                            if (pendingTransferCoinFuture.isDone()) {
                                                try {
                                                    int transferCoinResponse = pendingTransferCoinFuture.get();
                                                    System.out.println("Retry submit TransferCoin transaction response: " + transferCoinResponse);
                                                    if (transferCoinResponse == HttpURLConnection.HTTP_OK) {
                                                        pendingTransferCoinCount--;
                                                        System.out.println("Pending TransferCoin transaction count: " + pendingTransferCoinCount);
                                                        if (pendingTransferCoinCount == 0) {
                                                            pendingTaskPool.shutdown();
                                                            pendingTransferCoinPollingService.shutdown();
                                                            Platform.runLater(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    uploadButton.setDisable(false);
                                                                    accountBalanceText.setText("Balance: " + balanceFormat.format(renter.getWallet().getBalance()));
                                                                }
                                                            });
                                                        }
                                                    } else {
                                                        System.out.println("Failed to retry submitting TransferCoin transaction for host: " + transferCoinFailedHost.getEmail());
                                                    }
                                                } catch (ExecutionException ex) {
                                                    ex.printStackTrace();
                                                } catch (InterruptedException ex) {
                                                    ex.printStackTrace();
                                                }
                                            }
                                        }
                                    };
                                    pendingTransferCoinPollingService.scheduleAtFixedRate(pendingTransferCoinPollingTask, 0, 7, TimeUnit.SECONDS);
                                }
                            }
                        }
                    });
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
                    //Limit host to 3 peers
                    while (iterator.hasNext()) {
                        JSONObject rentorObject = (JSONObject) iterator.next();
                        String rentorData = rentorObject.toJSONString();
                        Rentor rentor = new Rentor(rentorData);

                        //Check if rentor lastOnline time is in 5 minutes range
                        if (rentor.getLastOnline().toInstant().plusSeconds((long) 5*60).isAfter(currentTime.toInstant())) {
                            candidateHostCount++;
                            candidateHostList.add(rentor);
                            if (candidateHostCount == 3) {
                                break;
                            }
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

    private CompletableFuture<ArrayList<Rentor>> uploadFile(RenterFile renterFile, File selectedFile, ArrayList<Rentor> candidateHostList) {
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
                        Future<String> socketListenerResult = renterSocket.sendMessage("fileUpload");
                        String socketResponse = socketListenerResult.get();
                        if (socketResponse.equals("OK")) {
                            //socketListenerResult = renterSocket.sendMessage("owner=" + renter.getEmail() + "&file=" + renterFile.toJSON(renter.getEmail()));
                            socketListenerResult = renterSocket.sendMessage(renterFile.toJSON(renter.getEmail()));
                            socketResponse = socketListenerResult.get();
                            if (socketResponse.equals("prepareFileUpload")) {
                                System.out.println("Uploading File: " + renterFile.getName() + " Hash: " + renterFile.getHash() + " to " + candidateHostAddress);
                                CompletableFuture<String> socketFileUploadTask = renterSocket.sendFile(selectedFile);
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
                            String rentorWalletData = composerConnection.getRentorWalletData(candidateHostEmail);
                            if (rentorWalletData != null) {
                                candidateHost.setWallet(new ploud.rentor.model.Wallet(rentorWalletData));
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

    private CompletableFuture<ArrayList<Rentor>> transferCoinOnRentSpace(ArrayList<Rentor> hostList, BigDecimal hostReward) {
        CompletableFuture<ArrayList<Rentor>> transferCoinTask = CompletableFuture.supplyAsync(new Supplier<ArrayList<Rentor>>() {
            @Override
            public ArrayList<Rentor> get() {
                ArrayList<Rentor> transferCoinFailedHostList = new ArrayList<>();
                String senderWalletID = renter.getWallet().getID();
                for (Rentor host : hostList) {
                    System.out.println("Submitting TransferCoin transaction on RentSpace for host: " + host.getEmail());
                    String receiverWalletID = host.getWallet().getID();
                    int transferCoinResponse = composerConnection.transferCoin(senderWalletID, receiverWalletID, hostReward.doubleValue());
                    if (transferCoinResponse == HttpURLConnection.HTTP_OK) {
                        //Notify host peer to reload wallet data and update UI
                        try {
                            String hostAddress = host.getIpAddress();
                            renterSocket = new RenterSocket(hostAddress, port);
                            renterSocket.start();

                            Future<String> socketListenerResult = renterSocket.sendMessage("doneFileUpload");
                            String socketResponse = socketListenerResult.get();
                            if (socketResponse.equals("OK")) {
                                BigDecimal currentBalance = renter.getWallet().getBalance();
                                BigDecimal newBalance = currentBalance.subtract(hostReward);
                                renter.getWallet().setBalance(newBalance);
                            }
                        } catch (ExecutionException ex) {
                            ex.printStackTrace();
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        transferCoinFailedHostList.add(host);
                    }
                }
                return transferCoinFailedHostList;
            }
        });
        return transferCoinTask;
    }

    private Callable<Integer> transferCoinOnFailed(Rentor host, BigDecimal hostReward) {
        Callable<Integer> transferCoinCallable = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                System.out.println("Submitting TransferCoin transaction on failed transfer for host: " + host.getEmail());
                String senderWalletID = renter.getWallet().getID();
                String receiverWalletID = host.getWallet().getID();
                int transferCoinResponse = composerConnection.transferCoin(senderWalletID, receiverWalletID, hostReward.doubleValue());
                if (transferCoinResponse == HttpURLConnection.HTTP_OK) {
                    try {
                        String hostAddress = host.getIpAddress();
                        renterSocket = new RenterSocket(hostAddress, port);
                        renterSocket.start();

                        Future<String> socketListenerResult = renterSocket.sendMessage("doneFileUpload");
                        String socketResponse = socketListenerResult.get();
                        if (socketResponse.equals("OK")) {
                            BigDecimal currentBalance = renter.getWallet().getBalance();
                            BigDecimal newBalance = currentBalance.subtract(hostReward);
                            renter.getWallet().setBalance(newBalance);
                        }
                    } catch (ExecutionException ex) {
                        ex.printStackTrace();
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
                return transferCoinResponse;
            }
        };
        return transferCoinCallable;
    }

    private void doDownload(RenterFile selectedFile, Stage primaryStage) {
        String userHome = System.getProperty("user.home");
        long freeSpace = new File(userHome).getUsableSpace();
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
                Date currentTime = new Date();
                for (String hostEmail : selectedFile.getHostList()) {
                    String rentorData = composerConnection.getRentorData(hostEmail);
                    Rentor host = new Rentor(rentorData);

                    //Check if host lastOnline time is in 5 minutes range
                    if (host.getLastOnline().toInstant().plusSeconds((long) 3*60).isAfter(currentTime.toInstant())) {
                        String hostAddress = host.getIpAddress();
                        int port = 8089;
                        renterSocket = new RenterSocket(hostAddress, port);
                        renterSocket.start();

                        try {
                            Future<String> socketListenerTask = renterSocket.sendMessage("fileDownload");
                            String socketResponse = socketListenerTask.get();
                            if (socketResponse.equals("OK")) {
                                socketListenerTask = renterSocket.sendMessage(selectedFile.toJSON(renter.getEmail()));
                                socketResponse = socketListenerTask.get();
                                if (socketResponse.equals("prepareFileReceive")) {
                                    System.out.println("Downloading File: " + selectedFile.getName() + " Hash: " + selectedFile.getHash() + " from " + hostAddress);
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
                Stage primaryStage = (Stage) profileMenu.getScene().getWindow();
                bodyContainer.setDisable(true);
                progressIndicator.setVisible(true);

                WalletTransaction walletTransaction = new WalletTransaction(composerConnection);
                CompletableFuture<Boolean> loadTransactionDataTask = walletTransaction.loadData(renter.getEmail());
                loadTransactionDataTask.thenAccept(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean transactionDataLoaded) {
                        if (transactionDataLoaded) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    progressIndicator.setVisible(false);
                                    bodyContainer.setDisable(false);
                                    walletTransaction.show();
                                }
                            });
                        } else {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    progressIndicator.setVisible(false);
                                    bodyContainer.setDisable(false);
                                    String message = "Error! Failed to load transaction history. Please try again later.";
                                    AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "WalletTransaction Error", message);
                                }
                            });
                        }
                    }
                });
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

        TableColumn<RenterFile, String> hashColumn = new TableColumn<>("Hash");
        hashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));

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
        renterFileTable.getColumns().addAll(nameColumn, sizeColumn, hashColumn, uploadDateColumn, hostListColumn, actionColumn);
    }

    public void closeSocket() {
        if (renterSocket != null) {
            renterSocket.close();
        }
    }
}
