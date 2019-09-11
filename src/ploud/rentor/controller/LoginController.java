package ploud.rentor.controller;


import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ploud.rentor.model.Rentor;
import ploud.rentor.util.ComposerConnection;
import ploud.util.AlertHelper;
import ploud.util.Authenticator;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LoginController {
    @FXML
    private TextField emailField;
    @FXML
    private TextField passwordField;
    @FXML
    private ProgressIndicator loginIndicator;

    private ComposerConnection composerConnection;
    @FXML
    protected void doLogin(ActionEvent event) {
        Button loginButton = (Button) event.getSource();
        Stage primaryStage = (Stage) loginButton.getScene().getWindow();

        loginButton.setDisable(true);
        loginIndicator.setVisible(true);

        Authenticator authenticator = new Authenticator() {
            @Override
            public void finish() {
                String composerAccessToken = getComposerAccessToken();
                String ploudAccessToken = getPloudAccessToken();
                String ploudRefreshToken = getPloudRefreshToken();
                String userInfoData = doGetUserInfo(ploudAccessToken);

                composerConnection = new ComposerConnection(composerAccessToken);
                int pingNetworkResponse = composerConnection.pingNetwork();
                if (pingNetworkResponse != HttpURLConnection.HTTP_OK) {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Network Error", "The Ploud network is currently unavailable. Please try again later.");
                    return;
                }

                try {
                    JSONObject user = (JSONObject) new JSONParser().parse(userInfoData);
                    String email = (String) user.get("email");
                    String firstName = (String) user.get("given_name");
                    String lastName = (String) user.get("family_name");
                    boolean rentorRegistered = composerConnection.isRentorRegistered(email);
                    if (rentorRegistered) {
                        String rentorData = composerConnection.getRentorData(email);
                        String ipAddress = composerConnection.getNetworkInetAddress().getHostAddress();
                        Rentor rentor = new Rentor(rentorData);
                        rentor.setLastLogin(new Date());
                        rentor.setIpAddress(ipAddress);
                        int updateOnLoginResponse = composerConnection.updateOnLogin(rentor);
                        if (updateOnLoginResponse != HttpURLConnection.HTTP_OK) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to update on login data. Please try again later.");
                            return;
                        }
                        loadDashboard(primaryStage, email);
                    } else {
                        String registerRentorResponse = composerConnection.registerRentor(email, firstName, lastName);
                        if (registerRentorResponse == null) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to register new rentor to the Ploud Network. Please try again later.");
                            return;
                        }
                        int createVaultResponse = composerConnection.createRentorWallet(email);
                        if (createVaultResponse != HttpURLConnection.HTTP_OK) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to create new wallet for rentor. Please try again later.");
                            return;
                        }
                        File businessCard = composerConnection.issueIdentity(email);
                        if (businessCard == null) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to issue identity for new rentor. Please try again later.");
                            return;
                        }
                        int walletImportResponse = composerConnection.walletImportBusinessCard(email, businessCard);
                        if (walletImportResponse != HttpURLConnection.HTTP_NO_CONTENT) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to import business card to rentor wallet. Please try again later.");
                            return;
                        }
                        boolean businessCardDeleted = businessCard.delete();
                        if (businessCardDeleted) {
                            loadRegisterSpace(primaryStage,  email);
                        }
                    }
                } catch (Exception ex) {
                    AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "An error has occured to the system. Please try again later.");
                }
                loginIndicator.setVisible(false);
            }

            @Override
            public void close() {
                loginButton.setDisable(false);
                loginIndicator.setVisible(false);
            }
        };
        authenticator.startLogin();
    }

    private void loadDashboard(Stage primaryStage, String email) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/RentorDashboard.fxml"));
            Parent root = loader.load();

            DashboardController controller = loader.getController();
            controller.loadRentorData(composerConnection, email);

            primaryStage.setScene(new Scene(root));
            primaryStage.setOnHidden(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent windowEvent) {
                    controller.logOut();
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void loadRegisterSpace(Stage primaryStage, String email) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/RegisterSpace.fxml"));
            Parent root = loader.load();

            RegisterSpaceController controller = loader.getController();
            controller.setComposerConnection(composerConnection, email);

            primaryStage.setScene(new Scene(root));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private String doGetUserInfo(String accessToken) {
        String userInfoAddress = "https://www.googleapis.com/oauth2/v2/userinfo";
        try {
            String userInfoParams = "?access_token=" + accessToken;
            URL userInfoURL = new URL(userInfoAddress+userInfoParams);

            System.out.println("GET User Info URL: " + userInfoAddress + userInfoParams);

            HttpURLConnection connection = (HttpURLConnection) userInfoURL.openConnection();

            connection.setDoInput(true);
            connection.setDoOutput(true);

            int responseCode = connection.getResponseCode();
            System.out.println("GET User Info Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder responseBuilder = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    responseBuilder.append(inputLine);
                }
                in.close();
                connection.disconnect();

                String accountInfoResponse = responseBuilder.toString();

                System.out.println("GET User Info Response: " + accountInfoResponse);

                return accountInfoResponse;
            } else {
                System.err.println("Error retrieving account info");
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                String inputLine;
                StringBuilder responseBuilder = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    responseBuilder.append(inputLine);
                }
                in.close();
                connection.disconnect();
                String errorResponse = responseBuilder.toString();
                System.err.println(errorResponse);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
