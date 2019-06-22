package ploud.renter.model;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.util.Iterator;

public class Vault {
    String ID;
    String owner;
    double balance;

    public Vault(String vaultData) {
        try {
            JSONArray vaultList = (JSONArray) new JSONParser().parse(vaultData);
            Iterator iterator = vaultList.iterator();
            JSONObject vault = (JSONObject) iterator.next();
            String ID = (String) vault.get("id");
            String owner = (String) vault.get("owner");
            if (vault.get("balance") instanceof Long) {
                long balance = (long) vault.get("balance");
                this.balance = Long.valueOf(balance).doubleValue();
            } else if (vault.get("balance") instanceof Double) {
                double balance = (double) vault.get("balance");
                this.balance = balance;
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
    public void setBalance(double balance) {
        this.balance = balance;
    }
    public double getBalance() {
        return balance;
    }
}
