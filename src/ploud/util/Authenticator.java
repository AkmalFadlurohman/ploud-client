package ploud.util;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Authenticator implements AuthTask {
    private final String composerAuthAddress = "http://localhost:3000/auth/google";
    private final String composerExplorerAddress = "http://localhost:3000/explorer/";

    private final String ploudClientID = "57320376615-tm45hfdt0lcgfn680p6vu2i9pet99bv1.apps.googleusercontent.com";
    private final String ploudClientSecret = "WgunZH5xy6WLkdrq8fr96XKk";
    private final String ploudClientRedirectURI = "urn:ietf:wg:oauth:2.0:oob";
    private final String ploudApiScope = "https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile";
    private final String ploudAccessCodeAddress = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&&client_id=" + ploudClientID + "&scope=" + ploudApiScope + "&access_type=offline&redirect_uri=" + ploudClientRedirectURI;
    private final String accessTokenAddress = "https://www.googleapis.com/oauth2/v4/token";

    private WebView webBrowser;
    private WebEngine webEngine;
    private boolean loginAttempted = false;

    private String composerAccessToken = null;
    private String ploudAccessToken = null;
    private String ploudRefreshToken = null;

    private Stage stage;

    public Authenticator() {
        stage = new Stage();
        webBrowser = new WebView();
        webEngine = webBrowser.getEngine();
        webEngine.setJavaScriptEnabled(true);
        stage.setOnHidden(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                close();
            }
        });
    }

    public void startLogin() {
        stage.setTitle("Ploud Google Sign In");
        Scene scene = new Scene(webBrowser,600,600, Color.web("#666970"));
        stage.setScene(scene);
        webEngine.load(composerAuthAddress);
        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<Worker.State>() {
            @Override
            public void changed(ObservableValue<? extends Worker.State> observableValue, Worker.State oldState, Worker.State newState) {
                if (newState == Worker.State.SUCCEEDED) {
                    String location = webEngine.getLocation();
                    System.out.println("Current browser location: " + location);
                    if (location.equals(composerExplorerAddress)) {
                        String composerAccessTokenCookie = (String) webEngine.executeScript("getCookie('access_token')");
                        composerAccessToken = composerAccessTokenCookie.substring(composerAccessTokenCookie.indexOf("s:")+2, composerAccessTokenCookie.indexOf("."));
                        System.out.println("Composer Client Access Token: " + composerAccessToken);
                        webEngine.load(ploudAccessCodeAddress);
                    }
                    if (location.contains("approval")) {
                        String accessCodeResponse = webEngine.getTitle();
                        String accessCode = accessCodeResponse.substring(accessCodeResponse.indexOf("code=")+5, accessCodeResponse.indexOf("&scope"));
                        stage.close();

                        System.out.println("Ploud Client Access Code: " + accessCode);

                        String ploudAccessTokenData = doGetAccessToken(accessCode);

                        try {
                            JSONObject accessTokenJSON = (JSONObject) new JSONParser().parse(ploudAccessTokenData);

                            ploudAccessToken = (String) accessTokenJSON.get("access_token");
                            ploudRefreshToken = (String) accessTokenJSON.get("refresh_token");
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }

                        System.out.println("Ploud Client Access Token: " + ploudAccessToken);

                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        });
                    }
                }
            }
        });
        stage.show();
    }

    protected String getComposerAccessToken() {
        return composerAccessToken;
    }


    protected String getPloudAccessToken() {
        return ploudAccessToken;
    }

    protected String getPloudRefreshToken() {
        return ploudRefreshToken;
    }


    private String doGetAccessToken(String accessCode) {
        try {
            URL accessTokenURL = new URL(accessTokenAddress);
            String accessTokenParams = "client_id=" + ploudClientID + "&redirect_uri=" + ploudClientRedirectURI + "&client_secret=" + ploudClientSecret + "&grant_type=authorization_code&code=" + accessCode;

            System.out.println("POST Access Token Address: " + accessTokenAddress);
            System.out.println("POST Access Token Param: " + accessTokenParams);

            byte[] postData = accessTokenParams.getBytes(StandardCharsets.UTF_8);
            int postDataLength = postData.length;

            HttpURLConnection httpPost = (HttpURLConnection) accessTokenURL.openConnection();
            httpPost.setRequestMethod("POST");
            httpPost.setRequestProperty("User-Agent", "Mozilla/5.0");
            httpPost.setRequestProperty("charset", "utf-8");
            httpPost.setRequestProperty("Content-Length", "" + postDataLength);
            httpPost.setRequestProperty("Connection", "keep-alive");
            httpPost.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpPost.setUseCaches(false);

            httpPost.setDoInput(true);
            httpPost.setDoOutput(true);
            httpPost.setInstanceFollowRedirects(false);

            DataOutputStream streamOut = new DataOutputStream(httpPost.getOutputStream());
            streamOut.write(postData);
            streamOut.flush();

            int responseCode = httpPost.getResponseCode();
            System.out.println("GET Access Token Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(httpPost.getInputStream()));
                String inputLine;
                StringBuilder responseBuilder = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    responseBuilder.append(inputLine);
                }
                in.close();
                httpPost.disconnect();
                String accessTokenResponse = responseBuilder.toString();

                System.out.println("GET Access Token Response: " + accessTokenResponse);

                return accessTokenResponse;
            } else {
                System.err.println("Error retrieving access token for OAuth login");
                BufferedReader in = new BufferedReader(new InputStreamReader(httpPost.getErrorStream()));
                String inputLine;
                StringBuilder responseBuilder = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    responseBuilder.append(inputLine);
                }
                in.close();
                httpPost.disconnect();
                String errorResponse = responseBuilder.toString();
                System.err.println(errorResponse);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
