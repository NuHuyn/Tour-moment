package com.example.mycurrenttour;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * "Viết đánh giá" form - 5-star tap-to-rate + comment. Used for both creating a new review and
 * editing the caller's existing one; TourDetailActivity decides create-vs-edit (by checking
 * whether the signed-in user already has a review in the list it already fetched) and passes the
 * existing rating/comment as prefill via newInstance() - this sheet itself has no create/edit
 * branching, it just reports the chosen rating+comment back through Listener.onSubmit().
 */
public class ReviewFormBottomSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onSubmit(int rating, String comment);
    }

    private static final String ARG_RATING = "initial_rating";
    private static final String ARG_COMMENT = "initial_comment";

    private int selectedRating;
    private final List<ImageView> starViews = new ArrayList<>();
    private Listener listener;

    public static ReviewFormBottomSheet newInstance(int initialRating, String initialComment) {
        ReviewFormBottomSheet sheet = new ReviewFormBottomSheet();
        Bundle args = new Bundle();
        args.putInt(ARG_RATING, initialRating);
        args.putString(ARG_COMMENT, initialComment != null ? initialComment : "");
        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_review_form, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int initialRating = getArguments() != null ? getArguments().getInt(ARG_RATING, 0) : 0;
        String initialComment = getArguments() != null ? getArguments().getString(ARG_COMMENT, "") : "";
        selectedRating = initialRating;

        LinearLayout starPicker = view.findViewById(R.id.layoutStarPicker);
        int starSizePx = dp(32);
        for (int i = 1; i <= 5; i++) {
            ImageView star = new ImageView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(starSizePx, starSizePx);
            lp.setMarginEnd(dp(4));
            star.setLayoutParams(lp);
            star.setPadding(dp(2), dp(2), dp(2), dp(2));
            final int starIndex = i;
            star.setOnClickListener(v -> setSelectedRating(starIndex));
            starPicker.addView(star);
            starViews.add(star);
        }
        updateStarDisplay();

        TextInputEditText edtComment = view.findViewById(R.id.edtReviewComment);
        edtComment.setText(initialComment);

        MaterialButton btnSubmit = view.findViewById(R.id.btnSubmitReview);
        btnSubmit.setOnClickListener(v -> {
            if (selectedRating == 0) {
                Toast.makeText(requireContext(), R.string.error_select_rating, Toast.LENGTH_SHORT).show();
                return;
            }
            String comment = edtComment.getText() != null ? edtComment.getText().toString().trim() : "";
            if (listener != null) listener.onSubmit(selectedRating, comment);
            dismiss();
        });
    }

    private void setSelectedRating(int rating) {
        selectedRating = rating;
        updateStarDisplay();
    }

    private void updateStarDisplay() {
        for (int i = 0; i < starViews.size(); i++) {
            starViews.get(i).setImageResource(i < selectedRating ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
