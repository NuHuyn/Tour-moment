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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Full-screen "Trip Assistant" chat - pushed from the floating chatbot icon on the Discovery tab
 * only (see DiscoveryFragment). Own Activity like any other detail screen, so it has no bottom
 * nav and back returns to Discovery.
 *
 * Demo reply only (no AI/RAG backend yet - chưa có DeepSeek+MongoDB): always suggests the first
 * mock tour. TODO: swap replyWithMockSuggestion() for a real API call once the backend is ready.
 */
public class ChatbotActivity extends AppCompatActivity {

    private LinearLayout layoutChatMessages;
    private ScrollView scrollChatMessages;
    private EditText edtChatInput;
    // Quick-reply chip row - lives inline in layoutChatMessages (added right after the greeting
    // bubble), not a fixed bottom bar. Tracked here so hideQuickReplies() can remove it once the
    // user sends their first message.
    private View quickRepliesRow;
    private final Handler chatHandler = new Handler(Looper.getMainLooper());
    // Locale-aware time format (12h/24h per the device's own format setting) instead of a
    // hardcoded "HH:mm" pattern - resolved in onCreate() since it needs a Context.
    private java.text.DateFormat timeFormat;
    // Typing-dot bounce animators run on an infinite repeat, so they must be tracked and cancelled
    // explicitly - onDestroy() won't stop them on its own.
    private final List<Animator> activeAnimators = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);
        timeFormat = android.text.format.DateFormat.getTimeFormat(this);

        layoutChatMessages = findViewById(R.id.layoutChatMessages);
        scrollChatMessages = findViewById(R.id.scrollChatMessages);
        edtChatInput = findViewById(R.id.edtChatInput);
        ImageView btnSendChat = findViewById(R.id.btnSendChat);

        findViewById(R.id.btnChatBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnChatMenu).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.coming_soon_format, getString(R.string.feature_menu)), Toast.LENGTH_SHORT).show());
        btnSendChat.setOnClickListener(v -> sendChatMessage());
        edtChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                sendChatMessage();
                return true;
            }
            return false;
        });

        addBotBubble(getString(R.string.chatbot_greeting));
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
            addBotBubble(getString(R.string.chatbot_suggestion_intro));
            Tour suggestion = MockDataProvider.getMockTours().get(0);
            addTourSuggestionCard(suggestion);
        }, 1200);
    }

    /** Quick-reply chips shown only for the empty state (before the user's first message) -
     *  added inline into layoutChatMessages right under the greeting bubble, so they scroll as
     *  part of the conversation rather than sitting pinned above the input bar. Stacked one per
     *  row, full width, in QUICK_REPLIES order - no horizontal scrolling/clipping. Tapping one
     *  fills the input and sends it immediately, same as typing it out. */
    private void setupQuickReplies() {
        LinearLayout chipColumn = new LinearLayout(this);
        chipColumn.setOrientation(LinearLayout.VERTICAL);
        chipColumn.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int strokeColor = Color.parseColor("#2E7D32");
        for (String suggestion : getResources().getStringArray(R.array.quick_replies)) {
            Chip chip = new Chip(this);
            chip.setText(suggestion);
            chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.WHITE));
            chip.setChipStrokeColor(ColorStateList.valueOf(strokeColor));
            chip.setChipStrokeWidth(dp(1));
            chip.setTextColor(strokeColor);
            chip.setTextSize(14);
            chip.setEnsureMinTouchTargetSize(false);
            // Comfortable breathing room around the label - ChipDrawable's own padding fields,
            // not View.setPadding(), since Chip renders its background/text via a single
            // ChipDrawable rather than a normal View background. chipMinHeight is what actually
            // grows the pill vertically (Chip has no separate top/bottom padding field - height
            // above the min the text needs is centered automatically).
            chip.setChipStartPadding(dp(18));
            chip.setChipEndPadding(dp(18));
            chip.setChipMinHeight(dp(46));
            // Oversized on purpose - Material's shape system clamps corner size to half the
            // chip's actual (shorter) side, so this guarantees a full pill/capsule regardless of
            // chipMinHeight above, instead of hardcoding a radius that only happens to match it.
            chip.setChipCornerRadius(dp(100));

            // WRAP_CONTENT (not MATCH_PARENT) so each chip sizes to its own label instead of
            // stretching into a full-width bar; chipColumn is a vertical LinearLayout, whose
            // children left-align by default, matching the target design's compact left-aligned
            // stack of pills.
            LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipLp.gravity = Gravity.START;
            chipLp.topMargin = dp(10);
            chip.setLayoutParams(chipLp);

            chip.setOnClickListener(v -> {
                edtChatInput.setText(suggestion);
                sendChatMessage();
            });
            chipColumn.addView(chip);
        }

        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chipColumn.getLayoutParams();
        lp.gravity = Gravity.START;
        lp.topMargin = dp(4);
        chipColumn.setLayoutParams(lp);

        quickRepliesRow = chipColumn;
        layoutChatMessages.addView(quickRepliesRow);
        animateIn(quickRepliesRow);
        scrollChatToBottom();
    }

    private void hideQuickReplies() {
        if (quickRepliesRow == null || quickRepliesRow.getParent() == null) return;
        View row = quickRepliesRow;
        quickRepliesRow = null;
        row.animate().alpha(0f).setDuration(150)
                .withEndAction(() -> layoutChatMessages.removeView(row)).start();
    }

    /** Bot bubble - text + timestamp inside one rounded container (item_chat_bot_message.xml),
     *  no avatar, left-aligned. */
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
