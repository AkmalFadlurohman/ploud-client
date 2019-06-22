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
import org.json.simple.JSONObject;
import ploud.renter.model.Vault;
import ploud.renter.util.Wallet;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
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
                return Integer.valueOf(withdrawBalanceResponse);
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
            if (!isFileDuplicated(selectedFile)) {
                RenterFile renterFile = new RenterFile();
                renterFile.setName(selectedFile.getName());
                renterFile.setHash(selectedFile);
                renterFile.setSize(selectedFile.length());
                renterFile.setRenderSize(selectedFile.length());

                DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
                renterFile.setUploadDate(dateFormat.format(new Date()));

                System.out.println("File: " + renterFile.getName() + " Hash: " + renterFile.getHash() + " will be uploaded to network.");

                CompletableFuture<Integer> dashboardFileUploadTask = uploadFile(renterFile, selectedFile);
                dashboardFileUploadTask.thenAccept(new Consumer<Integer>() {
                    @Override
                    public void accept(Integer uploadResponse) {
                        System.out.println("Dashboard file upload task response: " + uploadResponse);
                        if (uploadResponse ==1) {
                            //To do: Send renter updated data to server (new renter file, new free space)

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
                                    AlertHelper.showAlert(Alert.AlertType.INFORMATION, primaryStage, "File Upload", renterFile.getName() + "(" + renterFile.getRenderSize()+ ") successfully stored in network.");
                                }
                            });
                        } else {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", renterFile.getName() + " unable to uploaded at the moment. Please try again later.");
                                }
                            });
                        }
                        renterSocket.close();
                    }
                });
            } else {
                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Upload Error", selectedFile.getName() + " has already existed in your storage.");
                return;
            }
        }
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

    //0 = Upload failed
    //1 = Upload successful
    private CompletableFuture<Integer> uploadFile(RenterFile renterFile, File selectedFile) {
        ///Send file to peer
        CompletableFuture<Integer> dashboardFileUploadTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
            @Override
            public Integer get() {
                ///To do: Get rentor peer to host the file
                ArrayList<String> hostList = new ArrayList<>();
                hostList.add("127.0.0.1");
                hostList.add("132.16.34.133");
                hostList.add("113.14.25.233");
                hostList.add("181.235.10.111");
                hostList.add("165.15.10.174");
                renterFile.setHostList(hostList);

                ArrayList<CompletableFuture<String>> socketFileUploadTaskList = new ArrayList<>();
                int successCount = 0;
                int successThreshold = 1; // File upload success threshold

                //To do: Loop for each rentor peer
                String rentorAddress = "127.0.0.1";
                renterSocket = new RenterSocket(rentorAddress, port);
                renterSocket.start();


                try {
                    Future<String> socketListenerResult= renterSocket.sendMessage("fileUpload");
                    String socketResponse = socketListenerResult.get();
                    if (socketResponse.equals("OK")) {
                        socketListenerResult = renterSocket.sendMessage("owner=" + renter.getEmail() + "&file=" + renterFile.toJSON());
                        socketResponse = socketListenerResult.get();
                        if (socketResponse.equals("prepareFileUpload")) {
                            System.out.println("Uploading File: " + renterFile.getName() + " Hash: " + renterFile.getHash() + " to " + rentorAddress);
                            //Create file upload task on socket
                            CompletableFuture<String> socketFileUploadTask  = renterSocket.sendFile(selectedFile);
                            socketFileUploadTaskList.add(socketFileUploadTask);
                        }
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } catch (ExecutionException ex) {
                    ex.printStackTrace();
                }

                for (CompletableFuture<String> socketFileUploadTask : socketFileUploadTaskList) {
                    try {
                        String fileUploadResponse = socketFileUploadTask.get();
                        if (fileUploadResponse.equals("Success")) {
                            successCount++;
                        }
                    } catch (InterruptedException ex) {
                       ex.printStackTrace();
                    } catch (ExecutionException ex) {
                        ex.printStackTrace();
                    }
                }
                System.out.println("File upload success count: " + successCount);
                if (successCount >= successThreshold) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });
        return dashboardFileUploadTask;
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
