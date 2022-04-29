package com.adv.tms.stomp;

import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WSMessaging {
    public static class Agent {
        private class WebSocketListener extends okhttp3.WebSocketListener {
            @Override
            public void onOpen(okhttp3.WebSocket socket, okhttp3.Response resp) {
                super.onOpen(socket, resp);
                mConnected = true;

                /* Send CONNECT. */
                Stomp.Frame fConnect = Stomp.toConnect();
                Log.i(TAG, "onOpen: " + fConnect);
                socket.send(Stomp.serialize(fConnect));

                /* Send SUBSCRIBE. */
                for (String topic : mTopics) {
                    Stomp.Frame fSubscribe = Stomp.toSubscribe(mDest.clientID, topic);
                    Log.i(TAG, "onOpen: " + fSubscribe);
                    socket.send(Stomp.serialize(fSubscribe));
                }
            }

            @Override
            public void onMessage(okhttp3.WebSocket socket, String text) {
                super.onMessage(socket, text);

                Stomp.Frame frame = Stomp.deserialize(text);
                String topic = frame.getHeader(Stomp.HEADER_DESTINATION);
                if (mCallback != null) {
                    mCallback.onReceive(topic, frame);
                }
            }

            @Override
            public void onMessage(okhttp3.WebSocket socket, okio.ByteString bytes) {
                super.onMessage(socket, bytes);
                Log.i(TAG, "onMessage: " + bytes.hex());
            }

            @Override
            public void onClosing(okhttp3.WebSocket socket, int code, String reason) {
                super.onClosing(socket, code, reason);
                Log.i(TAG, "onClosing: code=" + code + ", reason=" + reason);
                mConnected = false;
                socket.close(1000, null);
            }

            @Override
            public void onClosed(okhttp3.WebSocket socket, int code, String reason) {
                super.onClosed(socket, code, reason);
                Log.i(TAG, "onClosed: code=" + code + ", reason=" + reason);
                mConnected = false;
            }

            @Override
            public void onFailure(okhttp3.WebSocket socket, Throwable t, okhttp3.Response resp) {
                super.onFailure(socket, t, resp);
                Log.w(TAG, "onFailure: " + t + " resp=" + resp);
            }
        }

        private final String TAG = "WSMAgent@" + Integer.toHexString(hashCode());
        private Dest mDest;
        private okhttp3.WebSocket mWebSocket;
        private boolean mConnected = false;
        private final List<String> mTopics = new LinkedList<>();
        private Stomp.Callback mCallback;

        public Agent() {}

        public void set(Stomp.Callback callback) {
            mCallback = callback;
        }

        public void connect(Dest dest) {
            /* Fool-proofing of invalid argument. */
            if (dest == null
                    || !dest.url.startsWith("ws://") && !dest.url.startsWith("wss://")) {
                Log.w(TAG, "connect(): Invalid " + dest);
                return;
            }

            /* Try to disconnect old connection. */
            disconnect();

            /* Try to connect to destination. */
            Log.i(TAG, "connect(): " + dest);
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(dest.timeoutInMs, TimeUnit.MILLISECONDS)
                    .readTimeout(dest.timeoutInMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(dest.timeoutInMs, TimeUnit.MILLISECONDS)
                    .pingInterval(dest.timeoutInMs, TimeUnit.MILLISECONDS)
                    .build();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(dest.url)
                    .build();
            mDest = dest;
            mWebSocket = client.newWebSocket(request, new WebSocketListener());

            // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
            client.dispatcher().executorService().shutdown();
        }

        public void disconnect() {
            /* Fool-proofing of null socket. */
            if (mWebSocket == null) {
                return;
            }

            /* Try to disconnect. */
            try {
                Log.i(TAG, "disconnect(): " + mDest.url);
                if (!mConnected) {
                    mWebSocket.cancel();
                }
                mWebSocket.close(1000, "Normal closure.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "disconnect(): " + e);
            } finally {
                mDest = null;
                mWebSocket = null;
                mConnected = false;
                mTopics.clear();
            }
        }

        public Dest getDest() {
            return mDest;
        }

        public void subscribe(String topic) {
            if (topic == null) {
                Log.w(TAG, "subscribe: Null topic!");
                return;
            }
            if (mTopics.contains(topic)) {
                Log.w(TAG, "subscribe: topic " + topic + " has subscribed!");
                return;
            }

            mTopics.add(topic);
            if (mWebSocket != null && mDest != null) {
                Stomp.Frame fSubscribe = Stomp.toSubscribe(mDest.clientID, topic);
                mWebSocket.send(Stomp.serialize(fSubscribe));
            }
        }

        public void unsubscribe(String topic) {
            mTopics.remove(topic);
        }

        public void sendJson(String topic, String content) {
            if (topic == null) {
                Log.w(TAG, "sendJson: Null topic!");
                return;
            }

            if (mWebSocket != null && mDest != null) {
                Stomp.Frame frame = Stomp.toSendJson(topic, content);
                Log.v(TAG, "sendJson: " + frame);
                mWebSocket.send(Stomp.serialize(frame));
            }
        }

        public void sendText(String topic,String content) {
            if (topic == null) {
                Log.w(TAG, "sendText: Null topic!");
                return;
            }

            if (mWebSocket != null && mDest != null) {
                Stomp.Frame frame = Stomp.toSendTextPlain(topic, content);
                Log.v(TAG, "sendText: " + frame);
                mWebSocket.send(Stomp.serialize(frame));
            }
        }
    }

    public static class Dest {
        public static class Builder {
            private String url = "";
            private String clientID = "";
            private long timeoutInMs = 5000L;

            public Builder() {}

            /** For example, ws://172.22.24.87:8080/cm-websocket */
            public Builder setUrl(String url) {
                this.url = (url == null) ? "" : url;
                return this;
            }

            /** For example, John. */
            public Builder setClientID(String clientID) {
                this.clientID = (clientID == null) ? "" : clientID;
                return this;
            }

            /** Default is 5000ms. */
            public Builder setTimeout(long timeoutInMs) {
                this.timeoutInMs = (timeoutInMs <= 0) ? 5000L : timeoutInMs;
                return this;
            }

            public Dest build() {
                return new Dest(this);
            }
        }

        private final String TAG = "WSMDest@" + Integer.toHexString(hashCode());
        public final String url;
        public final String clientID;
        public final long timeoutInMs;

        private Dest(Builder builder) {
            this.url = builder.url;
            this.clientID = builder.clientID;
            this.timeoutInMs = builder.timeoutInMs;
        }

        @Override
        public String toString() {
            return TAG + "{url=" + url + " timeout=" + timeoutInMs + ", client=" + clientID + "}";
        }
    }

    public static class Stomp {
        private static final String COMMAND_CONNECT = "CONNECT";
        private static final String COMMAND_SUBSCRIBE = "SUBSCRIBE";
        private static final String COMMAND_SEND = "SEND";
        private static final String HEADER_ACCEPT_VERSION = "accept-version";
        private static final String HEADER_HEARTBEAT = "heart-beat";
        private static final String HEADER_ID = "id";
        private static final String HEADER_DESTINATION = "destination";
        private static final String HEADER_CONTENT_TYPE = "content-type";

        public interface Callback {
            void onReceive(String topic, Frame frame);
        }

        public static class Frame {
            private String command = "";
            private Map<String, String> headers = new HashMap<>();
            private String content = "";

            @Override
            public String toString() {
                return "Frame{" + command + ", " + headers + ", " + content + "}";
            }

            public String getCommand() {
                return command;
            }

            private void setCommand(String command) {
                this.command = command;
            }

            public String getHeader(String name) {
                return headers.get(name);
            }

            public Map<String, String> getHeaders() {
                return new HashMap<>(headers);
            }

            private void setHeader(String name, String value) {
                headers.put(name, value);
            }

            public String getContent() {
                return content;
            }

            private void setContent(String body) {
                this.content = body;
            }
        }

        public static String serialize(Frame frame) {
            StringBuilder strBuilder = new StringBuilder();
            strBuilder.append(frame.getCommand() + "\n");
            for (Map.Entry<String, String> header : frame.getHeaders().entrySet()) {
                strBuilder.append(header.getKey()).append(":").append(header.getValue()).append("\n");
            }
            strBuilder.append("\n");
            strBuilder.append(frame.getContent());
            strBuilder.append('\0');
            return strBuilder.toString();
        }

        public static Frame deserialize(String message) {
            Frame frame = new Frame();
            String[] lines = message.split("\n");

            /* Parsing and setup command. */
            String command = lines[0].trim();
            frame.setCommand(command);

            /* Parsing and setup headers. */
            int i = 1;
            for (; i < lines.length; ++i) {
                String line = lines[i].trim();
                if (line.equals("")) {
                    break;
                }
                String[] parts = line.split(":");
                String name = parts[0].trim();
                String value = "";
                if (parts.length == 2) {
                    value = parts[1].trim();
                }
                frame.setHeader(name, value);
            }

            /* Parsing and setup content. */
            StringBuilder strBuilder = new StringBuilder();
            for (; i < lines.length; ++i) {
                strBuilder.append(lines[i]);
            }
            String content = strBuilder.toString().trim();
            frame.setContent(content);

            return frame;
        }

        private static Frame toConnect() {
            Frame frame = new Frame();
            frame.setCommand(COMMAND_CONNECT);
            frame.setHeader(HEADER_ACCEPT_VERSION, "1.1");
            frame.setHeader(HEADER_HEARTBEAT, "10000,10000");
            return frame;
        }

        private static Frame toSubscribe(String id, String topic) {
            Frame frame = new Frame();
            frame.setCommand(COMMAND_SUBSCRIBE);
            frame.setHeader(HEADER_ID, id);
            frame.setHeader(HEADER_DESTINATION, topic);
            return frame;
        }

        private static Frame toSendJson(String topic, String content) {
            Frame frame = new Frame();
            frame.setCommand(COMMAND_SEND);
            frame.setHeader(HEADER_DESTINATION, topic);
            frame.setHeader(HEADER_CONTENT_TYPE, "application/json");
            frame.setContent(content);
            return frame;
        }

        private static Frame toSendTextPlain(String topic, String content) {
            Frame frame = new Frame();
            frame.setCommand(COMMAND_SEND);
            frame.setHeader(HEADER_DESTINATION, topic);
            frame.setHeader(HEADER_CONTENT_TYPE, "text/plain");
            frame.setContent(content);
            return frame;
        }
    }
}
