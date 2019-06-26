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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class WalletTransaction {
    private ComposerConnection composerConnection;
    private ObservableList<Transaction> transactionList;
    private ListView<Transaction> transactionListView;
    private String message;

    public WalletTransaction(ComposerConnection composerConnection) {
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

    public CompletableFuture<Boolean> loadData(String email) {
        CompletableFuture<Boolean> loadTransactionDataTask = CompletableFuture.supplyAsync(new Supplier<String>() {
            @Override
            public String get() {
                String walletData = composerConnection.getRenterWalletData(email);
                return walletData;
            }
        }).thenApply(new Function<String, Boolean>() {
            @Override
            public Boolean apply(String walletData) {
                if (walletData != null) {
                    try {
                        JSONArray walletList = (JSONArray) new JSONParser().parse(walletData);
                        Iterator iterator = walletList.iterator();
                        JSONObject wallet = (JSONObject) iterator.next();
                        JSONArray transactionArray = (JSONArray) wallet.get("transactions");
                        iterator = transactionArray.iterator();
                        while (iterator.hasNext()) {
                            String transactionData = ((JSONObject) iterator.next()).toJSONString();
                            Transaction transaction = new Transaction(transactionData);
                            transactionList.add(transaction);
                        }
                        return (transactionArray.size() == transactionList.size());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                return false;
            }
        });
        return loadTransactionDataTask;
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
    }


    private class TransactionListCell extends ListCell<Transaction> {
        private VBox content;
        private Text transactionType;
        private Text transactionID;
        private Text transactionTimestamp;
        private Text participantInvoking;
        private Text commodity;

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
            commodity = new Text();
            Separator separator = new Separator();
            content = new VBox(transactionType, transactionID, transactionTimestamp, participantInvoking, commodity, separator);
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
                if (transaction.getType().equals("RentSpace")) {
                    commodity.setText("Space Size: " + transaction.getCommodityAmount());
                } else {
                    commodity.setText("Amount: " + transaction.getCommodityAmount());
                }
                setGraphic(content);
            } else {
                setGraphic(null);
            }
        }
    }

}
