package com.abualaz.ai;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class LocalModelDownloader {
    public interface Progress {
        void onProgress(int percent);
    }

    public static File modelFile(File filesDir) {
        File folder = new File(filesDir, "brain/models");
        if (!folder.exists()) folder.mkdirs();
        return new File(folder, "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm");
    }

    public static void download(File filesDir, Progress progress) throws Exception {
        File target = modelFile(filesDir);
        File folder = target.getParentFile();
        if (folder == null || (!folder.exists() && !folder.mkdirs())) {
            throw new IllegalStateException("تعذر إنشاء مجلد النموذج.");
        }
        File temp = new File(folder, target.getName() + ".part");
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm"
        ).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "Abboud-Android/0.2.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("تعذر تنزيل النموذج. HTTP " + status);
            }
            long total = connection.getContentLengthLong();
            if (total > 0 && total < 300L * 1024L * 1024L) {
                throw new IllegalStateException("حجم ملف النموذج غير متوقع، أوقف التنزيل للحماية.");
            }
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(temp, false)) {
                byte[] buffer = new byte[1024 * 128];
                long count = 0;
                int read;
                int lastPercent = -1;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    count += read;
                    if (total > 0 && progress != null) {
                        int percent = (int) Math.min(100, (count * 100L) / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            progress.onProgress(percent);
                        }
                    }
                }
                out.flush();
                if (total > 0 && count != total) {
                    throw new IllegalStateException("اكتمل اتصال التنزيل قبل اكتمال ملف النموذج.");
                }
                if (count < 300L * 1024L * 1024L || count > 400L * 1024L * 1024L) {
                    throw new IllegalStateException("حجم النموذج بعد التنزيل غير صحيح.");
                }
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("تعذر استبدال ملف النموذج القديم.");
            }
            if (!temp.renameTo(target)) {
                throw new IllegalStateException("تعذر تثبيت ملف النموذج بعد التنزيل.");
            }
            if (progress != null) progress.onProgress(100);
        } catch (Exception error) {
            if (temp.exists()) temp.delete();
            throw error;
        } finally {
            connection.disconnect();
        }
    }

    private LocalModelDownloader() {}
}
