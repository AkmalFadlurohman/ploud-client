package ploud.renter;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ploud.renter.controller.LoginController;

public class Main extends Application {
    private final String windowTitle = "Ploud for Renter";
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/Login.fxml"));
        loader.setController(new LoginController());
        Parent root = loader.load();
        primaryStage.setTitle(windowTitle);
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Application is closing...");
        super.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
