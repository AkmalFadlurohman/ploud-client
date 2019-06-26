package ploud.renter.controller;

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
import ploud.util.AlertHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import ploud.util.Authenticator;
import ploud.renter.util.ComposerConnection;

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
        try {
            URL url = new URL("https://www.google.com");
            URLConnection connection = url.openConnection();
            connection.connect();
        } catch (Exception ex) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "No Internet Connection", "Error! Make sure you have a good or stable internet connection to continue.");
            loginButton.setDisable(false);
            loginIndicator.setVisible(false);
            return;
        }

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
                    boolean renterRegistered = composerConnection.isRenterRegistered(email);
                    if (renterRegistered) {
                        int updateOnLoginResponse = composerConnection.updateOnLogin(email);
                        if (updateOnLoginResponse != HttpURLConnection.HTTP_OK) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to update on login data. Please try again later.");
                            return;
                        }
                        loadDashboard(primaryStage, email);
                    } else {
                        String registerRenterResponse = composerConnection.registerRenter(email, firstName, lastName);
                        if (registerRenterResponse == null) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to register new renter to the Ploud Network. Please try again later.");
                            return;
                        }
                        int createVaultResponse = composerConnection.createRenterWallet(email);
                        if (createVaultResponse != HttpURLConnection.HTTP_OK) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to create new wallet for renter. Please try again later.");
                            return;
                        }
                        File businessCard = composerConnection.issueIdentity(email);
                        if (businessCard == null) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to issue identity for new renter. Please try again later.");
                            return;
                        }
                        int walletImportResponse = composerConnection.walletImportBusinessCard(email, businessCard);
                        if (walletImportResponse != HttpURLConnection.HTTP_NO_CONTENT) {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "System Error", "Failed to import business card to renter wallet. Please try again later.");
                            return;
                        }
                        boolean businessCardDeleted = businessCard.delete();
                        if (businessCardDeleted) {
                            loadDashboard(primaryStage, email);
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
        if (composerConnection != null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/RenterDashboard.fxml"));
                Parent root = loader.load();

                DashboardController controller = loader.getController();
                controller.loadRenterData(composerConnection, email);

                primaryStage.setScene(new Scene(root));
                primaryStage.setOnHidden(new EventHandler<WindowEvent>() {
                    @Override
                    public void handle(WindowEvent windowEvent) {
                        controller.closeSocket();
                    }
                });
            } catch (IOException ex) {
                AlertHelper.showAlert(Alert.AlertType.WARNING, primaryStage,"Exception", ex.getMessage());
                ex.printStackTrace();
            }
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
