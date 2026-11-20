package com.abualaz.ai;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_SPEECH = 4101;
    private static final int REQUEST_MICROPHONE = 4102;
    private static final String PREFS_NAME = "abboud_private_brain";

    private LinearLayout messageList;
    private ScrollView chatScroll;
    private EditText messageInput;
    private SharedPreferences preferences;
    private TextToSpeech textToSpeech;
    private AudioManager audioManager;
    private boolean ttsReady = false;
    private SecureConfigStore secureConfig;
    private File localModelFile;
    private LocalBrainEngine localBrain;
    private final ExecutorService brainExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(11, 20, 36));
        window.setNavigationBarColor(Color.rgb(11, 20, 36));

        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        secureConfig = new SecureConfigStore(this);
        localModelFile = LocalModelDownloader.modelFile(getFilesDir());
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        initializeSpeech();
        buildInterface();
        addMessage("مرحباً، أنا عبود. أستطيع في هذه النسخة إجراء محادثة أولية، حفظ ملاحظاتك على الهاتف، واستخدام التعرف الصوتي المتاح في جهازك. ما الذي نبدأ به؟", false);
    }

    private void initializeSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ar", "SA"));
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = textToSpeech.setLanguage(new Locale("ar"));
                }
                textToSpeech.setSpeechRate(0.96f);
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED;
            }
        });
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(Color.rgb(11, 20, 36));
        root.setPadding(dp(14), dp(10), dp(14), dp(10));
        setContentView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(8), dp(4), dp(14));
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView avatar = new TextView(this);
        avatar.setText("ع");
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(26);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(Color.rgb(29, 180, 163), dp(22)));
        header.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, 0, 0);
        header.addView(heading, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView title = new TextView(this);
        title.setText("عبود | Abboud");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        heading.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("مساعدك الشخصي");
        subtitle.setTextColor(Color.rgb(163, 178, 199));
        subtitle.setTextSize(12);
        heading.addView(subtitle);

        Button settingsButton = makeButton("⋮", Color.rgb(36, 51, 72));
        settingsButton.setContentDescription("إعدادات عبود");
        settingsButton.setOnClickListener(v -> showSettings());
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(46), dp(44)));

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);
        chatScroll.setPadding(0, dp(4), 0, dp(8));
        root.addView(chatScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        chatScroll.addView(messageList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView privacy = new TextView(this);
        privacy.setText("الملاحظات التي تحفظها تبقى داخل مساحة التطبيق على هذا الجهاز.");
        privacy.setTextColor(Color.rgb(143, 160, 183));
        privacy.setTextSize(11);
        privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(dp(4), dp(7), dp(4), dp(9));
        root.addView(privacy);

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(7), dp(6), dp(7), dp(6));
        composer.setBackground(rounded(Color.rgb(27, 40, 59), dp(18)));
        root.addView(composer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        messageInput = new EditText(this);
        messageInput.setSingleLine(false);
        messageInput.setMaxLines(4);
        messageInput.setTextSize(15);
        messageInput.setTextColor(Color.WHITE);
        messageInput.setHintTextColor(Color.rgb(150, 165, 185));
        messageInput.setHint("اكتب لعبود هنا...");
        messageInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        messageInput.setBackgroundColor(Color.TRANSPARENT);
        messageInput.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        composer.addView(messageInput, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button voiceButton = makeButton("🎙", Color.rgb(48, 69, 94));
        voiceButton.setContentDescription("التحدث إلى عبود");
        voiceButton.setOnClickListener(v -> startVoiceInput());
        LinearLayout.LayoutParams voiceParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        voiceParams.setMargins(dp(4), 0, dp(4), 0);
        composer.addView(voiceButton, voiceParams);

        Button sendButton = makeButton("إرسال", Color.rgb(25, 169, 151));
        sendButton.setOnClickListener(v -> sendTypedMessage());
        composer.addView(sendButton, new LinearLayout.LayoutParams(dp(74), dp(46)));
    }

    private void sendTypedMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) {
            return;
        }
        messageInput.setText("");
        handleUserMessage(message);
    }

    private void handleUserMessage(String message) {
        addMessage(message, true);

        if (isLocalCommand(message)) {
            String response = createBasicResponse(message);
            addMessage(response, false);
            speak(response);
            return;
        }

        boolean cloudForThisQuestion = secureConfig.isCloudEnabled()
                && requiresCloud(message);
        if (cloudForThisQuestion || !localModelFile.exists()) {
            if (secureConfig.isCloudEnabled()) {
                askCloud(message);
            } else {
                String response = localModelFile.exists()
                        ? "العقل المحلي موجود، جارٍ تجهيزه..."
                        : "لم يتم تنزيل العقل المحلي بعد. افتح الإعدادات ثم اختر تحميل العقل المحلي. ويمكنك إعداد العقل السحابي للأسئلة المتقدمة.";
                if (localModelFile.exists()) {
                    askLocal(message);
                } else {
                    addMessage(response, false);
                    speak(response);
                }
            }
            return;
        }
        askLocal(message);
    }

    private boolean requiresCloud(String message) {
        String s = message.toLowerCase(Locale.ROOT);
        String[] markers = {"بحث عميق", "تحليل معمق", "حلل بالتفصيل", "ابحث في الإنترنت",
                "برمج", "اكتب كود", "قارن بالتفصيل", "خطة شاملة", "استعمل العقل السحابي"};
        for (String marker : markers) if (s.contains(marker)) return true;
        return false;
    }

    private void askCloud(String message) {
        addMessage("أستخدم العقل السحابي لهذا السؤال...", false);
        brainExecutor.execute(() -> {
            String response;
            try {
                response = CloudBrainClient.ask(secureConfig.getEndpoint(),
                        secureConfig.getModel(), secureConfig.getApiKey(), message);
            } catch (Exception error) {
                response = "تعذر الوصول إلى العقل السحابي: " + error.getMessage();
            }
            final String answer = response;
            runOnUiThread(() -> { addMessage(answer, false); speak(answer); });
        });
    }

    private void askLocal(String message) {
        addMessage("عبود يفكر محلياً على الهاتف...", false);
        brainExecutor.execute(() -> {
            String response;
            try {
                if (localBrain == null) {
                    LocalBrainEngine engine = new LocalBrainEngine(
                            localModelFile.getAbsolutePath(), getCacheDir().getAbsolutePath());
                    engine.load();
                    localBrain = engine;
                }
                response = localBrain.ask(message);
            } catch (Exception error) {
                response = "تعذر تشغيل العقل المحلي: " + error.getMessage();
                if (secureConfig.isCloudEnabled()) {
                    try {
                        response = CloudBrainClient.ask(secureConfig.getEndpoint(),
                                secureConfig.getModel(), secureConfig.getApiKey(), message);
                    } catch (Exception cloudError) {
                        response += "\\nوالعقل السحابي غير متاح: " + cloudError.getMessage();
                    }
                }
            }
            final String answer = response;
            runOnUiThread(() -> { addMessage(answer, false); speak(answer); });
        });
    }

    private boolean isLocalCommand(String message) {
        String s = message.toLowerCase(Locale.ROOT);
        String[] keys = {
                "احفظ ", "تذكر ", "ماذا حفظت", "ما حفظت", "اذكر ما حفظت",
                "ماذا تتذكر", "امسح ذاكرتك", "احذف الذاكرة",
                "افتح الإعدادات", "افتح الاعدادات", "ارفع الصوت", "زد الصوت",
                "اخفض الصوت", "خفض الصوت", "قلل الصوت", "افتح واتساب",
                "افتح يوتيوب", "افتح الهاتف", "افتح الاتصال", "افتح الكاميرا",
                "افتح المتصفح", "كم الساعة", "الوقت الآن", "ما التاريخ",
                "تاريخ اليوم", "اتصل على", "اتصل بأ", "أرسل رسالة",
                "ارسل رسالة", "السلام عليكم", "مرحبا", "هلا", "يا عبود",
                "من أنت", "من انت"
        };
        for (String key : keys) if (s.contains(key)) return true;
        return false;
    }

    private String createBasicResponse(String message) {
        String value = message.trim();
        String lower = value.toLowerCase(Locale.ROOT);

        if (lower.startsWith("احفظ ") || lower.startsWith("تذكر ")) {
            int split = value.indexOf(' ');
            String note = split >= 0 ? value.substring(split + 1).trim() : "";
            if (note.isEmpty()) {
                return "اكتب المعلومة بعد كلمة احفظ، وسأخزنها في ذاكرة عبود على هذا الجهاز.";
            }
            JSONArray notes = readNotes();
            notes.put(note);
            preferences.edit().putString("brain_notes", notes.toString()).apply();
            return "تم حفظ هذه الملاحظة محلياً في ذاكرة عبود: " + note;
        }

        if (lower.contains("ماذا حفظت") || lower.contains("ما حفظت") ||
                lower.contains("اذكر ما حفظت") || lower.contains("ماذا تتذكر")) {
            JSONArray notes = readNotes();
            if (notes.length() == 0) {
                return "لا توجد ملاحظات محفوظة حتى الآن. قل: احفظ أن ...";
            }
            StringBuilder result = new StringBuilder("هذه الملاحظات المحفوظة على جهازك:\n");
            for (int i = 0; i < notes.length(); i++) {
                result.append(i + 1).append(". ").append(notes.optString(i)).append("\n");
            }
            return result.toString().trim();
        }

        if (lower.contains("امسح ذاكرتك") || lower.contains("احذف الذاكرة")) {
            confirmMemoryDeletion();
            return "عرضت لك نافذة تأكيد. لن أحذف الملاحظات إلا بعد موافقتك.";
        }

        if (lower.contains("افتح الإعدادات") || lower.contains("افتح الاعدادات")) {
            openSystemSettings();
            return "فتحت إعدادات الهاتف.";
        }

        if (lower.contains("ارفع الصوت") || lower.contains("زد الصوت")) {
            adjustVolume(AudioManager.ADJUST_RAISE);
            return "رفعت مستوى صوت الوسائط.";
        }

        if (lower.contains("اخفض الصوت") || lower.contains("خفض الصوت") ||
                lower.contains("قلل الصوت")) {
            adjustVolume(AudioManager.ADJUST_LOWER);
            return "خفضت مستوى صوت الوسائط.";
        }

        if (lower.contains("افتح واتساب")) {
            return launchApp("com.whatsapp", "واتساب");
        }

        if (lower.contains("افتح يوتيوب")) {
            return launchApp("com.google.android.youtube", "يوتيوب");
        }

        if (lower.contains("افتح الهاتف") || lower.contains("افتح الاتصال")) {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL));
                return "فتحت لوحة الاتصال. اختر الرقم بنفسك قبل إجراء المكالمة.";
            } catch (Exception e) {
                return "تعذر فتح تطبيق الاتصال على هذا الجهاز.";
            }
        }

        if (lower.contains("افتح الكاميرا")) {
            try {
                startActivity(new Intent("android.media.action.IMAGE_CAPTURE"));
                return "طلبت فتح الكاميرا.";
            } catch (Exception e) {
                return "لم أتمكن من فتح الكاميرا.";
            }
        }

        if (lower.contains("افتح المتصفح")) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
                startActivity(browser);
                return "فتحت المتصفح.";
            } catch (Exception e) {
                return "لم أجد متصفحاً متاحاً.";
            }
        }

        if (lower.contains("كم الساعة") || lower.contains("الوقت الآن")) {
            String time = new SimpleDateFormat("hh:mm a", new Locale("ar")).format(new Date());
            return "الوقت الآن " + time;
        }

        if (lower.contains("ما التاريخ") || lower.contains("تاريخ اليوم")) {
            String date = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar")).format(new Date());
            return "تاريخ اليوم " + date;
        }

        if (lower.contains("اتصل على") || lower.contains("اتصل بأ")) {
            return "ميزة اختيار جهة الاتصال وإظهار تأكيد قبل الاتصال ستضاف في مرحلة التحكم بالهاتف. لم أجر أي اتصال.";
        }

        if (lower.contains("أرسل رسالة") || lower.contains("ارسل رسالة")) {
            return "إرسال الرسائل يحتاج اختيار جهة الاتصال ومراجعة نص الرسالة قبل فتح تطبيق الإرسال. هذه الميزة ستضاف في مرحلة لاحقة.";
        }

        if (lower.contains("السلام عليكم") || lower.contains("مرحبا") ||
                lower.contains("هلا") || lower.contains("يا عبود")) {
            return "وعليكم السلام ورحمة الله. حيّاك، أنا حاضر. كيف أساعدك؟";
        }

        if (lower.contains("من أنت") || lower.contains("من انت")) {
            return "أنا عبود، نسخة أولية من مساعدك الشخصي. هذه النسخة تعمل دون اتصال بخادم ذكاء اصطناعي، وتوفر أوامر أساسية وذاكرة ملاحظات محلية.";
        }

        return "وصلتني رسالتك. هذه نسخة المرحلة الأولى، وتدعم حالياً المحادثة الأولية والأوامر الأساسية وحفظ الملاحظات محلياً. ربط نموذج ذكاء اصطناعي محلي أو سحابي سيأتي في مرحلة مستقلة.";
    }

    private JSONArray readNotes() {
        try {
            return new JSONArray(preferences.getString("brain_notes", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void confirmMemoryDeletion() {
        new AlertDialog.Builder(this)
                .setTitle("حذف ذاكرة عبود")
                .setMessage("هل تريد حذف جميع الملاحظات التي حفظتها على هذا الجهاز؟")
                .setNegativeButton("إلغاء", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("حذف", (dialog, which) -> {
                    preferences.edit().remove("brain_notes").apply();
                    addMessage("حُذفت الملاحظات المحفوظة من ذاكرة عبود على هذا الجهاز.", false);
                })
                .show();
    }

    private String launchApp(String packageName, String appName) {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                return "تطبيق " + appName + " غير مثبت أو لا يمكن فتحه.";
            }
            startActivity(launch);
            return "فتحت " + appName + ".";
        } catch (Exception e) {
            return "تعذر فتح " + appName + ".";
        }
    }

    private void openSystemSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح الإعدادات", Toast.LENGTH_SHORT).show();
        }
    }

    private void adjustVolume(int direction) {
        if (audioManager != null) {
            audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    direction,
                    AudioManager.FLAG_SHOW_UI
            );
        }
    }

    private void startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        launchSpeechRecognizer();
    }

    private void launchSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث الآن إلى عبود");
        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "لا يوجد تطبيق للتعرف على الكلام. فعّل خدمة التعرف الصوتي في الهاتف.",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchSpeechRecognizer();
            } else {
                Toast.makeText(this, "يحتاج عبود إلى إذن الميكروفون لاستخدام الإدخال الصوتي.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                handleUserMessage(results.get(0));
            }
        }
    }

    private void speak(String text) {
        if (textToSpeech != null && ttsReady) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "abboud_reply");
        }
    }

    private void addMessage(String text, boolean fromUser) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(15);
        bubble.setLineSpacing(dp(2), 1.0f);
        bubble.setPadding(dp(14), dp(11), dp(14), dp(11));
        bubble.setGravity(Gravity.RIGHT);
        bubble.setMaxWidth(dp(310));
        bubble.setBackground(rounded(
                fromUser ? Color.rgb(21, 112, 111) : Color.rgb(35, 51, 73),
                dp(17)
        ));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(fromUser ? Gravity.START : Gravity.END);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(bubble, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        messageList.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private Button makeButton(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setPadding(dp(5), dp(4), dp(5), dp(4));
        button.setBackground(rounded(color, dp(14)));
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showSettings() {
        String[] items = {
                "إيقاف الرد الصوتي",
                "تشغيل الرد الصوتي",
                "إعداد العقل السحابي",
                "تحميل العقل المحلي",
                "حذف الذاكرة المحفوظة",
                "حول هذه النسخة"
        };
        new AlertDialog.Builder(this)
                .setTitle("إعدادات عبود")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        ttsReady = false;
                        if (textToSpeech != null) {
                            textToSpeech.stop();
                        }
                        Toast.makeText(this, "تم إيقاف الرد الصوتي حتى إعادة فتح التطبيق.",
                                Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        if (textToSpeech != null) {
                            int result = textToSpeech.setLanguage(new Locale("ar", "SA"));
                            ttsReady = result != TextToSpeech.LANG_MISSING_DATA
                                    && result != TextToSpeech.LANG_NOT_SUPPORTED;
                        }
                        Toast.makeText(this, ttsReady ? "الرد الصوتي جاهز." :
                                "الصوت العربي غير متوفر في محرك النطق الحالي.",
                                Toast.LENGTH_LONG).show();
                    } else if (which == 2) {
                        showCloudSettings();
                    } else if (which == 3) {
                        showLocalBrainSettings();
                    } else if (which == 4) {
                        confirmMemoryDeletion();
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle("عبود 0.2.0")
                                .setMessage("نسخة تطويرية بواجهة عربية، أوامر محلية، ذاكرة خاصة، محرك ذكاء محلي قابل للتنزيل، واتصال سحابي اختياري. جودة الصوت تعتمد على محرك الهاتف.")
                                .setPositiveButton("حسناً", null)
                                .show();
                    }
                })
                .show();
    }

    private void showCloudSettings() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        TextView info = new TextView(this);
        info.setText("عند تفعيل العقل السحابي، يُرسل نص السؤال الحالي إلى مزود الذكاء الاصطناعي. لا تُرسل الملاحظات المحفوظة أو المحادثات السابقة تلقائياً. استخدم HTTPS ومزوداً تثق به.");
        info.setTextSize(13);
        info.setTextColor(Color.DKGRAY);
        form.addView(info);

        EditText endpoint = new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setText(secureConfig.getEndpoint());
        endpoint.setHint("رابط API المتوافق مع Chat Completions");
        form.addView(endpoint);

        EditText model = new EditText(this);
        model.setSingleLine(true);
        model.setText(secureConfig.getModel());
        model.setHint("اسم النموذج لدى المزود");
        form.addView(model);

        EditText apiKey = new EditText(this);
        apiKey.setSingleLine(true);
        apiKey.setInputType(129);
        apiKey.setHint("مفتاح API (اتركه فارغاً للاحتفاظ بالمفتاح المحفوظ)");
        form.addView(apiKey);

        CheckBox enabled = new CheckBox(this);
        enabled.setText("تفعيل العقل السحابي للأسئلة العامة");
        enabled.setChecked(secureConfig.isCloudEnabled());
        form.addView(enabled);

        new AlertDialog.Builder(this)
                .setTitle("إعداد العقل السحابي")
                .setView(form)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    try {
                        secureConfig.save(
                                endpoint.getText().toString(),
                                model.getText().toString(),
                                apiKey.getText().toString(),
                                enabled.isChecked()
                        );
                        Toast.makeText(this, "تم حفظ إعدادات العقل السحابي على الجهاز.", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "لم تحفظ الإعدادات: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showLocalBrainSettings() {
        if (localModelFile.exists()) {
            new AlertDialog.Builder(this)
                    .setTitle("العقل المحلي جاهز للتشغيل")
                    .setMessage("تم العثور على ملف Qwen3 المحلي. سيُحمّل إلى الذاكرة عند أول سؤال، وقد يستغرق التحميل الأول بعض الوقت.")
                    .setNegativeButton("إغلاق", null)
                    .setPositiveButton("تهيئة العقل", (d, w) -> loadLocalBrain())
                    .show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("تنزيل العقل المحلي Qwen3")
                .setMessage("حجم النموذج نحو 329 ميجابايت. يلزم اتصال Wi-Fi ومساحة فارغة لا تقل عن 600 ميجابايت. سيُحفظ داخل مساحة التطبيق الخاصة، ولن يُضاف إلى APK أو GitHub. التنزيل اختياري ويمكنك استخدام العقل السحابي بدلاً منه.")
                .setNegativeButton("لاحقاً", null)
                .setPositiveButton("تنزيل النموذج", (d, w) -> downloadLocalBrain())
                .show();
    }

    private void downloadLocalBrain() {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("تنزيل عقل عبود");
        progress.setMessage("جارٍ تنزيل نموذج Qwen3، يرجى إبقاء الاتصال مستقراً...");
        progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setCancelable(false);
        progress.show();
        brainExecutor.execute(() -> {
            try {
                LocalModelDownloader.download(getFilesDir(), percent ->
                        runOnUiThread(() -> progress.setProgress(percent)));
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(this, "اكتمل تنزيل العقل المحلي.", Toast.LENGTH_LONG).show();
                    loadLocalBrain();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(this).setTitle("تعذر تنزيل النموذج")
                            .setMessage(error.getMessage()).setPositiveButton("حسناً", null).show();
                });
            }
        });
    }

    private void loadLocalBrain() {
        Toast.makeText(this, "جارٍ تهيئة العقل المحلي في الخلفية...", Toast.LENGTH_LONG).show();
        brainExecutor.execute(() -> {
            try {
                if (localBrain == null) {
                    LocalBrainEngine engine = new LocalBrainEngine(
                            localModelFile.getAbsolutePath(), getCacheDir().getAbsolutePath());
                    engine.load();
                    localBrain = engine;
                }
                runOnUiThread(() -> Toast.makeText(this, "العقل المحلي أصبح جاهزاً.", Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("فشل تشغيل العقل المحلي")
                        .setMessage(error.getMessage()).setPositiveButton("حسناً", null).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (localBrain != null) {
            LocalBrainEngine brain = localBrain;
            localBrain = null;
            brainExecutor.execute(() -> { try { brain.close(); } catch (Exception ignored) {} });
        }
        brainExecutor.shutdown();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
