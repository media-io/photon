package com.netflix.imflibrary.utils;

import org.phoenixframework.channels.*;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.security.SecureRandom;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.FormBody;
import okhttp3.MediaType;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WsFileLocator implements FileLocator {
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public String connect_url;
    public String token;
    private String window_id;
    private URI uri;
    private SortedMap<String, String> query;
    private Envelope envelope;

    private String randomIndentifier() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; ++i) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public WsFileLocator(String uri_string) {
        try {
            this.uri = new URI(uri_string);
        } catch(URISyntaxException e) {
            System.out.println(e);
        }

        this.query = queryMap(this.uri.getQuery());

        try {
            this.token = getAuthToken();
            this.window_id = randomIndentifier();

            this.connect_url = new URI(
                this.uri.getScheme(),
                this.uri.getAuthority(),
                this.uri.getPath(),
                String.format("userToken=%s&window_id=%s", token, this.window_id),
                null)
                .toString();
        } catch (IOException e) {
            System.out.println(e);
        } catch(URISyntaxException e) {
            System.out.println(e);
        }
    }

    public WsFileLocator(URI uri) {
        this.uri = uri;
        this.query = queryMap(this.uri.getQuery());

        try {
            this.token = getAuthToken();
            this.window_id = randomIndentifier();
            this.connect_url = new URI(
                this.uri.getScheme(),
                this.uri.getAuthority(),
                this.uri.getPath(),
                String.format("userToken=%s&window_id=%s", token, this.window_id),
                null)
                .toString();
        } catch (IOException e) {
            System.out.println(e);
        } catch(URISyntaxException e) {
            System.out.println(e);
        }
    }

    private static SortedMap<String, String> queryMap(String query) {
        SortedMap<String, String> map = new TreeMap<String, String>();
        if (query == null)
            return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx < 0)
                map.put(pair, "");
            else
                 map.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return map;
    }

    private String getAuthToken() throws IOException {
        String username = query.get("username");
        String password = query.get("password");

        final OkHttpClient client = new OkHttpClient();
        String content = String.format("{\"session\": {\"email\": \"%s\", \"password\": \"%s\"}}", username, password);
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), content);

        String url = this.uri.getScheme() + "://" + this.uri.getAuthority() + "/api/sessions";

        String http_url = url.replaceFirst("^ws:", "http:")
            .replaceFirst("^wss:", "https:");

        final Request request = new Request.Builder()
            .post(body)
            .url(http_url)
            .build();

        Response response = client.newCall(request).execute();
        String jsonData = response.body().string();

        final JsonReader jsonReader = Json.createReader(new StringReader(jsonData));
        JsonObject respObj = jsonReader.readObject();
        String token = respObj.getString("access_token");
        return token;
    }

    private void loadFileInfo() {
        if (this.envelope != null) {
            return;
        }

        try {
            Socket socket = new Socket(this.connect_url);
            socket.connect(this.token);
            ObjectMapper channel_mapper = new ObjectMapper();
            JsonNode channel_payload = channel_mapper.readTree("{}");
            Channel channel_ui_agent = new Channel("ui_agent:all", channel_payload, socket);
            channel_ui_agent.join();

            WsMessageCallback callback = new WsMessageCallback("ui_agent:all", "ls_response");
            socket.onMessage(callback);

            String agent = query.get("agent");
            String path = query.get("path");
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = String.format("{\"body\": {\"agent\":\"%s\", \"path\": \"%s\"}}", agent, path);

            JsonNode payload = mapper.readTree(jsonString);
            Envelope send_envelope = new Envelope("ui_agent:all", "file_info", payload, null, null);
            socket.push(send_envelope);
            while (!callback.received()) {
                Thread.sleep(10);
            }
            this.envelope = callback.getEnvelope();
            channel_ui_agent.leave();
            channel_ui_agent.close();
            socket.removeAllChannels();
            socket.disconnect();

            while (socket.isConnected()) {
                Thread.sleep(10);
            }
            socket.close();
        } catch (IOException e) {
            System.out.println(e);
        } catch (IllegalStateException e) {
            System.out.println(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        }
    }

    public URI toURI() {
        return this.uri;
    }

    public String getAbsolutePath() {
        return this.uri.toString();
    }

    public String getPath() {
        return this.getAbsolutePath();
    }

    public long length() {
        loadFileInfo();
        return envelope.getPayload().get("entries").get(0).get("size").asLong();
    }

    public InputStream getInputStream() throws IOException {
        System.out.println("get input stream");
        return null;
    }

    public ResourceByteRangeProvider getResourceByteRangeProvider() {
        return new WsByteRangeProvider(this);
    }

    public String getName() {
        File file = new File(query.get("path"));
        return file.getName();
    }

    public String getAgentPath() {
        return query.get("path");
    }

    public String getAgent() {
        return query.get("agent");
    }

    public boolean exists() {
        loadFileInfo();
        return envelope.getPayload().get("entries").get(0).get("exists").asBoolean();
    }

    // *
    //  * Tests whether the file denoted by this abstract pathname is a
    //  * directory.
    //  *
    //  * @return <code>true</code> if and only if the file denoted by this
    //  * abstract pathname exists <em>and</em> is a directory;
    //  * <code>false</code> otherwise
     
    @Override
    public boolean isDirectory() {
        loadFileInfo();
        return envelope.getPayload().get("entries").get(0).get("is_dir").asBoolean();
    }

    // /**
    //  * Returns the top level keys in an agent folder
    //  * @return
    //  */
    public WsFileLocator[] listFiles(FilenameFilter filenameFilter) {
        try {
            Socket socket = new Socket(this.connect_url);
            socket.connect(this.token);
            ObjectMapper channel_mapper = new ObjectMapper();
            JsonNode channel_payload = channel_mapper.readTree("{}");
            Channel channel_ui_agent = new Channel("ui_agent:all", channel_payload, socket);
            channel_ui_agent.join();

            WsMessageCallback callback = new WsMessageCallback("ui_agent:all", "ls_response");
            socket.onMessage(callback);

            String agent = query.get("agent");
            String path = query.get("path");
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = String.format("{\"body\": {\"agent\":\"%s\", \"path\": \"%s\"}}", agent, path);

            JsonNode payload = mapper.readTree(jsonString);
            Envelope send_envelope = new Envelope("ui_agent:all", "ls", payload, null, null);
            socket.push(send_envelope);
            while (!callback.received()) {
                Thread.sleep(10);
            }
            Envelope envelope = callback.getEnvelope();
            JsonNode entries = envelope.getPayload().get("entries");

            ArrayList<WsFileLocator> fileLocators = new ArrayList<WsFileLocator>();
            for (Iterator<JsonNode> iter = entries.elements(); iter.hasNext(); ) {
                JsonNode objectSummary = iter.next();
                SortedMap<String, String> file_query = this.query;
                file_query.put("path", objectSummary.get("abs_path").asText());

                String new_query = file_query.entrySet()
                     .stream()
                     .map(Object::toString)
                     .collect(Collectors.joining("&"));

                URI file_uri = new URI(this.uri.getScheme(), this.uri.getAuthority(), this.uri.getPath(), new_query, null);
                WsFileLocator fl = new WsFileLocator(file_uri.toString());

                if (filenameFilter == null || filenameFilter.accept(null, fl.getName())) {
                    fileLocators.add(fl);
                }
            }

            channel_ui_agent.leave();
            channel_ui_agent.close();
            socket.removeAllChannels();
            socket.disconnect();

            while (socket.isConnected()) {
                Thread.sleep(10);
            }
            socket.close();
            return fileLocators.toArray(new WsFileLocator[0]);
        } catch (IOException e) {
            System.out.println(e);
        } catch (IllegalStateException e) {
            System.out.println(e);
        } catch (InterruptedException e) {
            System.out.println(e);
        } catch (URISyntaxException e) {
            System.out.println(e);
        }
        return null;
    }
}
