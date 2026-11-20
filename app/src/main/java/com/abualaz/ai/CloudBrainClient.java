package com.abualaz.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class CloudBrainClient {
    public static String ask(String endpoint, String model, String apiKey, String prompt) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("لم يتم إعداد مفتاح خدمة الذكاء السحابي.");
        }
        if (endpoint == null || !endpoint.startsWith("https://")) {
            throw new IllegalArgumentException("رابط الخدمة غير آمن أو غير صحيح.");
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(35000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.55);
            body.put("max_tokens", 900);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject()
                    .put("role", "system")
                    .put("content", "أنت عبود، مساعد شخصي عربي واضح وعملي. أجب بلغة المستخدم، وكن دقيقاً ومختصراً. لا تدّع تنفيذ أي إجراء على الهاتف ما لم يحدث فعلاً. لا تطلب كلمات المرور أو مفاتيح API."));
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", messages);

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(bytes);
                out.flush();
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
            }
            JSONObject json = new JSONObject(response.toString());
            if (status < 200 || status >= 300) {
                String message = json.optJSONObject("error") != null
                        ? json.optJSONObject("error").optString("message", "خطأ من مزود الخدمة")
                        : "رفض مزود الخدمة الطلب، رمز الخطأ: " + status;
                throw new IllegalStateException(message);
            }
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new IllegalStateException("لم يرجع النموذج إجابة.");
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            String answer = message == null ? "" : message.optString("content", "").trim();
            if (answer.isEmpty()) throw new IllegalStateException("وصل رد فارغ من النموذج.");
            return answer;
        } finally {
            connection.disconnect();
        }
    }

    private CloudBrainClient() {}
}
