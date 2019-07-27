package ploud.rentor.model;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

public class RentorFile {
    private String path;
    private String hash;
    private long size; // file size in bytes
    private String renderSize; //file render size (Bytes, kB, MB)
    private String hostedDate;
    private String owner;
    private ArrayList<String> peerList;
    private String nameSpace = "org.ploud.network";
    private String rentorClass = nameSpace + ".Rentor";

    public RentorFile() {
        System.out.println("Creating new empty rentor file model");
        peerList = new ArrayList<>();
    }

    public RentorFile(String hash, String owner, long size, String hostedDate, ArrayList<String> peerList) {
        this.hash = hash;
        this.owner = owner;
        String path = File.separator + owner + File.separator + hash;
        this.path = path;
        this.size = size;
        this.hostedDate = hostedDate;
        this.peerList = new ArrayList<>();
        this.peerList.addAll(peerList);
    }

    public RentorFile(String rentorFileData) {
        peerList = new ArrayList<>();
        try {

            JSONObject rentorFile = (JSONObject) new JSONParser().parse(rentorFileData);
            String hash = (String) rentorFile.get("hash");
            long size = (long) rentorFile.get("size");
            String hostedDate = (String) rentorFile.get("uploadDate");
            String owner = (String) rentorFile.get("owner");


            this.hash = hash;
            this.size = size;
            this.hostedDate = hostedDate;
            this.owner = owner;

            String path = File.separator + owner + File.separator + hash;
            this.path = path;
            setRenderSize(size);

            JSONArray peerArray = (JSONArray) rentorFile.get("hostList");
            Iterator iterator = peerArray.iterator();
            while (iterator.hasNext()) {
                String peer = (String) iterator.next();
                String peerAddress = peer.split("#")[1];
                peerList.add(peerAddress);
            }
        } catch (ParseException ex) {
            ex.printStackTrace();
        }
    }

    public RentorFile(JSONObject rentorFileJSON) {
        peerList = new ArrayList<>();
        String hash = (String) rentorFileJSON.get("hash");
        long size = (long) rentorFileJSON.get("size");
        String hostedDate = (String) rentorFileJSON.get("uploadDate");
        String owner = (String) rentorFileJSON.get("owner");


        this.hash = hash;
        this.size = size;
        this.hostedDate = hostedDate;
        this.owner = owner;

        String path = File.separator + owner + File.separator + hash;
        this.path = path;
        setRenderSize(size);

        JSONArray peerArray = (JSONArray) rentorFileJSON.get("hostList");
        Iterator iterator = peerArray.iterator();
        while (iterator.hasNext()) {
            String peer = (String) iterator.next();
            String peerAddress = peer.split("#")[1];
            peerList.add(peerAddress);
        }
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getHash() {
        return hash;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public void setRenderSize(long fileSize) {
        if (fileSize < 1000) {
            this.renderSize = getFileSizeBytes(fileSize);
        } else if (fileSize < (1000*1000)) {
            this.renderSize = getFileSizeKiloBytes(fileSize);
        } else {
            this.renderSize = getFileSizeMegaBytes(fileSize);
        }
    }
    public String getRenderSize() {
        return renderSize;
    }

    public void setHostedDate(String hostedDate) {
        this.hostedDate = hostedDate;
    }

    public String getHostedDate() {
        return hostedDate;
    }

    public void setOwner(String ownerEmail) {
        owner = ownerEmail;
    }

    public String getOwner() {
        return owner;
    }

    public  void setPeerList(ArrayList<String> peerList) {
        this.peerList.addAll(peerList);
    }

    public ArrayList<String> getPeerList() {
        return peerList;
    }

    public String toJSON() {
        JSONObject rentorFile = new JSONObject();
        rentorFile.put("owner", owner);
        rentorFile.put("hash", hash);
        rentorFile.put("size", size);
        rentorFile.put("uploadDate", hostedDate);

        JSONArray peerArray = new JSONArray();
        for (String peerAddress : peerList) {
            String peer = "resource:" + rentorClass + "#" + peerAddress;
            peerArray.add(peer);
        }

        rentorFile.put("hostList", peerArray);

        return rentorFile.toJSONString();
    }

    public JSONObject toJSONObject() {
        JSONObject rentorFile = new JSONObject();
        rentorFile.put("owner", owner);
        rentorFile.put("hash", hash);
        rentorFile.put("size", size);
        rentorFile.put("uploadDate", hostedDate);

        JSONArray peerArray = new JSONArray();
        for (String peerAddress : peerList) {
            String peer = "resource:" + rentorClass + "#" + peerAddress;
            peerArray.add(peer);
        }

        rentorFile.put("hostList", peerArray);

        return rentorFile;
    }

    private String getFileSizeBytes(long fileLength) {
        return fileLength + " byte";
    }

    private String getFileSizeKiloBytes(long fileLength) {
        return String.format("%.2f", (double) fileLength / 1000) + "  kB";
    }

    private String getFileSizeMegaBytes(long fileLength) {
        return String.format("%.2f", (double) fileLength / (1000 * 1000)) + " MB";
    }
}

