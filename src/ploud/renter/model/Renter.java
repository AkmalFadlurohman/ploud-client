package ploud.renter.model;



import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class Renter {
    private String name;
    private String email;
    private String ipAddress;
    private long spaceUsage;
    private String token;
    private Date lastLogin;
    private Date registerDate;
    private ArrayList<RenterFile> renterFiles;
    private Vault vault;

    public Renter() {
        renterFiles = new ArrayList<>();
    }

    public Renter(String name, String email, String ipAddress, long spaceUsage, String token, Date lastLogin, Date registerDate) {
        this.name = name;
        this.email = email;
        this.ipAddress = ipAddress;
        this.spaceUsage = spaceUsage;
        this.token = token;
        this.lastLogin = lastLogin;
        this.registerDate = registerDate;
        renterFiles = new ArrayList<>();
    }

    public Renter(String renterData) {
        try {
            JSONObject renter = (JSONObject) new JSONParser().parse(renterData);

            String name = (String) renter.get("name");
            String email = (String) renter.get("email");
            String ipAddress = (String) renter.get("ipAddress");
            long spaceUsage = (long) renter.get("spaceUsage");

            this.name = name;
            this.email = email;
            this.ipAddress = ipAddress;
            this.spaceUsage = spaceUsage;

            DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            Date lastLogin = simpleDateFormat.parse((String) renter.get("lastLogin"));
            Date registerDate = simpleDateFormat.parse((String) renter.get("registerDate"));

            this.lastLogin = lastLogin;
            this.registerDate = registerDate;

            renterFiles = new ArrayList<>();
            JSONArray fileList = (JSONArray) renter.get("documents");
            Iterator iterator = fileList.iterator();
            while (iterator.hasNext()) {
                JSONObject renterFileJSON = (JSONObject) iterator.next();
                RenterFile renterFile = new RenterFile(renterFileJSON);
                renterFiles.add(renterFile);
            }
            vault = null;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName(String name) {
        return name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setSpaceUsage(long spaceUsage) {
        this.spaceUsage = spaceUsage;
    }

    public long getSpaceUsage() {
        return spaceUsage;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setLastLogin(Date lastLogin) {
        this.lastLogin = lastLogin;
    }

    public Date getLastLogin() {
        return lastLogin;
    }

    public void setRegisterDate(Date registerDate) {
        this.registerDate = registerDate;
    }

    public Date getRegisterDate() {
        return registerDate;
    }

    public void setRenterFiles(ArrayList renterFiles) {
        this.renterFiles = renterFiles;
    }

    public ArrayList getRenterFiles() {
        return renterFiles;
    }

    public void setVault(Vault vault) {
        this.vault = vault;
    }

    public Vault getVault() {
        return vault;
    }

    public String toJSON() {
        String renteClass = "org.ploud.network.Renter";
        JSONObject renter = new JSONObject();

        renter.put("$class", renteClass);
        renter.put("spaceUsage", spaceUsage);
        renter.put("email", email);
        renter.put("name", name);
        renter.put("ipAddress", ipAddress);

        DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        renter.put("lastLogin", simpleDateFormat.format(lastLogin));
        renter.put("registerDate", simpleDateFormat.format(registerDate));

        JSONArray fileList = new JSONArray();
        for (RenterFile renterFile : renterFiles) {
            fileList.add(renterFile.toJSON(email));
        }
        renter.put("documents", fileList);

        return renter.toJSONString();
    }

    public String getRenderSpaceUsage() {
        if (spaceUsage < 1000) {
            return getSizeBytes(spaceUsage);
        } else if (spaceUsage < (1000*1000)) {
            return  getSizeKiloBytes(spaceUsage);
        } else if (spaceUsage < (1000*1000*1000)) {
            return getSizeMegaBytes(spaceUsage);
        } else {
            return getSizeGigaBytes(spaceUsage);
        }
    }

    private String getSizeBytes(long spaceSize) {
        return spaceSize + " byte";
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

