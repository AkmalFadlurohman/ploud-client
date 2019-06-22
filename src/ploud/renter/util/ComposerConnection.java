package ploud.renter.util;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ComposerConnection {
    private String accessToken;
    private String composerAuthAPI = "http://localhost:3000/api";
    private String composerAPI = "http://localhost:3001/api";
    private String systemAuthAddress = composerAuthAPI + "/system";
    private String systemAddress = composerAPI + "/system";
    private String identitySystemAddress = systemAddress + "/identities";

    private String renterAddress = composerAPI + "/Renter";
    private String vaultAddress = composerAPI + "/Vault";
    private String renterAuthAddress = composerAuthAPI + "/Renter";
    private String depositCoinAddress = composerAuthAPI + "/DepositCoin";
    private String withdrawCoinAddress = composerAuthAPI + "/WithdrawCoin";

    private String networkName = "@ploud-network";
    private String nameSpace = "org.ploud.network";
    private String renterClass = nameSpace + ".Renter";
    private String vaultClass = nameSpace + ".Vault";
    private String depositCoinClass = nameSpace + ".DepositCoin";
    private String withdrawCoinClass = nameSpace + ".WithdrawCoin";
    private String transferCoinClass = nameSpace + ".TransferCoin";
    private String rentSpaceClass = nameSpace + ".RentSpace";

    public ComposerConnection(String accessToken) {
        this.accessToken = accessToken;
    }

    public int pingNetwork() {
        String address = systemAddress + "/ping";
        try {
            URL urlAddress = new URL(address);
            HttpURLConnection httpGet = (HttpURLConnection) urlAddress.openConnection();
            httpGet.setRequestMethod("GET");

            int responseCode = httpGet.getResponseCode();
            System.out.println("Network ping response code: " + responseCode);
            httpGet.disconnect();
            return responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public boolean isRenterRegistered(String email) {
        String param = "/" + email;
        String address = renterAddress+param;
        try {
            URL urlAddress = new URL(address);
            HttpURLConnection httpHead = (HttpURLConnection) urlAddress.openConnection();

            httpHead.setRequestMethod("HEAD");

            int responseCode = httpHead.getResponseCode();
            System.out.println("Is renter registered response code: " + responseCode);
            httpHead.disconnect();
            return (responseCode == HttpURLConnection.HTTP_OK);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }

    public String registerRenter(String email, String firstName, String lastName) {
        try {
            String name = firstName + " " + lastName;
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            String registerDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date());

            JSONObject renter = new JSONObject();
            renter.put("$class", renterClass);
            renter.put("spaceUsage", 0);
            renter.put("documents", new JSONArray());
            renter.put("email", email);
            renter.put("name", name);
            renter.put("ipAddress", ipAddress);
            renter.put("registerDate", registerDate);
            renter.put("lastLogin", registerDate);

            String body = renter.toJSONString();
            System.out.println("Sending register renter request: " + body);

            URL urlAddress = new URL(renterAddress);
            HttpURLConnection httpPost = (HttpURLConnection) urlAddress.openConnection();

            httpPost.setRequestMethod("POST");
            httpPost.setRequestProperty("Content-Type", "application/json");
            httpPost.setDoInput(true);
            httpPost.setDoOutput(true);

            DataOutputStream streamOut = new DataOutputStream(httpPost.getOutputStream());
            streamOut.writeBytes(body);
            streamOut.flush();

            int responseCode = httpPost.getResponseCode();
            System.out.println("Register renter response code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpPost.getInputStream()));
                String inputLine;
                StringBuilder stringBuilder = new StringBuilder();
                while ((inputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(inputLine);
                }
                bufferedReader.close();
                httpPost.disconnect();
                String response = stringBuilder.toString();
                System.out.println("Register renter response: " + response);
                return response;
            }
            httpPost.disconnect();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public int createRenterVault(String email){
        int vaultID = ThreadLocalRandom.current().nextInt(1000,  10000);
        String owner = "resource:" + renterClass + "#" + email;
        JSONObject vault = new JSONObject();
        vault.put("$class", vaultClass);
        vault.put("id", vaultID);
        vault.put("balance", 0.00);
        vault.put("owner", owner);

        String body = vault.toJSONString();
        System.out.println("Sending create vault request: " + body);
        try {
            URL urlAddress = new URL(vaultAddress);
            HttpURLConnection httpPost = (HttpURLConnection) urlAddress.openConnection();

            httpPost.setRequestMethod("POST");
            httpPost.setRequestProperty("Content-Type", "application/json");
            httpPost.setDoOutput(true);

            DataOutputStream streamOut = new DataOutputStream(httpPost.getOutputStream());
            streamOut.writeBytes(body);
            streamOut.flush();

            int responseCode = httpPost.getResponseCode();
            System.out.println("Create vault response code: " + responseCode);
            return responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public File issueIdentity(String email) {
        String address = identitySystemAddress + "/issue";
        JSONObject identity = new JSONObject();
        String participant = renterClass + "#" + email;
        String userID = email.split("@")[0];
        String options = "{}";
        identity.put("participant", participant);
        identity.put("userID", userID);
        identity.put("options", options);

        String body = identity.toJSONString();
        System.out.println("Sending identity issue request: " + body);

        File businessCard = null;
        try {
            URL urlAddress = new URL(address);
            HttpURLConnection httpPost = (HttpURLConnection) urlAddress.openConnection();

            httpPost.setRequestMethod("POST");
            httpPost.setRequestProperty("Content-Type", "application/json");
            httpPost.setDoInput(true);
            httpPost.setDoOutput(true);

            DataOutputStream streamOut = new DataOutputStream(httpPost.getOutputStream());
            streamOut.writeBytes(body);
            streamOut.flush();

            int responseCode = httpPost.getResponseCode();
            System.out.println("Identity Issue response code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String fileName = "";
                String disposition = httpPost.getHeaderField("content-disposition");
                if (disposition != null) {
                    int index = disposition.indexOf("filename=");
                    if (index > 0) {
                        fileName = disposition.substring(index + 9, disposition.length());

                        businessCard = new File(fileName);
                        boolean businessCardCreated = businessCard.createNewFile();
                        BufferedInputStream bufferedInputStream = new BufferedInputStream(httpPost.getInputStream());
                        FileOutputStream fos = new FileOutputStream(businessCard, false);

                        int bytesRead = -1;
                        byte[] buffer = new byte[4096];
                        while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                        fos.close();
                        bufferedInputStream.close();
                        System.out.println("Received business card file: " + businessCard);
                        return businessCard;
                    } else {
                        System.out.println("Error! No business card file name found");
                    }
                } else {
                    System.out.println("Error! No 'content-disposition' in response header");
                }
            } else {
                System.out.println("Error! Issue identity response code: " + responseCode);
            }
            httpPost.disconnect();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public int walletImportBusinessCard(String email, File businessCard) {
        String address = composerAuthAPI + "/wallet/import";
        String charset = "UTF-8";
        String userID = email.split("@")[0] + networkName;
        try {
            MultipartUtility multipartUtility = new MultipartUtility(address, charset);
            multipartUtility.addHeaderField("X-Access-Token", accessToken);
            multipartUtility.addHeaderField("User-Agent", "Mozilla/5.0");

            multipartUtility.addFilePart("card", businessCard);
            multipartUtility.addFormField("name", userID);
            int responseCode = multipartUtility.submit();
            System.out.println("Import business card to wallet response code: " + responseCode);
            return responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public int updateOnLogin(String email) {
        String param = "/" + email;
        String address = renterAddress + param;
        String renterData = getRenterData(email);
        if (renterData == null) {
            return -1;
        }
        try {
            String lastLogin = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").format(new Date());
            String ipAddress = InetAddress.getLocalHost().getHostAddress();
            JSONObject renter = (JSONObject) new JSONParser().parse(renterData);
            renter.put("lastLogin", lastLogin);
            renter.put("ipAddress", ipAddress);

            String body = renter.toJSONString();
            System.out.println("Sending update renter data onLogin request: " + body);

            URL urlAddress = new URL(address);
            HttpURLConnection httpPut = (HttpURLConnection) urlAddress.openConnection();

            httpPut.setRequestMethod("PUT");
            httpPut.setRequestProperty("Content-Type", "application/json");
            httpPut.setRequestProperty("X-Access-Token", accessToken);
            httpPut.setDoOutput(true);

            DataOutputStream streamOut = new DataOutputStream(httpPut.getOutputStream());
            streamOut.writeBytes(body);
            streamOut.flush();

            int responseCode = httpPut.getResponseCode();
            System.out.println("Update renter data onLogin response code: " + responseCode);
            return responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public String getRenterData(String email) {
        String param = "/" + email;
        String address = renterAuthAddress+param;
        System.out.println("Get renter data address: " + address);
        try {
            URL urlAddress = new URL(address);
            HttpURLConnection httpGet = (HttpURLConnection) urlAddress.openConnection();

            httpGet.setRequestMethod("GET");
            httpGet.setRequestProperty("X-Access-Token", accessToken);
            httpGet.setDoInput(true);

            int responseCode = httpGet.getResponseCode();
            System.out.println("Get renter data response code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpGet.getInputStream()));
                String inputLine;
                StringBuilder stringBuilder = new StringBuilder();
                while ((inputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(inputLine);
                }
                bufferedReader.close();
                httpGet.disconnect();
                String response = stringBuilder.toString();
                System.out.println("Get renter data response: " + response);
                return response;
            }
            httpGet.disconnect();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String getVaultData(String email) {
        try {
            String owner = "resource:" + renterClass + "#" + email;
            String param = URLEncoder.encode(owner, "UTF-8");
            String address = composerAuthAPI + "/queries/selectVaultByOwner?owner=" + param;
            System.out.println("Get vault data address: " + address);

            URL urlAddress = new URL(address);
            HttpURLConnection httpGet = (HttpURLConnection) urlAddress.openConnection();

            httpGet.setRequestMethod("GET");
            httpGet.setRequestProperty("X-Access-Token", accessToken);
            httpGet.setDoInput(true);

            int responseCode = httpGet.getResponseCode();
            System.out.println("Get vault data response code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpGet.getInputStream()));
                String inputLine;
                StringBuilder stringBuilder = new StringBuilder();
                while ((inputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(inputLine);
                }
                bufferedReader.close();
                httpGet.disconnect();
                String response = stringBuilder.toString();
                System.out.println("Get vault data response: " + response);
                return response;
            }
            httpGet.disconnect();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String getHistorianTransaction() {
        String address = systemAuthAddress + "/historian";
        System.out.println("Get historian data address: " + address);
        try {
            URL urlAddress = new URL(address);
            HttpURLConnection httpGet = (HttpURLConnection) urlAddress.openConnection();

            httpGet.setRequestMethod("GET");
            httpGet.setRequestProperty("X-Access-Token", accessToken);
            httpGet.setDoInput(true);

            int responseCode = httpGet.getResponseCode();
            System.out.println("Get historian data response code: " + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpGet.getInputStream()));
                String inputLine;
                StringBuilder stringBuilder = new StringBuilder();
                while ((inputLine = bufferedReader.readLine()) != null) {
                    stringBuilder.append(inputLine);
                }
                bufferedReader.close();
                httpGet.disconnect();
                String response = stringBuilder.toString();
                System.out.println("Get historian data response: " + response);
                return response;
            }
            httpGet.disconnect();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public int depositCoin(String vaultID, double amount) {
        String vault = vaultClass + "#" + vaultID;
        JSONObject depositCoin = new JSONObject();
        depositCoin.put("$class", depositCoinClass);
        depositCoin.put("vault", vault);
        depositCoin.put("amount", amount);

        String body = depositCoin.toJSONString();
        System.out.println("Sending DepositCoin transaction request: " + body);
        try {
            URL urlAddress = new URL(depositCoinAddress);
            HttpURLConnection httpPost = (HttpURLConnection) urlAddress.openConnection();

            httpPost.setRequestMethod("POST");
            httpPost.setRequestProperty("Content-Type", "application/json");
            httpPost.setRequestProperty("X-Access-Token", accessToken);
            httpPost.setDoOutput(true);

            DataOutputStream streamOut = new DataOutputStream(httpPost.getOutputStream());
            streamOut.writeBytes(body);
            streamOut.flush();

            int responseCode = httpPost.getResponseCode();
            System.out.println("DepositCoin transaction response code: " + responseCode);
            return responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public int withdrawCoin(String vaultID, double amount) {
        String vault = vaultClass + "#" + vaultID;
        JSONObject depositCoin = new JSONObject();
        depositCoin.put("$class", withdrawCoinClass);
        depositCoin.put("vault", vault);
        depositCoin.put("amount", amount);

        String body = depositCoin.toJSONString();
        System.out.println("Sending WithdrawCoin transaction request: " + body);
        try {
            URL urlAddress = new URL(withdrawCoinAddress);
            HttpURLConnection httpPost = (HttpURLConnection) urlAddress.openConnection();

            httpPost.setRequestMethod("POST");
            httpPost.setRequestProperty("Content-Type", "application/json");
            httpPost.setRequestProperty("X-Access-Token", accessToken);
            httpPost.setDoOutput(true);

            DataOutputStream streamOut = new DataOutputStream(httpPost.getOutputStream());
            streamOut.writeBytes(body);
            streamOut.flush();

            int responseCode = httpPost.getResponseCode();
            System.out.println("WithdrawCoin transaction response code: " + responseCode);
            return responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }

    public int logOut() {
        String logoutAddress = "http://localhost:3000/auth/logout";
        try {
            URL urlAddress = new URL(logoutAddress);
            HttpURLConnection httpGet = (HttpURLConnection) urlAddress.openConnection();

            httpGet.setRequestMethod("GET");
            httpGet.setRequestProperty("X-Access-Token", accessToken);
            httpGet.setDoInput(true);

            int responseCode = httpGet.getResponseCode();
            System.out.println("Logout response code: " + responseCode);
            BufferedReader in = new BufferedReader(new InputStreamReader(httpGet.getInputStream()));
            String inputLine;
            StringBuilder responseBuilder = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                responseBuilder.append(inputLine);
            }
            in.close();

            String logoutResponse = responseBuilder.toString();

            System.out.println("Log Out Response: " + logoutResponse);
            accessToken = null;
            httpGet.disconnect();
            return  responseCode;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return -1;
    }
}

class MultipartUtility {
    private final String boundary;
    private static final String LINE_FEED = "\r\n";
    private HttpURLConnection httpConn;
    private String charset;
    private OutputStream outputStream;
    private PrintWriter writer;

     MultipartUtility(String requestURL, String charset) throws IOException {
        this.charset = charset;
        boundary = "===" + System.currentTimeMillis() + "===";

        URL url = new URL(requestURL);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true);
        httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        outputStream = httpConn.getOutputStream();
        writer = new PrintWriter(new OutputStreamWriter(outputStream, charset), true);
    }

    void addHeaderField(String name, String value) {
        writer.append(name + ": " + value).append(LINE_FEED);
        writer.flush();
    }

    void addFormField(String name, String value) {
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"" + name + "\"").append(LINE_FEED);
        writer.append("Content-Type: text/plain; charset=" + charset).append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.append(value).append(LINE_FEED);
        writer.flush();
    }

    void addFilePart(String fieldName, File uploadFile)
            throws IOException {
        String fileName = uploadFile.getName();
        writer.append("--" + boundary).append(LINE_FEED);
        writer.append("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"").append(LINE_FEED);
        writer.append("Content-Type: " + URLConnection.guessContentTypeFromName(fileName)).append(LINE_FEED);
        writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        writer.append(LINE_FEED);
        writer.flush();

        FileInputStream inputStream = new FileInputStream(uploadFile);
        byte[] buffer = new byte[4096];
        int bytesRead = -1;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        outputStream.flush();
        inputStream.close();

        writer.append(LINE_FEED);
        writer.flush();
    }


    int submit() throws IOException {
        writer.append(LINE_FEED).flush();
        writer.append("--" + boundary + "--").append(LINE_FEED);
        writer.close();

        int responseCode = httpConn.getResponseCode();
        httpConn.disconnect();
        return responseCode;
    }
}
