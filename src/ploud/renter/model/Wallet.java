package ploud.renter.model;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.math.BigDecimal;
import java.util.Iterator;

public class Wallet {
    String ID;
    String owner;
    BigDecimal balance;

    public Wallet(String walletData) {
        try {
            JSONArray walletList = (JSONArray) new JSONParser().parse(walletData);
            Iterator iterator = walletList.iterator();
            JSONObject wallet = (JSONObject) iterator.next();
            String ID = (String) wallet.get("id");
            String owner = (String) wallet.get("owner");
            if (wallet.get("balance") instanceof Long) {
                long balance = (long) wallet.get("balance");
                this.balance = BigDecimal.valueOf(balance);
            } else if (wallet.get("balance") instanceof Double) {
                double balance = (double) wallet.get("balance");
                this.balance = BigDecimal.valueOf(balance);
            }

            this.ID = ID;
            this.owner = owner;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public void setID(String ID) {
        this.ID = ID;
    }
    public String getID() {
        return ID;
    }
    public void setOwner(String owner) {
        this.owner = owner;
    }
    public String getOwner() {
        return owner;
    }
    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
    public BigDecimal getBalance() {
        return balance;
    }
}
