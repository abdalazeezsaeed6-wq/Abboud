package com.abualaz.ai;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_SPEECH = 701;
    private TextView conversation;
    private EditText messageInput;
    private TextToSpeech textToSpeech;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF101827);
        getWindow().setNavigationBarColor(0xFF101827);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(16));
        root.setBackgroundColor(0xFFF4F6FA);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = new TextView(this);
        title.setText("عبود");
        title.setTextSize(30);
        title.setTextColor(0xFF14233B);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView subtitle = new TextView(this);
        subtitle.setText("مساعدك الشخصي");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF68758A);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(32)));

        ScrollView scroll = new ScrollView(this);
        conversation = new TextView(this);
        conversation.setText("عبود: مرحباً! أنا النسخة التأسيسية من عبود. اكتب رسالة أو اضغط على الميكروفون.\n\n");
        conversation.setTextSize(16);
        conversation.setTextColor(0xFF243247);
        conversation.setPadding(dp(16), dp(16), dp(16), dp(16));
        conversation.setBackgroundColor(0xFFFFFFFF);
        scroll.addView(conversation);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.setMargins(0, dp(18), 0, dp(14));
        root.addView(scroll, scrollParams);

        messageInput = new EditText(this);
        messageInput.setSingleLine(false);
        messageInput.setMinLines(2);
        messageInput.setHint("اكتب رسالتك هنا...");
        messageInput.setTextSize(16);
        root.addView(messageInput, new LinearLayout.LayoutParams(-1, dp(58)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        Button send = new Button(this);
        send.setText("إرسال");
        Button speak = new Button(this);
        speak.setText("🎙 تحدث");
        actions.addView(send, new LinearLayout.LayoutParams(0, dp(54), 1));
        actions.addView(speak, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(actions);
        setContentView(root);

        send.setOnClickListener(v -> sendMessage());
        speak.setOnClickListener(v -> startSpeechInput());

        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("ar"));
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "صوت العربية غير متوفر في محرك النطق الحالي", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void sendMessage() {
        String message = messageInput.getText().toString().trim();
        if (message.isEmpty()) return;
        append("أنت: " + message);
        messageInput.setText("");
        String reply = makeStarterReply(message);
        append("عبود: " + reply);
        if (textToSpeech != null) textToSpeech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "abboud-reply");
    }

    private String makeStarterReply(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.contains("السلام عليكم")) return "وعليكم السلام ورحمة الله وبركاته. حيا بك!";
        if (message.contains("اسمك") || lower.contains("your name")) return "أنا عبود، مساعدك الشخصي.";
        if (message.contains("كيف حالك")) return "بخير، وجاهز أساعدك.";
        if (message.contains("الوقت")) return "يمكنك معرفة الوقت الحالي من ساعة الهاتف.";
        return "وصلتني رسالتك. هذه نسخة تأسيسية، وسيتم ربط محرك الذكاء المحلي والسحابي في المراحل القادمة.";
    }

    private void startSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "تحدث إلى عبود");
        try {
            startActivityForResult(intent, REQUEST_SPEECH);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "لا يوجد تطبيق للتعرف على الصوت في الهاتف", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                messageInput.setText(results.get(0));
                sendMessage();
            }
        }
    }

    private void append(String line) {
        conversation.append(line + "\n\n");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}
