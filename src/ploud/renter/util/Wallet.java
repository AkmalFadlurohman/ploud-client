package ploud.renter.util;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Border;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import ploud.renter.model.Transaction;
import ploud.util.AlertHelper;

import java.util.Iterator;

public class Wallet {
    private ComposerConnection composerConnection;
    private ObservableList<Transaction> transactionList;
    private ListView<Transaction> transactionListView;
    private String message;

    public Wallet(ComposerConnection composerConnection) {
        transactionList = FXCollections.observableArrayList();
        transactionListView = new ListView<Transaction>(transactionList);
        transactionListView.setCellFactory(new Callback<ListView<Transaction>, ListCell<Transaction>>() {
            @Override
            public ListCell<Transaction> call(ListView<Transaction> transactionListView) {
                return new TransactionListCell();
            }
        });
        this.composerConnection = composerConnection;
    }

    public void loadData() {
        String historianData = composerConnection.getHistorianTransaction();
        if (historianData == null) {
            message = "Error! Failed to load transaction history. Please try again later.";
            return;
        }
        try {
            JSONArray transactionArray = (JSONArray) new JSONParser().parse(historianData);
            Iterator iterator = transactionArray.iterator();
            while (iterator.hasNext()) {
                String transactionData = ((JSONObject) iterator.next()).toJSONString();
                System.out.println("Historian data element: " + transactionData);
                Transaction transaction = new Transaction(transactionData);
                transactionList.add(transaction);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void show() {
        Stage walletStage = new Stage();
        StackPane root = new StackPane();
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-border-color: black; -fx-border-color: #d3d3d3;");
        if (transactionList.size() == 0) {
            System.out.println("No Transaction Data");
            Text noTrasactionText = new Text();
            noTrasactionText.setText("No Transaction Data");
            noTrasactionText.setUnderline(true);
            root.getChildren().add(noTrasactionText);
        } else {
            root.getChildren().add(transactionListView);
        }
        walletStage.setScene(new Scene(root, 520,600));
        walletStage.show();
        walletStage.setOnHidden(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                transactionList.removeAll();
            }
        });
        if (message != null) {
            AlertHelper.showAlert(Alert.AlertType.ERROR, walletStage, "Wallet Error", message);
            walletStage.close();
        }
    }


    private class TransactionListCell extends ListCell<Transaction> {
        private VBox content;
        private Text transactionType;
        private Text transactionID;
        private Text transactionTimestamp;
        private Text participantInvoking;

        public TransactionListCell() {
            super();
            transactionType = new Text();
            //transactionType.setFont(new Font(16));
            transactionID = new Text();
            //transactionID.setFont(new Font(16));
            transactionTimestamp = new Text();
            //transactionTimestamp.setFont(new Font(16));
            participantInvoking = new Text();
            //participantInvoking.setFont(new Font(16));
            Separator separator = new Separator();
            content = new VBox(transactionType, transactionID, transactionTimestamp, participantInvoking, separator);
            content.setSpacing(4);
        }

        @Override
        protected void updateItem(Transaction transaction, boolean empty) {
            super.updateItem(transaction, empty);
            if (transaction != null && !empty) {
                transactionType.setText("Type: " + transaction.getType());
                transactionID.setText("ID: " + transaction.getID());
                transactionTimestamp.setText("Timestamp: " + transaction.getTimeStamp());
                participantInvoking.setText("Participant Invoking: " + transaction.getParticipantInvoking());
                setGraphic(content);
            } else {
                setGraphic(null);
            }
        }
    }

}
