package ploud.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Optional;

public class AlertHelper {

    public static void showAlert(Alert.AlertType alertType, Window owner, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.initOwner(owner);
        alert.show();
    }

    public static ButtonType showConfirmation(Window owner, String title, String message) {
        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmationAlert.setTitle(title);
        confirmationAlert.setHeaderText(null);
        confirmationAlert.setContentText(message);
        confirmationAlert.initOwner(owner);
        Optional<ButtonType> selectedOption = confirmationAlert.showAndWait();
        return selectedOption.get();

    }
}
