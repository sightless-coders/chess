package org.sightlesscoders.chess.online;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class HttpClient {
    private static final String TAG = "HttpClient";

    public interface Callback {
        void onSuccess(String response);
        void onError(Exception e);
    }

    public static void get(String urlString, Map<String, String> headers, Callback callback) {
        new RequestTask("GET", urlString, null, headers, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void post(String urlString, String jsonBody, Map<String, String> headers, Callback callback) {
        new RequestTask("POST", urlString, jsonBody, headers, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void put(String urlString, String jsonBody, Map<String, String> headers, Callback callback) {
        new RequestTask("PUT", urlString, jsonBody, headers, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void patch(String urlString, String jsonBody, Map<String, String> headers, Callback callback) {
        new RequestTask("PATCH", urlString, jsonBody, headers, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void delete(String urlString, Map<String, String> headers, Callback callback) {
        new RequestTask("DELETE", urlString, null, headers, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static class RequestTask extends AsyncTask<Void, Void, Result> {
        private final String method;
        private final String urlString;
        private final String jsonBody;
        private final Map<String, String> headers;
        private final Callback callback;

        RequestTask(String method, String urlString, String jsonBody, Map<String, String> headers, Callback callback) {
            this.method = method;
            this.urlString = urlString;
            this.jsonBody = jsonBody;
            this.headers = headers;
            this.callback = callback;
        }

        @Override
        protected Result doInBackground(Void... voids) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(jsonBody != null);
                conn.setDoInput(true);

                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
                conn.setRequestProperty("Content-Type", "application/json");

                if (jsonBody != null) {
                    byte[] postData = jsonBody.getBytes("UTF-8");
                    conn.setRequestProperty("Content-Length", String.valueOf(postData.length));
                    try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
                        os.write(postData);
                    }
                }

                int responseCode = conn.getResponseCode();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                return new Result(responseCode, response.toString());
            } catch (Exception e) {
                return new Result(-1, e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        @Override
        protected void onPostExecute(Result result) {
            if (result.code >= 200 && result.code < 300) {
                callback.onSuccess(result.body);
            } else {
                callback.onError(new ApiException(result.code, result.body));
            }
        }
    }

    private static class Result {
        final int code;
        final String body;

        Result(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    public static class ApiException extends Exception {
        public final int statusCode;
        public final String responseBody;

        ApiException(int statusCode, String responseBody) {
            super("API Error: " + statusCode + " - " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }
}