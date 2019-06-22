package ploud.rentor.model;




import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class Rentor {
    private String name;
    private String email;
    private String ipAddress;
    private Long registeredSpace;
    private Long freeSpace;
    private Date lastOnline;
    private String token;
    private Date lastLogin;
    private Date registerDate;
    private ArrayList<RentorFile> rentorFiles;
    private Vault vault;

    public Rentor() {
        rentorFiles = new ArrayList<>();
    }

    public Rentor(String name, String email, String ipAddress, long registeredSpace, long freeSpace, String token, Date lastLogin, Date registerDate) {
        this.name = name;
        this.email = email;
        this.ipAddress = ipAddress;
        this.registeredSpace = registeredSpace;
        this.freeSpace = freeSpace;
        this.token = token;
        this.lastLogin = lastLogin;
        this.registerDate = registerDate;
        rentorFiles = new ArrayList<>();
    }

    public Rentor(String rentorData) {
        try {
            JSONObject rentor = (JSONObject) new JSONParser().parse(rentorData);

            String name = (String) rentor.get("name");
            String email = (String) rentor.get("email");
            String ipAddress = (String) rentor.get("ipAddress");
            long registeredSpace = (long) rentor.get("registeredSpace");
            long freeSpace = (long) rentor.get("freeSpace");

            this.name = name;
            this.email = email;
            this.ipAddress = ipAddress;
            this.registeredSpace = registeredSpace;
            this.freeSpace = freeSpace;

            DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            Date lastOnline = simpleDateFormat.parse((String) rentor.get("lastOnline"));
            Date lastLogin = simpleDateFormat.parse((String) rentor.get("lastLogin"));
            Date registerDate = simpleDateFormat.parse((String) rentor.get("registerDate"));

            this.lastLogin = lastLogin;
            this.registerDate = registerDate;

            rentorFiles = new ArrayList<>();
            JSONArray fileList = (JSONArray) rentor.get("documents");
            Iterator iterator = fileList.iterator();
            while (iterator.hasNext()) {
                String jsonRentorFile = (String) iterator.next();
                RentorFile rentorFile = new RentorFile(jsonRentorFile);
                rentorFiles.add(rentorFile);
            }
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

    public void setRegisteredSpace(Long registeredSpace) {
        this.registeredSpace = registeredSpace;
    }

    public Long getRegisteredSpace() {
        return registeredSpace;
    }

    public void setFreeSpace(Long freeSpace) {
        this.freeSpace = freeSpace;
    }

    public Long getFreeSpace() {
        return freeSpace;
    }

    public void setLastOnline(Date lastOnline) {
        this.lastOnline = lastOnline;
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

    public void setRentorFiles(ArrayList renterFiles) {
        this.rentorFiles = rentorFiles;
    }

    public ArrayList getRentorFiles() {
        return rentorFiles;
    }

    public void setVault(Vault vault) {
        this.vault = vault;
    }

    public Vault getVault() {
        return vault;
    }

    public String toJSON() {
        String rentorClass = "org.ploud.network.Rentor";
        JSONObject rentor = new JSONObject();

        rentor.put("$class", rentorClass);
        rentor.put("registeredSpace", registeredSpace);
        rentor.put("freeSpace", freeSpace);
        rentor.put("name", name);
        rentor.put("email", email);
        rentor.put("ipAddress", ipAddress);

        DateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        rentor.put("lastOnline", simpleDateFormat.format(lastOnline));
        rentor.put("lastLogin", simpleDateFormat.format(lastLogin));
        rentor.put("registerDate", simpleDateFormat.format(registerDate));

        JSONArray fileList = new JSONArray();
        for (RentorFile rentorFile : rentorFiles) {
            fileList.add(rentorFile.toJSON());
        }
        rentor.put("documents", fileList);

        return rentor.toJSONString();
    }

    public String getRenderSpaceOccupancy() {
        long occupiedSpace = registeredSpace - freeSpace;
        return getSizeGigaBytes(occupiedSpace);
    }

    public String getSizeGigaBytes(long spaceSize) {
        //return String.format("%.2f", (double) spaceSize / (1024*1024*1024)) + " GB";
        return String.format("%.2f", (double) spaceSize / (1000*1000*1000)) + " GB";
    }
}
