package ploud.rentor.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.json.simple.JSONObject;
import ploud.rentor.util.ComposerConnection;
import ploud.util.AlertHelper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RegisterSpaceController implements Initializable {
    @FXML
    private GridPane bodyContainer;
    @FXML
    private VBox progressIndicator;
    @FXML
    private Button registerFiveButton;
    @FXML
    private Button registerTenButton;
    @FXML
    private Button registerFifteenButton;

    private final String ploudHomePath = System.getProperty("user.home") + File.separator + "Ploud";
    private String rentorEmail;
    private ComposerConnection composerConnection;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Examine available local storage space
        String userHome = System.getProperty("user.home");
        long freeSpace = new File(userHome).getUsableSpace();
        System.out.println("Rentor local storage available space: " + freeSpace);
        long fiveThreshold = (long) 7*1024*1024*1024;
        long tenThreshold = (long) 12*1024*1024*1024;
        long fifteenThreshold = (long) 17*1024*1024*1024;

        registerFiveButton.setDisable(true);
        registerTenButton.setDisable(true);
        registerFifteenButton.setDisable(true);

        if (freeSpace >= fifteenThreshold) {
            registerFifteenButton.setDisable(false);
        }
        if (freeSpace >= tenThreshold) {
            registerTenButton.setDisable(false);
        }
        if (freeSpace >= fiveThreshold) {
            registerFiveButton.setDisable(false);
        }
    }

    void setComposerConnection(ComposerConnection composerConnection, String rentorEmail) {
        this.composerConnection = composerConnection;
        this.rentorEmail = rentorEmail;
    }

    @FXML
    protected void registerSpaceFive(ActionEvent event) {
        Button rentSpaceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) rentSpaceButton.getScene().getWindow();
        ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "Register Storage Space", "Are you sure you want 5 GB storage space to be rented?");
        if (confirmationResponse == ButtonType.OK) {
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);
            Task<Integer> registerSpaceTask = registerSpace(5);
            registerSpaceTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent workerStateEvent) {
                    int registerSpaceResponse = registerSpaceTask.getValue();
                    if (registerSpaceResponse == HttpURLConnection.HTTP_OK) {
                        boolean homeDirCreated = createPloudHomeDirectory();
                        long blockerFileSize  = (long) 5*1000*1000*1000;
                        createBlockerFile(blockerFileSize);
                        createConfigurationFile();
                        if (homeDirCreated) {
                            bodyContainer.setDisable(false);
                            progressIndicator.setVisible(false);
                            loadDashboard(primaryStage);
                        }
                    } else {
                        bodyContainer.setDisable(false);
                        progressIndicator.setVisible(false);
                        AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Space Error", "Error! Failed to submit RegisterSpace transaction. Please try again later.");
                    }
                }
            });
            new Thread(registerSpaceTask).start();
        }
    }


    @FXML
    protected void registerSpaceTen(ActionEvent event) {
        Button rentSpaceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) rentSpaceButton.getScene().getWindow();
        ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "Register Storage Space", "Are you sure you want 10 GB storage space to be rented?");
        if (confirmationResponse == ButtonType.OK) {
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);
            Task<Integer> registerSpaceTask = registerSpace(10);
            registerSpaceTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent workerStateEvent) {
                    int registerSpaceResponse = registerSpaceTask.getValue();
                    if (registerSpaceResponse == HttpURLConnection.HTTP_OK) {
                        boolean homeDirCreated = createPloudHomeDirectory();
                        long blockerFileSize  = (long) 10*1000*1000*1000;
                        createBlockerFile(blockerFileSize);
                        createConfigurationFile();
                        if (homeDirCreated) {
                            bodyContainer.setDisable(false);
                            progressIndicator.setVisible(false);
                            loadDashboard(primaryStage);
                        }
                    } else {
                        bodyContainer.setDisable(false);
                        progressIndicator.setVisible(false);
                        AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Space Error", "Error! Failed to submit RegisterSpace transaction. Please try again later.");
                    }
                }
            });
            new Thread(registerSpaceTask).start();
        }
    }

    @FXML
    protected void registerSpaceFifteen(ActionEvent event) {
        Button rentSpaceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) rentSpaceButton.getScene().getWindow();
        ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "Register Storage Space", "Are you sure you want 15 GB storage space to be rented?");
        if (confirmationResponse == ButtonType.OK) {
            bodyContainer.setDisable(true);
            progressIndicator.setVisible(true);
            Task<Integer> registerSpaceTask = registerSpace(15);
            registerSpaceTask.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent workerStateEvent) {
                    int registerSpaceResponse = registerSpaceTask.getValue();
                    if (registerSpaceResponse == HttpURLConnection.HTTP_OK) {
                        boolean homeDirCreated = createPloudHomeDirectory();
                        long blockerFileSize  = (long) 15*1000*1000*1000;
                        createBlockerFile(blockerFileSize);
                        createConfigurationFile();
                        if (homeDirCreated) {
                            bodyContainer.setDisable(false);
                            progressIndicator.setVisible(false);
                            loadDashboard(primaryStage);
                        }
                    } else {
                        bodyContainer.setDisable(false);
                        progressIndicator.setVisible(false);
                        AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Space Error", "Error! Failed to submit RegisterSpace transaction. Please try again later.");
                    }
                }
            });
            new Thread(registerSpaceTask).start();
//            CompletableFuture<Integer> registerSpaceTask = registerSpace(15);
//            registerSpaceTask.thenAccept(new Consumer<Integer>() {
//                @Override
//                public void accept(Integer registerSpaceResponse) {
//                    if (registerSpaceResponse == HttpURLConnection.HTTP_OK) {
//                        boolean homeDirCreated = createPloudHomeDirectory();
//                        long blockerFileSize  = (long) 15*1000*1000*1000;
//                        createBlockerFile(blockerFileSize);
//                        if (homeDirCreated) {
//                            bodyContainer.setDisable(false);
//                            progressIndicator.setVisible(false);
//                            //createConfigurationFile();
//                            loadDashboard(primaryStage);
//                        }
//                    } else {
//                        Platform.runLater(new Runnable() {
//                            @Override
//                            public void run() {
//                                bodyContainer.setDisable(false);
//                                progressIndicator.setVisible(false);
//                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Space Error", "Error! Failed to submit RegisterSpace transaction. Please try again later.");
//                            }
//                        });
//                    }
//                }
//            });
        }
    }

//    private CompletableFuture<Integer> registerSpace(int size) {
//        System.out.println("Space to register: " + size);
//        long spaceSize = (long) size * 1000 * 1000 * 1000;
//        CompletableFuture<Integer> registerSpaceTask = CompletableFuture.supplyAsync(new Supplier<Integer>() {
//            @Override
//            public Integer get() {
//                int registerSpaceResponse = composerConnection.registerSpace(rentorEmail, spaceSize);
//                return registerSpaceResponse;
//            }
//        });
//        return registerSpaceTask;
//    }

    private Task<Integer> registerSpace(int size) {
        System.out.println("Space to register: " + size);
        long spaceSize = (long) size * 1000 * 1000 * 1000;
        Task<Integer> registerSPaceTask = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                int registerSpaceResponse = composerConnection.registerSpace(rentorEmail, spaceSize);
                return registerSpaceResponse;
            }
        };
        return registerSPaceTask;
    }


    private boolean createPloudHomeDirectory() {
        File ploudHomeDir = new File(ploudHomePath);
        return ploudHomeDir.mkdir();
    }

    private void createBlockerFile(long size) {
        try {
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

    private void loadDashboard(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/RentorDashboard.fxml"));
            Parent root = loader.load();

            DashboardController controller = loader.getController();
            controller.loadRentorData(composerConnection, rentorEmail);

            primaryStage.setScene(new Scene(root));
            primaryStage.setOnHidden(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent windowEvent) {
                    controller.logOut();
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void createConfigurationFile() {
        System.out.println("Creating configuration file...");
        try {
            JSONObject conf = new JSONObject();
            conf.put("email", rentorEmail);
            conf.put("composerAccessToken", composerConnection.getAccessToken());

            String confFilePath = ploudHomePath + File.separator + "conf.json";
            File confFile = new File(confFilePath);
            boolean confFileCreated = confFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(confFile);
            fileOutputStream.write(conf.toJSONString().getBytes());
            fileOutputStream.flush();
            fileOutputStream.close();
            System.out.println("Created configuration: " + conf.toJSONString());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
