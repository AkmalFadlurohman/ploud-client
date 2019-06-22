package ploud.renter.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.json.simple.JSONObject;
import ploud.util.AlertHelper;
import ploud.renter.util.ComposerConnection;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class RentSpaceController {
    private String renterEmail;
    private ComposerConnection composerConnection = null;

    void setRenterEmail(String email) {
        renterEmail = email;
    }

    @FXML
    protected void rentSpaceTwo(ActionEvent event) {
        Button rentSpaceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) rentSpaceButton.getScene().getWindow();
        ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "Rent Storage Space", "Are you sure you want to rent 2 GB storage space?");
        if (confirmationResponse == ButtonType.OK) {
            CompletableFuture<String> rentSpaceTask = rentSpace(2);
            rentSpaceTask.thenAccept(new Consumer<String>() {
                @Override
                public void accept(String rentSpaceResponse) {
                    if (rentSpaceResponse.equals("Success")) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                loadDashboard(primaryStage);
                            }
                        });
                    } else {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Rent Space Error", rentSpaceResponse);
                            }
                        });
                    }
                }
            });
        }
    }

    @FXML
    protected void rentSpaceFour(ActionEvent event) {
        Button rentSpaceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) rentSpaceButton.getScene().getWindow();
        ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "Rent Storage Space", "Are you sure you want to rent 4 GB storage space?");
        if (confirmationResponse == ButtonType.OK) {
            CompletableFuture<String> rentSpaceTask = rentSpace(4);
            rentSpaceTask.thenAccept(new Consumer<String>() {
                @Override
                public void accept(String rentSpaceResponse) {
                    if (rentSpaceResponse.equals("Success")) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                loadDashboard(primaryStage);
                            }
                        });
                    } else {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Rent Space Error", rentSpaceResponse);
                            }
                        });
                    }
                }
            });
        }
    }

    @FXML
    protected void rentSpaceEight(ActionEvent event) {
        Button rentSpaceButton = (Button) event.getSource();
        Stage primaryStage = (Stage) rentSpaceButton.getScene().getWindow();
        ButtonType confirmationResponse = AlertHelper.showConfirmation(primaryStage, "Rent Storage Space", "Are you sure you want to rent 8 GB storage space?");
        if (confirmationResponse == ButtonType.OK) {
            CompletableFuture<String> rentSpaceTask = rentSpace(8);
            rentSpaceTask.thenAccept(new Consumer<String>() {
                @Override
                public void accept(String rentSpaceResponse) {
                    if (rentSpaceResponse.equals("Success")) {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                loadDashboard(primaryStage);
                            }
                        });
                    } else {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Rent Space Error", rentSpaceResponse);
                            }
                        });
                    }
                }
            });
        }
    }

    private CompletableFuture<String> rentSpace(int size) {
        System.out.println("Space to rent: " + size);
        CompletableFuture<String> rentSpaceTask = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                JSONObject rentSpaceRequestJson = new JSONObject();
                rentSpaceRequestJson.put("email", renterEmail);
                rentSpaceRequestJson.put("rentSpaceSize", size);

                String rentSpaceRequest = rentSpaceRequestJson.toJSONString();
                System.out.println("Sending rent space request: " + rentSpaceRequest);

                //To do: Send request and get response

                String rentSpaceResponse = "Success";
                System.out.println("Rent space response: " + rentSpaceResponse);
                return rentSpaceResponse;
            }
        });
        return rentSpaceTask;
    }
    private void loadDashboard(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/RenterDashboard.fxml"));
            Parent root = loader.load();

            DashboardController controller = loader.getController();
            controller.loadRenterData(composerConnection, renterEmail);

            primaryStage.setScene(new Scene(root));
            primaryStage.setOnHidden(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent windowEvent) {
                    controller.closeSocket();
                }
            });
        } catch (Exception ex) {
            AlertHelper.showAlert(Alert.AlertType.WARNING, primaryStage,"Exception", ex.getMessage());
            ex.printStackTrace();
        }
    }
}
