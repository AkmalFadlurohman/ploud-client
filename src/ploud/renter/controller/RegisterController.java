package ploud.renter.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.json.simple.JSONObject;
import ploud.util.AlertHelper;


import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class RegisterController implements Initializable {
    @FXML
    private TextField nameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField passwordField;
    @FXML
    private TextField confirmPasswordField;
    @FXML
    private  TextField creditCardField;
    @FXML
    private ProgressIndicator registerIndicator;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
    }

    @FXML
    protected void doRegister(ActionEvent event) {
        Button registerButton = (Button) event.getSource();
        Stage primaryStage = (Stage) registerButton.getScene().getWindow();

        if (nameField.getText().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Name can not be empty.");
            return;
        }
        if(emailField.getText().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Email address can not be empty.");
            return;
        }
        if(passwordField.getText().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Password can not be empty.");
            return;
        }
        if (confirmPasswordField.getText().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Please confirm your password.");
            return;
        }
        if (creditCardField.getText().isEmpty()) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Credit card number can not be empty.");
            return;
        }
        if (!creditCardField.getText().matches("[0-9]+") || creditCardField.getText().length() < 16) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Credit card should be 16 digits of number.");
            return;
        }

        String name = nameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        Pattern emailPattern = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
        boolean validEmail = emailPattern.matcher(email).find();
        if (!validEmail) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Email address is not valid.");
            return;
        }

        String passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$";
        if (!password.matches(passwordPattern)) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "Password should be 8 or more characters, contain an uppercase, a lowercase, a number, and a symbol.");
            return;
        }
        if (!confirmPassword.equals(password)) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", "The passwords you entered did not match. Try again");
            confirmPasswordField.setText("");
            return;
        }

        registerIndicator.setVisible(true);
        CompletableFuture<String> registerTask = register(name, email, password);
        registerTask.thenAccept(new Consumer<String>() {
            @Override
            public void accept(String registerResponse) {
                if (registerResponse.equals("Success")) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            registerIndicator.setVisible(false);
                            loadLogin(primaryStage);
                        }
                    });
                } else {
                    nameField.setText("");
                    emailField.setText("");
                    passwordField.setText("");
                    confirmPasswordField.setText("");
                    creditCardField.setText("");

                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            AlertHelper.showAlert(Alert.AlertType.ERROR, primaryStage, "Register Error", registerResponse + ".");
                        }
                    });
                }
            }
        });
    }

    private CompletableFuture<String> register(String name, String email, String password) {
        CompletableFuture<String> registerTask = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                JSONObject registerRequestJson = new JSONObject();
                registerRequestJson.put("name", name);
                registerRequestJson.put("email", email);
                registerRequestJson.put("role", "renter");
                registerRequestJson.put("password", password);

                String registerRequest = registerRequestJson.toJSONString();
                System.out.println("Sending register request: " + registerRequest);

                //To do: Send request and get response

                String registerResponse = "Success";
                System.out.println("Register response: " + registerResponse);
                return registerResponse;
            }
        });
        return registerTask;
    }

    private void loadLogin(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/Login.fxml"));
            loader.setController(new LoginController());
            Parent root = loader.load();

            primaryStage.setScene(new Scene(root));
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
