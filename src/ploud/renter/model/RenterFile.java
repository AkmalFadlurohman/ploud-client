package ploud.renter.model;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import ploud.rentor.model.Rentor;

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

public class RenterFile {
    private String name;
    private String hash;
    private long size; // file size in bytes
    private String renderSize; //file render size (Bytes, kB, MB)
    private String uploadDate;
    private ArrayList<String> hostList;
    private String nameSpace = "org.ploud.network";
    private String rentorClass = nameSpace + ".Rentor";

    public RenterFile() {
        hostList = new ArrayList<>();
    }

    public RenterFile(String name, long size, String uploadDate, ArrayList<String> hostList) {
        this.name = name;
        this.size = size;
        this.uploadDate = uploadDate;
        this.hostList = new ArrayList<>();
        this.hostList.addAll(hostList);
    }

    public RenterFile(String renterFileData) {
        System.out.println("Creating new renter file model");
        try {
            JSONObject renterFile = (JSONObject) new JSONParser().parse(renterFileData);
            String name = (String) renterFile.get("name");
            long size = (long) renterFile.get("size");
            this.name = name;
            this.size = size;

            setRenderSize(size);
            String uploadDate = (String) renterFile.get("uploadDate");
            this.uploadDate = uploadDate;

            String hash = (String) renterFile.get("hash");
            this.hash = hash;

            JSONArray hostArray = (JSONArray) renterFile.get("hostList");
            Iterator iterator = hostArray.iterator();
            while (iterator.hasNext()) {
                String host = (String) iterator.next();
                String hostAddress = host.split("#")[1];
                hostList.add(hostAddress);
            }
        } catch (ParseException ex) {
            ex.printStackTrace();
        }

    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setHash(File file) throws Exception {
        String hash = calculateHash(file);
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

    public void setRenderSize(long fileLength) {
        if (fileLength < 1024) {
            this.renderSize = getFileSizeBytes(fileLength);
        } else if (fileLength < (1024*1024)) {
            this.renderSize = getFileSizeKiloBytes(fileLength);
        } else {
            this.renderSize = getFileSizeMegaBytes(fileLength);
        }
    }
    public String getRenderSize() {
        return renderSize;
    }

    public void setUploadDate(String uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getUploadDate() {
        return uploadDate;
    }

    public void setHostList(ArrayList<Rentor> hostList) {
        this.hostList = new ArrayList<>();
        for (Rentor host : hostList) {
            this.hostList.add(host.getEmail());
        }
    }

    public ArrayList<String> getHostList() {
        return hostList;
    }

    public String toJSON(String owner) {
        JSONObject renterFile = new JSONObject();
        renterFile.put("name", name);
        renterFile.put("size", size);
        renterFile.put("uploadDate", uploadDate);
        renterFile.put("owner", owner);
        renterFile.put("hash", hash);

        JSONArray hostArray = new JSONArray();
        for (String hostAddress : hostList) {
            String host = rentorClass + "#" + hostAddress;
            hostArray.add(host);
        }

        renterFile.put("hostList", hostArray);

        return renterFile.toJSONString();
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

    public static String calculateHash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        //InputStream is = Files.newInputStream(Paths.get(file.getPath()));
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead = fis.read(buffer);
        while (bytesRead != -1) {
            md.update(buffer, 0, bytesRead);
            bytesRead = fis.read(buffer);
        }
        fis.close();
        byte[] digest = md.digest();

        StringBuilder stringBuilder = new StringBuilder();
        for (int i=0;i<digest.length;i++) {
            stringBuilder.append(Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1));
        }
        String hash = stringBuilder.toString().toUpperCase();

        return hash;
    }
}
