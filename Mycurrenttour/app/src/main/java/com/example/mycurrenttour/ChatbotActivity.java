package com.example.mycurrenttour;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Full-screen "Trip Assistant" chat - pushed from the floating chatbot icon on the Discovery tab
 * only (see DiscoveryFragment). Own Activity like any other detail screen, so it has no bottom
 * nav and back returns to Discovery.
 *
 * Demo reply only (no AI/RAG backend yet - chưa có DeepSeek+MongoDB): always suggests the first
 * mock tour. TODO: swap replyWithMockSuggestion() for a real API call once the backend is ready.
 */
public class ChatbotActivity extends AppCompatActivity {

    private static final String[] QUICK_REPLIES = {
            "Địa điểm ăn uống", "Tour giá rẻ", "Đi 1 mình", "Tour gần đây"
    };

    private LinearLayout layoutChatMessages;
    private ScrollView scrollChatMessages;
    private EditText edtChatInput;
    private View scrollQuickReplies;
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    // Typing-dot bounce animators run on an infinite repeat, so they must be tracked and cancelled
    // explicitly - onDestroy() won't stop them on its own.
    private final List<Animator> activeAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        layoutChatMessages = findViewById(R.id.layoutChatMessages);
        scrollChatMessages = findViewById(R.id.scrollChatMessages);
        edtChatInput = findViewById(R.id.edtChatInput);
        scrollQuickReplies = findViewById(R.id.scrollQuickReplies);
        ImageView btnSendChat = findViewById(R.id.btnSendChat);

        findViewById(R.id.btnChatBack).setOnClickListener(v -> finish());
        btnSendChat.setOnClickListener(v -> sendChatMessage());
        edtChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendChatMessage();
                return true;
            }
            return false;
        });

        addBotBubble("Xin chào! Mình là Trip Assistant. Bạn cần giúp gì cho chuyến đi sắp tới?");
        setupQuickReplies();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        chatHandler.removeCallbacksAndMessages(null);
        for (Animator animator : activeAnimators) animator.cancel();
        activeAnimators.clear();
    }

    private void sendChatMessage() {
        String question = edtChatInput.getText().toString().trim();
        if (question.isEmpty()) return;

        hideQuickReplies();
        addUserBubble(question);
        edtChatInput.setText("");

        // Demo reply only: always suggest the first tour from mock data. A "typing..." indicator
        // shows first so the reply doesn't feel like a hardcoded instant echo.
        View typingRow = addTypingIndicator();
        chatHandler.postDelayed(() -> {
            removeTypingIndicator(typingRow);
            addBotBubble("Đây là chuyến đi mình nghĩ bạn sẽ thích:");
            Tour suggestion = MockDataProvider.getMockTours().get(0);
            addTourSuggestionCard(suggestion);
        }, 1200);
    }

    /** Quick-reply chips shown only for the empty state (before the user's first message) -
     *  tapping one fills the input and sends it immediately, same as typing it out. */
    private void setupQuickReplies() {
        LinearLayout layoutQuickReplies = findViewById(R.id.layoutQuickReplies);
        int strokeColor = Color.parseColor("#2E7D32");

        for (String suggestion : QUICK_REPLIES) {
            Chip chip = new Chip(this);
            chip.setText(suggestion);
            chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.WHITE));
            chip.setChipStrokeColor(ColorStateList.valueOf(strokeColor));
            chip.setChipStrokeWidth(dp(1));
            chip.setTextColor(strokeColor);
            chip.setTextSize(13);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> {
                edtChatInput.setText(suggestion);
                sendChatMessage();
            });
            layoutQuickReplies.addView(chip);
        }
    }

    private void hideQuickReplies() {
        if (scrollQuickReplies.getVisibility() != View.VISIBLE) return;
        scrollQuickReplies.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> scrollQuickReplies.setVisibility(View.GONE)).start();
    }

    /** Bot bubble - avatar + bubble + timestamp row (item_chat_bot_message.xml), left-aligned. */
    private void addBotBubble(String text) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_chat_bot_message, layoutChatMessages, false);
        TextView bubble = row.findViewById(R.id.txtBotBubble);
        bubble.setText(text);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.7));
        ((TextView) row.findViewById(R.id.txtBotTimestamp)).setText(currentTime());

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
        lp.gravity = Gravity.START;
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);

        layoutChatMessages.addView(row);
        animateIn(row);
        scrollChatToBottom();
    }

    /** User bubble + timestamp, right-aligned, no avatar. */
    private void addUserBubble(String text) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(14);
        bubble.setTextColor(Color.WHITE);
        bubble.setBackgroundResource(R.drawable.bg_bubble_user);
        bubble.setElevation(dp(4));
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.7));
        bubble.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView timestamp = new TextView(this);
        timestamp.setText(currentTime());
        timestamp.setTextSize(10);
        timestamp.setTextColor(Color.parseColor("#9E9E9E"));
        LinearLayout.LayoutParams timestampLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timestampLp.gravity = Gravity.END;
        timestampLp.topMargin = dp(3);
        timestampLp.setMarginEnd(dp(4));
        timestamp.setLayoutParams(timestampLp);

        column.addView(bubble);
        column.addView(timestamp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.topMargin = dp(6);
        column.setLayoutParams(lp);

        layoutChatMessages.addView(column);
        animateIn(column);
        scrollChatToBottom();
    }

    /** "Bot is typing..." row with 3 bouncing dots (item_chat_typing.xml). Returns the row so the
     *  caller can remove it once the real reply is ready. */
    private View addTypingIndicator() {
        View row = LayoutInflater.from(this).inflate(R.layout.item_chat_typing, layoutChatMessages, false);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) row.getLayoutParams();
        lp.gravity = Gravity.START;
        lp.topMargin = dp(6);
        row.setLayoutParams(lp);

        layoutChatMessages.addView(row);
        animateIn(row);
        bounceDot(row.findViewById(R.id.dotTyping1), 0);
        bounceDot(row.findViewById(R.id.dotTyping2), 150);
        bounceDot(row.findViewById(R.id.dotTyping3), 300);
        scrollChatToBottom();
        return row;
    }

    private void removeTypingIndicator(View row) {
        layoutChatMessages.removeView(row);
    }

    private void bounceDot(View dot, long startDelay) {
        ObjectAnimator bounce = ObjectAnimator.ofFloat(dot, "translationY", 0f, -dp(4));
        bounce.setDuration(300);
        bounce.setStartDelay(startDelay);
        bounce.setRepeatMode(ValueAnimator.REVERSE);
        bounce.setRepeatCount(ValueAnimator.INFINITE);
        bounce.setInterpolator(new AccelerateDecelerateInterpolator());
        bounce.start();
        activeAnimators.add(bounce);
    }

    /** Suggested-tour card - reuses TourAdapter/item_tour so it looks and behaves exactly like a
     *  home-page tour card (tap to open detail, "+ Add this Tour", etc.). */
    private void addTourSuggestionCard(Tour tour) {
        RecyclerView cardHost = new RecyclerView(this);
        cardHost.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        cardHost.setLayoutManager(new LinearLayoutManager(this));
        cardHost.setNestedScrollingEnabled(false);
        cardHost.setAdapter(new TourAdapter(Collections.singletonList(tour), true));

        layoutChatMessages.addView(cardHost);
        animateIn(cardHost);
        scrollChatToBottom();
    }

    /** Short fade + rise-in entrance so new messages feel alive rather than snapping into place. */
    private void animateIn(View view) {
        view.setAlpha(0f);
        view.setTranslationY(dp(8));
        view.animate().alpha(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void scrollChatToBottom() {
        scrollChatMessages.post(() -> scrollChatMessages.fullScroll(View.FOCUS_DOWN));
    }

    private String currentTime() {
        return timeFormat.format(new Date());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
