package ploud.rentor;


import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ploud.rentor.controller.DashboardController;
import ploud.rentor.controller.LoginController;
import ploud.rentor.util.ComposerConnection;

import java.io.File;
import java.io.FileReader;

public class Main extends Application {
    private final String windowTitle = "Ploud for Rentor";
    private ComposerConnection composerConnection = null;
    private String email;
    private boolean isApplicationAssigned = false;

    @Override
    public void init() throws Exception {
        super.init();
        String ploudHomePath = System.getProperty("user.home") + File.separator + "Ploud";
        File ploudHomeDir = new File(ploudHomePath);
        if (ploudHomeDir.exists() && ploudHomeDir.isDirectory()) {
            String confFilePath = ploudHomeDir + File.separator + "conf.json";
            File confFile = new File(confFilePath);
            if (confFile.exists() && confFile.isFile()) {
                JSONParser parser = new JSONParser();
                FileReader fileReader = new FileReader(confFilePath);
                JSONObject conf = (JSONObject) parser.parse(fileReader);
                email = (String) conf.get("email");
                String composerAccessToken = (String) conf.get("composerAccessToken");
                composerConnection = new ComposerConnection(composerAccessToken);
                composerConnection.updateOnLogin(email);
                isApplicationAssigned = true;
            }
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle(windowTitle);
        if (isApplicationAssigned) {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/RentorDashboard.fxml"));
            Parent root = loader.load();
            DashboardController controller = loader.getController();
            controller.loadRentorData(composerConnection, email);

            primaryStage.setOnHidden(new EventHandler<WindowEvent>() {
                @Override
                public void handle(WindowEvent windowEvent) {
                    controller.logOut();
                }
            });
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        } else {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/layout/Login.fxml"));
            loader.setController(new LoginController());
            Parent root = loader.load();
            primaryStage.setScene(new Scene(root));
            primaryStage.show();
        }
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
