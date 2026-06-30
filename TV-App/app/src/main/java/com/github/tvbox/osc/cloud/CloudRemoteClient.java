package com.github.tvbox.osc.cloud;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.receiver.SearchReceiver;
import com.github.tvbox.osc.server.RemoteKeyHelper;
import com.github.tvbox.osc.server.RemoteSettingsHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * TV 端连接云后端，接收手机遥控指令。
 */
public class CloudRemoteClient {

    public static final String DEFAULT_SERVER = "http://10.0.2.2:3080";

    private static CloudRemoteClient instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private boolean connecting;

    public interface LoginCallback {
        void onSuccess(String username);

        void onError(String message);
    }

    public static CloudRemoteClient get() {
        if (instance == null) {
            synchronized (CloudRemoteClient.class) {
                if (instance == null) {
                    instance = new CloudRemoteClient();
                }
            }
        }
        return instance;
    }

    private OkHttpClient client() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .pingInterval(20, TimeUnit.SECONDS)
                    .build();
        }
        return httpClient;
    }

    public void autoStart() {
        String token = Hawk.get(HawkConfig.CLOUD_TOKEN, "");
        String server = Hawk.get(HawkConfig.CLOUD_SERVER_URL, "");
        if (!TextUtils.isEmpty(token) && !TextUtils.isEmpty(server)) {
            connectWebSocket(server, token);
        }
    }

    public void logout() {
        disconnect();
        Hawk.delete(HawkConfig.CLOUD_TOKEN);
        Hawk.delete(HawkConfig.CLOUD_USERNAME);
    }

    public boolean isLoggedIn() {
        return !TextUtils.isEmpty(Hawk.get(HawkConfig.CLOUD_TOKEN, ""));
    }

    public String getUsername() {
        return Hawk.get(HawkConfig.CLOUD_USERNAME, "");
    }

    public String getServerUrl() {
        return Hawk.get(HawkConfig.CLOUD_SERVER_URL, DEFAULT_SERVER);
    }

    public void login(String serverUrl, String username, String password, LoginCallback callback) {
        new Thread(() -> {
            try {
                String base = normalizeServer(serverUrl);
                JsonObject body = new JsonObject();
                body.addProperty("username", username.trim());
                body.addProperty("password", password);
                Request request = new Request.Builder()
                        .url(base + "/api/auth/login")
                        .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString()))
                        .build();
                try (Response response = client().newCall(request).execute()) {
                    String text = response.body() != null ? response.body().string() : "";
                    JsonObject json = JsonParser.parseString(text).getAsJsonObject();
                    if (!response.isSuccessful() || !json.has("token")) {
                        String msg = json.has("message") ? json.get("message").getAsString() : "登录失败";
                        postError(callback, msg);
                        return;
                    }
                    String token = json.get("token").getAsString();
                    Hawk.put(HawkConfig.CLOUD_SERVER_URL, base);
                    Hawk.put(HawkConfig.CLOUD_TOKEN, token);
                    Hawk.put(HawkConfig.CLOUD_USERNAME, username.trim());
                    mainHandler.post(() -> {
                        connectWebSocket(base, token);
                        if (callback != null) callback.onSuccess(username.trim());
                    });
                }
            } catch (Exception e) {
                LOG.e("cloud-login: " + e.getMessage());
                postError(callback, "无法连接后端: " + e.getMessage());
            }
        }).start();
    }

    private void postError(LoginCallback callback, String msg) {
        mainHandler.post(() -> {
            if (callback != null) callback.onError(msg);
        });
    }

    public void connectWebSocket(String serverUrl, String token) {
        if (connecting) return;
        disconnect();
        connecting = true;
        String wsUrl = toWsUrl(serverUrl) + "/ws?token=" + token;
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client().newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                connecting = false;
                JsonObject auth = new JsonObject();
                auth.addProperty("type", "auth");
                auth.addProperty("role", "device");
                auth.addProperty("token", token);
                auth.addProperty("deviceName", "TVBox");
                auth.addProperty("deviceUuid", Hawk.get(HawkConfig.CLOUD_DEVICE_UUID, ""));
                auth.addProperty("activity", RemoteKeyHelper.currentActivityName());
                webSocket.send(auth.toString());
                sendHello(webSocket);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                handleMessage(bytes.utf8());
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                connecting = false;
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                connecting = false;
                LOG.e("cloud-ws: " + t.getMessage());
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (TextUtils.isEmpty(Hawk.get(HawkConfig.CLOUD_TOKEN, ""))) return;
        mainHandler.postDelayed(() -> autoStart(), 5000);
    }

    private void sendHello(WebSocket ws) {
        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("activity", RemoteKeyHelper.currentActivityName());
        ws.send(hello.toString());
    }

    private void handleMessage(String text) {
        try {
            JsonObject msg = JsonParser.parseString(text).getAsJsonObject();
            String type = msg.has("type") ? msg.get("type").getAsString() : "";
            switch (type) {
                case "auth_ok":
                    if (msg.has("deviceUuid")) {
                        Hawk.put(HawkConfig.CLOUD_DEVICE_UUID, msg.get("deviceUuid").getAsString());
                    }
                    break;
                case "ping":
                    if (webSocket != null) {
                        JsonObject pong = new JsonObject();
                        pong.addProperty("type", "pong");
                        webSocket.send(pong.toString());
                        JsonObject hello = new JsonObject();
                        hello.addProperty("type", "hello");
                        hello.addProperty("activity", RemoteKeyHelper.currentActivityName());
                        webSocket.send(hello.toString());
                    }
                    break;
                case "key":
                    mainHandler.post(() -> RemoteKeyHelper.dispatch(msg.get("key").getAsString()));
                    break;
                case "search":
                    mainHandler.post(() -> dispatchSearch(msg.get("word").getAsString()));
                    break;
                case "push":
                    mainHandler.post(() -> EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_PUSH_URL, msg.get("url").getAsString())));
                    break;
                case "setting":
                    mainHandler.post(() -> {
                        String key = msg.get("key").getAsString();
                        String value = msg.has("value") ? msg.get("value").getAsString() : "";
                        RemoteSettingsHelper.apply(key, value);
                    });
                    break;
                case "get_settings":
                    mainHandler.post(() -> sendSettings(msg.has("requestId") ? msg.get("requestId").getAsString() : ""));
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LOG.e("cloud-msg: " + e.getMessage());
        }
    }

    private void sendSettings(String requestId) {
        if (webSocket == null) return;
        JsonObject out = new JsonObject();
        out.addProperty("type", "settings");
        if (!TextUtils.isEmpty(requestId)) {
            out.addProperty("requestId", requestId);
        }
        out.add("data", RemoteSettingsHelper.getSettings());
        webSocket.send(out.toString());
    }

    private void dispatchSearch(String word) {
        if (TextUtils.isEmpty(word)) return;
        Intent intent = new Intent();
        intent.setAction(SearchReceiver.action);
        intent.setPackage(App.getInstance().getPackageName());
        intent.setComponent(new ComponentName(App.getInstance(), SearchReceiver.class));
        intent.putExtra("title", word);
        App.getInstance().sendBroadcast(intent);
    }

    public void disconnect() {
        mainHandler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            try {
                webSocket.close(1000, "logout");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }
        connecting = false;
    }

    private static String normalizeServer(String url) {
        String base = (url == null ? "" : url.trim()).replaceAll("/+$", "");
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        return base;
    }

    private static String toWsUrl(String httpUrl) {
        String base = normalizeServer(httpUrl);
        if (base.startsWith("https://")) {
            return "wss://" + base.substring(8);
        }
        return "ws://" + base.substring(7);
    }
}
