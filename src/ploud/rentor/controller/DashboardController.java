package ploud.rentor.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ploud.renter.model.RenterFile;
import ploud.rentor.model.Wallet;
import ploud.rentor.util.ComposerConnection;
import ploud.rentor.util.MirrorSocketServer;
import ploud.rentor.util.WalletTransaction;
import ploud.util.AlertHelper;
import ploud.rentor.model.Rentor;
import ploud.rentor.model.RentorFile;
import ploud.rentor.util.RentorSocketServer;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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
    private Label spaceOccupancyLabel;
    @FXML
    private ProgressBar spaceOccupancyBar;
    @FXML
    private TableView rentorFileTable;
    @FXML
    private VBox bodyContainer;
    @FXML
    private VBox progressIndicator;

    private Rentor rentor;

    private String ploudHomePath = System.getProperty("user.home") + File.separator + "Ploud";

    private RentorSocketServer rentorSocketServer = null;
    private MirrorSocketServer mirrorSocketServer = null;

    private ComposerConnection composerConnection;

    private Timeline serverPollingTask;

    private DecimalFormat balanceFormat = new DecimalFormat("#0.00000000");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setRentorFileTable();
        bodyContainer.setDisable(true);
        progressIndicator.setVisible(true);
        //Create and run socket server
        mirrorSocketServer = new MirrorSocketServer() {
            int publicPort;

            @Override
            public void setPublicPort(int port) {
                publicPort = port;
            }

            @Override
            public int getPublicPort() {
                return publicPort;
            }
        };
        mirrorSocketServer.start();
        rentorSocketServer = new RentorSocketServer() {
            private RentorFile receivedFile = null;
            private File requestedFile = null;
            @Override
            public synchronized void prepareFileReceive(String renterFileData) {
                if (renterFileData != null) {
                    System.out.println("Received new file: " + renterFileData);
                    RentorFile rentorFile = new RentorFile(renterFileData);

                    String owner = rentorFile.getOwner();
                    receivedFile = rentorFile;
                    System.out.println("Received rentor file: " + receivedFile.toJSON());
                    System.out.println("Checking if owner directory exists...");
                    String ownerDirPath = ploudHomePath + File.separator + owner;

                    File ownerDir = new File(ownerDirPath);
                    if (!ownerDir.exists()) {
                        System.out.println("Owner: " + owner + " directory not found. Creating new...");
                        if (ownerDir.mkdir()) {
                            System.out.println("New owner: " + owner + " directory created");
                        }
                    }
                    System.out.println("Done preparing file receive for file: " + receivedFile.toJSON());
                }

            }

            @Override
            public synchronized void receiveFile(DataInputStream streamIn) {
                if (receivedFile != null) {
                    System.out.println("Receiving file bytes...");
                    byte[] receivedFileBytes = new byte[(int) receivedFile.getSize()];
                    int receivedFileSize = (int) receivedFile.getSize();
                    String receivedFilePath = ploudHomePath + File.separator + receivedFile.getOwner() + File.separator + receivedFile.getHash();
                    try {
                        FileOutputStream fos = new FileOutputStream(receivedFilePath);
                        int bytesRead = 0;
                        int totalBytesRead = 0;
                        int remainingBytes = receivedFileSize;
                        while((bytesRead = streamIn.read(receivedFileBytes, 0, remainingBytes)) > 0) {
                            totalBytesRead += bytesRead;
                            remainingBytes -= bytesRead;
                            System.out.println("Received " + totalBytesRead + " bytes. Remaining expected: " + remainingBytes);
                            fos.write(receivedFileBytes, 0, bytesRead);
                            if (remainingBytes == 1) {
                                remainingBytes--;
                            }
                        }
                        fos.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }

            @Override
            public synchronized void completeFileReceive() {
                if (receivedFile != null) {
                    System.out.println("Completed new file hosting request received from: " + receivedFile.getOwner());

                    long receivedFileSize = receivedFile.getSize();
                    long newFreeSpace = rentor.getFreeSpace()-receivedFileSize;
                    rentor.setFreeSpace(newFreeSpace);
                    updateBlockerFileSize();

                    rentor.getRentorFiles().add(receivedFile);
                    rentorFileTable.getItems().add(receivedFile);
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            setSpaceOccupancy();
                        }
                    });
                    receivedFile = null;
                }
            }

            @Override
            public synchronized void prepareRequestedFile(String renterFileData) {
                System.out.println("Preparing to send requested file...");

                RentorFile rentorFile = new RentorFile(renterFileData);
                System.out.println("Loading requested file: " + rentorFile.getHash());

                String owner = rentorFile.getOwner();
                String requestedFilePath = ploudHomePath + File.separator + owner + File.separator + rentorFile.getHash();
                requestedFile = new File(requestedFilePath);
                if (!requestedFile.exists()) {
                    System.out.println("Error: " + requestedFilePath + " does not exist");
                    requestedFile = null;
                    //return "File not Exist";
                    return;
                }
//                if (requestedFile.length() != rentorFile.getSize()) {
//                    System.out.println("Error: Mismatched requested file size");
//                    requestedFile = null;
//                    //return "Mismatched file";
//                    return;
//                }
            }

            @Override
            public boolean sendRequestedFile(DataOutputStream streamOut) {
                boolean fileSent = false;
                if (requestedFile != null) {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(requestedFile);
                        BufferedInputStream fileInputStreamBuffer = new BufferedInputStream(fileInputStream);
                        byte[] fileBytes = new byte[(int) requestedFile.length()];
                        fileInputStreamBuffer.read(fileBytes, 0, fileBytes.length);
                        streamOut.write(fileBytes, 0, fileBytes.length);
                        streamOut.flush();
                        System.out.println("File: " + requestedFile.getName() + " successfully sent.");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    requestedFile = null;
                    fileSent = true;
                }
                return fileSent;
            }

            @Override
            public boolean deleteFile(String renterFileData) {
                RentorFile rentorFile = new RentorFile(renterFileData);
                String owner = rentorFile.getOwner();
                String fileToDeletePath = ploudHomePath + File.separator + owner + File.separator + rentorFile.getHash();
                File fileToDelete = new File(fileToDeletePath);
                if (!fileToDelete.exists()) {
                    System.out.println("Error: " + fileToDeletePath + " does not exist");
                    return false;
                }
                boolean fileDeleted = fileToDelete.delete();
                if (fileDeleted) {
                    serverPollingTask.stop();
                    removeFile(rentorFile);
                }
                return fileDeleted;
            }

            @Override
            public synchronized void reloadWallet() {
                bodyContainer.setDisable(true);
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(new Supplier<String>() {
                    @Override
                    public String get() {
                        String walletData = composerConnection.getWalletData(rentor.getEmail());
                        System.out.println("Reloaded wallet data: " + walletData);
                        return walletData;
                    }
                }).thenAccept(new Consumer<String>() {
                    @Override
                    public void accept(String walletData) {
                        if (walletData != null) {
                            System.out.println("Reloading wallet data...");
                            rentor.setWallet(new Wallet(walletData));
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    accountBalanceText.setText("Balance: " + balanceFormat.format(rentor.getWallet().getBalance()));
                                    progressIndicator.setVisible(false);
                                    bodyContainer.setDisable(false);
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void reloadRentorData() {
                bodyContainer.setDisable(true);
                progressIndicator.setVisible(true);
                CompletableFuture.supplyAsync(new Supplier<String>() {
                    @Override
                    public String get() {
                        String rentorData = composerConnection.getRentorData(rentor.getEmail());
                        return rentorData;
                    }
                }).thenAccept(new Consumer<String>() {
                    @Override
                    public void accept(String rentorData) {
                        if (rentorData != null) {
                            System.out.println("Reloading rentor data...");
                            rentor = new Rentor(rentorData);
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    setSpaceOccupancy();
                                    progressIndicator.setVisible(false);
                                    bodyContainer.setDisable(false);
                                    serverPollingTask.play();
                                }
                            });
                        }
                    }
                });
            }
        };
        rentorSocketServer.start();
        rentorSocketServer.pingMirrorSocket();
    }

    public void loadRentorData(ComposerConnection composerConnection, String email) {
        this.composerConnection = composerConnection;
        CompletableFuture<String> rentorDataLoadTask = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                //Load rentor data
                String rentorData = composerConnection.getRentorData(email);
                System.out.println("Loaded rentor data: " + rentorData);
                return rentorData;
            }
        }).thenApply(new Function<String, String>() {
            @Override
            public String apply(String rentorData) {
                rentor = new Rentor(rentorData);
                String walletData = composerConnection.getWalletData(email);
                return walletData;
            }
        });

        rentorDataLoadTask.thenAccept(new Consumer<String>() {
            @Override
            public void accept(String walletData) {
                rentor.setWallet(new Wallet(walletData));

                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        updateBlockerFileSize();

                        setProfileMenu();
                        walletIDText.setText(walletIDText.getText().substring(0, walletIDText.getText().indexOf(":")+1) + " " + rentor.getWallet().getID());
                        accountBalanceText.setText("Balance: " + balanceFormat.format(rentor.getWallet().getBalance()));
                        setSpaceOccupancy();

                        rentorFileTable.getItems().addAll(rentor.getRentorFiles());

                        progressIndicator.setVisible(false);
                        bodyContainer.setDisable(false);
                    }
                });
            }
        });
        Runnable releaseSpaceSyncTask = new Runnable() {
            @Override
            public void run() {
                System.out.println("Running ReleaseSpace synchronization process..");
                //DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                DateFormat utcDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                utcDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                String utcLastLogin = utcDateFormat.format(rentor.getLastLogin());
                String releaseSpaceData = composerConnection.getReleaseSpaceRecords(utcLastLogin);
                if (releaseSpaceData != null && !releaseSpaceData.equals("[]")) {
                    try {
                        JSONArray releaseSpaceRecords = (JSONArray) new JSONParser().parse(releaseSpaceData);
                        Iterator iterator = releaseSpaceRecords.iterator();
                        while (iterator.hasNext()) {
                            JSONObject releaseSpace = (JSONObject) iterator.next();
                            System.out.println("ReleaseSpace transaction: " + releaseSpace.toJSONString());
                            String owner = ((String) releaseSpace.get("renter")).split("#")[1];
                            String hash = (String) releaseSpace.get("documentHash");
                            long size = (long) releaseSpace.get("documentSize");
                            String releasedFilePath = ploudHomePath + File.separator + owner + File.separator + hash;
                            System.out.println("File to be released path: " + releasedFilePath);
                            File releasedFile = new File(releasedFilePath);
                            if (releasedFile.exists() && releasedFile.isFile()) {
                                boolean releasedFileDeleted = releasedFile.delete();
                                if (releasedFileDeleted) {
                                    System.out.println("Deleted File: " + releasedFile.getName() + " Owner: " + owner + " on ReleaseSpace");
                                } else {
                                    System.out.println("Failed to delete File: " + releasedFile.getName() + " Owner: " + owner + " on ReleaseSpace");
                                }
                            } else {
                                System.out.println(releasedFilePath + " does not exist.");
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    System.out.println("No new ReleaseSpace data.");
                }
            }
        };
        rentorDataLoadTask.thenRun(releaseSpaceSyncTask);

        Runnable updateOnLoginTask = new Runnable() {
            @Override
            public void run() {
                int port = mirrorSocketServer.getPublicPort();
                mirrorSocketServer.terminate();
                mirrorSocketServer.close();
                System.out.println("Retrieved hole punching port: " + port);
                mirrorSocketServer = null;

                String ipAddress = ComposerConnection.getNetworkInetAddress().getHostAddress();
                rentor.setLastLogin(new Date());
                rentor.setIpAddress(ipAddress);
                composerConnection.updateOnLogin(rentor);
            }
        };
        rentorDataLoadTask.thenRun(updateOnLoginTask);

        serverPollingTask = new Timeline(new KeyFrame(Duration.seconds(20), new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                //System.out.println("Polling server to update lastOnline data and retrieve newest wallet data...");
                rentor.setLastOnline(new Date());
                int updateLastOnlineResponse = composerConnection.updateLastOnline(rentor);
                if (updateLastOnlineResponse != HttpsURLConnection.HTTP_OK) {
                    System.out.println("Error! Failed to update rentor lastOnline data. Response code: " + updateLastOnlineResponse);
                }

            }
        }));
        serverPollingTask.setCycleCount(Timeline.INDEFINITE);
        serverPollingTask.play();

        //new Thread(releaseSpaceSyncTask).start();
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
                        BigDecimal currentBalance = rentor.getWallet().getBalance();
                        BigDecimal newBalance = currentBalance.add(depositAmount);
                        rentor.getWallet().setBalance(newBalance);
                        accountBalanceText.setText("Balance: " + balanceFormat.format(rentor.getWallet().getBalance()));
                        String message = balanceFormat.format(depositAmount) + " coin successfully added to your wallet (ID: " + rentor.getWallet().getID() + ").";
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
        String walletID = rentor.getWallet().getID();
        CompletableFuture<Integer> depositBalanceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int depositBalanceResponse = composerConnection.depositCoin(walletID, amount.doubleValue());
                return Integer.valueOf(depositBalanceResponse);
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
            BigDecimal currentBalance = rentor.getWallet().getBalance();
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
                        BigDecimal currentBalance = rentor.getWallet().getBalance();
                        BigDecimal newBalance = currentBalance.subtract(withdrawAmount);
                        rentor.getWallet().setBalance(newBalance);
                        accountBalanceText.setText("Balance: " + balanceFormat.format(rentor.getWallet().getBalance()));
                        String message = balanceFormat.format(withdrawAmount) + " coin successfully withdrew from your wallet (ID: " + rentor.getWallet().getID() + ").";
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
        String walletID = rentor.getWallet().getID();
        CompletableFuture<Integer> withdrawBalanceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                int withdrawBalanceResponse = composerConnection.withdrawCoin(walletID, amount.doubleValue());
                return Integer.valueOf(withdrawBalanceResponse);
            }
        });
        return withdrawBalanceTask;
    }

    private void setProfileMenu() {
        MenuItem emailMenu = new MenuItem(rentor.getEmail());
        emailMenu.setDisable(true);
        MenuItem walletMenu = new MenuItem("Wallet");
        walletMenu.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                Stage primaryStage = (Stage) profileMenu.getScene().getWindow();
                bodyContainer.setDisable(true);
                progressIndicator.setVisible(true);

                WalletTransaction walletTransaction = new WalletTransaction(composerConnection);
                CompletableFuture<Boolean> loadTransactionDataTask = walletTransaction.loadData(rentor.getEmail());
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
                System.out.println("Logging out rentor...");
                logOut();
                Platform.exit();
            }
        });
        profileMenu.getItems().addAll(emailMenu, walletMenu, logoutMenu);
    }

    private void setSpaceOccupancy() {
        String renderSpaceOccupancy = rentor.getRenderSpaceOccupancy();
        String renderRegisteredSpace = rentor.getSizeGigaBytes(rentor.getRegisteredSpace());
        String renderSpaceOccupancyRatio = renderSpaceOccupancy + " occupied out of " + renderRegisteredSpace;
        spaceOccupancyLabel.setText(renderSpaceOccupancyRatio);

        double spaceOccupancy = Long.valueOf(rentor.getRegisteredSpace()-rentor.getFreeSpace()).doubleValue();
        double spaceOccupancyRatio = spaceOccupancy/ rentor.getRegisteredSpace().doubleValue();
        spaceOccupancyBar.setProgress(spaceOccupancyRatio);
    }

    private void removeFile(RentorFile deletedFile) {
        for (int i=0;i<rentor.getRentorFiles().size();i++) {
            RentorFile rentorFile = rentor.getRentorFiles().get(i);
            if (rentorFile.getHash().equals(deletedFile.getHash()) && rentorFile.getOwner().equals(deletedFile.getOwner())) {
                rentor.getRentorFiles().remove(i);
                break;
            }
        }
        for (int j=0;j<rentorFileTable.getItems().size();j++) {
            RentorFile rentorFile = (RentorFile) rentorFileTable.getItems().get(j);
            if (rentorFile.getHash().equals(deletedFile.getHash()) && rentorFile.getOwner().equals(deletedFile.getOwner())) {
                rentorFileTable.getItems().remove(j);
                break;
            }
        }
    }

    private void setRentorFileTable() {
        rentorFileTable.setPlaceholder(new Label("No File Hosted in Your Storage"));
        TableColumn<RentorFile, String> hashColumn = new TableColumn<>("Hash");
        hashColumn.setCellValueFactory(new PropertyValueFactory<>("hash"));

        TableColumn<RentorFile, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("renderSize"));

        TableColumn<RentorFile, String> hostedDateColumn = new TableColumn<>("Hosted Date");
        hostedDateColumn.setCellValueFactory(new PropertyValueFactory<>("hostedDate"));

        TableColumn<RentorFile, String> ownerColumn = new TableColumn<>("Owner");
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("owner"));
        ownerColumn.setPrefWidth(20);

        TableColumn<RentorFile, ArrayList<String>> peerListColumn = new TableColumn<>("Peer List");
        peerListColumn.setCellValueFactory(new PropertyValueFactory<>("peerList"));


        rentorFileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        rentorFileTable.getColumns().addAll(hashColumn, sizeColumn, hostedDateColumn, ownerColumn, peerListColumn);
    }

    public void closeSocket() {
        if (rentorSocketServer != null) {
            rentorSocketServer.stop();
            rentorSocketServer.close();
        }
        if (mirrorSocketServer != null) {
            mirrorSocketServer.terminate();
            mirrorSocketServer.close();
        }
    }

    public void logOut() {
        rentor = null;
        closeSocket();
        serverPollingTask.stop();
        rentorFileTable.getItems().removeAll();
    }

    private void createBlockerFile(long size) {
        try {
            String userHome = System.getProperty("user.home");
            String ploudHomePath = userHome + File.separator + "Ploud";
            String blockerFilePath = ploudHomePath + File.separator + "blocker";
            System.out.println("Creating new blocker file with the size: " + size);
            RandomAccessFile randomAccessFile = new RandomAccessFile(blockerFilePath, "rw");
            randomAccessFile.setLength(size);
            randomAccessFile.close();
            System.out.println("New blocker file successfully created");
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void updateBlockerFileSize() {
        System.out.println("Ploud home path: " + ploudHomePath);
        String blockerFilePath = ploudHomePath + File.separator + "blocker";
        System.out.println("Blocker file path: " + blockerFilePath);
        File currentBlockerFile = new File(blockerFilePath);
        if (currentBlockerFile.exists()) {
            long newBlockerFileSize = rentor.getFreeSpace();
            boolean blockerFileDeleted = currentBlockerFile.delete();
            if (blockerFileDeleted) {
                System.out.println("Current blocker file deleted");
                createBlockerFile(newBlockerFileSize);
                System.out.println("New blocker file (size: " + newBlockerFileSize + ") created");
            }
        }
    }
}

