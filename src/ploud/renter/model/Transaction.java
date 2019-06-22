package ploud.renter.model;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Transaction {
    private String type;
    private String ID;
    private String timeStamp;
    private String participantInvoking;

    public Transaction(String transactionData) {
        try {
            JSONObject transaction = (JSONObject) new JSONParser().parse(transactionData);
            String type = (String) transaction.get("transactionType");
            String ID = (String) transaction.get("transactionId");
            String timeStamp = (String) transaction.get("transactionTimestamp");
            String participantInvoking = (String) transaction.get("participantInvoking");

            this.type = type.split("\\.")[3];
            this.ID = ID;
            this.timeStamp = timeStamp;
            this.participantInvoking = participantInvoking.split("#")[1];
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public void setType(String type) {
        this.type = type;
    }
    public String getType() {
        return type;
    }
    public void setID(String ID) {
        this.ID = ID;
    }
    public String getID() {
        return ID;
    }
    public void setTimestamp(String timeStamp) {
        this.timeStamp = timeStamp;
    }
    public String getTimeStamp() {
        return timeStamp;
    }
    public void setParticipantInvoking(String participantInvoking) {
        this.participantInvoking = participantInvoking;
    }
    public String getParticipantInvoking() {
        return participantInvoking;
    }
}
