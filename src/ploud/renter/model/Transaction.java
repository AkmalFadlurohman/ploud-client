package ploud.renter.model;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Transaction {
    private String type;
    private String ID;
    private String timeStamp;
    private String participantInvoking;
    private String commodityAmount;

    public Transaction(String transactionData) {
        try {
            JSONObject transaction = (JSONObject) new JSONParser().parse(transactionData);
            String type = (String) transaction.get("type");
            String ID = (String) transaction.get("transactionId");
            String timeStamp = (String) transaction.get("timestamp");
            String participantInvoking = (String) transaction.get("participantInvoking");

            this.type = type;
            this.ID = ID;
            this.timeStamp = timeStamp;
            this.participantInvoking = participantInvoking;

            if (type.equals("RentSpace") || type.equals("ReleaseSpace")) {
                long spaceSize = (long) transaction.get("spaceSize");
                if (spaceSize < 1000) {
                    this.commodityAmount =  getSizeBytes(spaceSize);
                } else if (spaceSize < (1000*1000)) {
                    this.commodityAmount =  getSizeKiloBytes(spaceSize);
                } else if (spaceSize < (1000*1000*1000)) {
                    this.commodityAmount =  getSizeMegaBytes(spaceSize);
                } else {
                    this.commodityAmount = getSizeGigaBytes(spaceSize);
                }
            } else {
                if (transaction.get("amount") instanceof Long) {
                    long amount = (long) transaction.get("amount");
                    this.commodityAmount = String.format("%.8f", Long.valueOf(amount).doubleValue());
                } else if (transaction.get("amount") instanceof Double) {
                    double amount = (double) transaction.get("amount");
                    this.commodityAmount = String.format("%.8f", amount);
                }
            }
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
    public String getCommodityAmount() {
        return commodityAmount;
    }

    private String getSizeBytes(long spaceSize) {
        return spaceSize + " B";
    }

    private String getSizeKiloBytes(long spaceSize) {
        return String.format("%.2f", (double) spaceSize / 1000) + "  kB";
    }

    private String getSizeMegaBytes(long spaceSize) {
        return String.format("%.2f", (double) spaceSize / (1000 * 1000)) + " MB";
    }

    private String getSizeGigaBytes(long spaceSize) {
        return String.format("%.2f", (double) spaceSize / (1000*1000*1000)) + " GB";
    }
}
