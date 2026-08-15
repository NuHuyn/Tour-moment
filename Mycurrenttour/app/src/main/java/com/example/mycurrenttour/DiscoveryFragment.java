package com.example.mycurrenttour;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Discovery" tab (nav_explore) - was HomeActivity. Moved into a Fragment hosted by
 * HomeActivity's fragmentContainer so the bottom nav bar stays fixed across tabs.
 *
 * The floating chatbot icon (bottom-right, see fragment_discovery.xml) lives only in this
 * fragment's layout, so it's only ever visible on this tab. Tapping it pushes the full-screen
 * ChatbotActivity - it is NOT an overlay/dialog, so it doesn't need to be torn down on tab switch.
 */
public class DiscoveryFragment extends Fragment {

    private ImageView iconProfile;
    private EditText edtSearch;
    private RecyclerView recyclerTours, recyclerPopular;
    private TourAdapter adapter;
    private PopularAdapter popularAdapter;
    private ChipGroup chipGroup;

    private List<Tour> originalList = new ArrayList<>();

    // Cloud hint above the chatbot FAB: shows once per app process ("session"), not once per
    // visit to this tab - a static flag survives this Fragment being recreated on every tab
    // switch, but resets naturally when the app process is killed and relaunched.
    private static boolean cloudHintShownThisSession = false;
    private static final String[] CLOUD_HINTS = {
            "Tôi có thể giúp gì cho bạn?",
            "Bạn muốn đi đâu hôm nay?",
            "Hỏi tôi về chuyến đi nhé!"
    };
    private final Handler cloudHintHandler = new Handler(Looper.getMainLooper());
    private View cloudBubbleContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);
        loadTours();

        view.findViewById(R.id.btnChatbotFab).setOnClickListener(v -> openChatbot());
        setupChatbotHint(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cloudHintHandler.removeCallbacksAndMessages(null);
    }

    private void openChatbot() {
        hideCloudHint();
        startActivity(new Intent(getContext(), ChatbotActivity.class));
    }

    /** One-time-per-session hint bubble that draws attention to the chatbot FAB (see
     *  cloudBubbleContainer in fragment_discovery.xml). Auto-hides after 4s; tapping it (or the
     *  FAB) opens the chat immediately and dismisses it right away. */
    private void setupChatbotHint(View root) {
        cloudBubbleContainer = root.findViewById(R.id.cloudBubbleContainer);
        cloudBubbleContainer.setOnClickListener(v -> openChatbot());

        if (cloudHintShownThisSession) return;
        cloudHintShownThisSession = true;

        TextView txtCloudBubble = root.findViewById(R.id.txtCloudBubble);
        txtCloudBubble.setText(CLOUD_HINTS[new Random().nextInt(CLOUD_HINTS.length)]);

        cloudHintHandler.postDelayed(() -> {
            if (cloudBubbleContainer == null) return;
            cloudBubbleContainer.setVisibility(View.VISIBLE);
            cloudBubbleContainer.animate().alpha(1f).setDuration(200).start();
        }, 500);

        cloudHintHandler.postDelayed(this::hideCloudHint, 4500);
    }

    private void hideCloudHint() {
        if (cloudBubbleContainer == null || cloudBubbleContainer.getVisibility() != View.VISIBLE) return;
        cloudBubbleContainer.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> {
                    if (cloudBubbleContainer != null) cloudBubbleContainer.setVisibility(View.GONE);
                }).start();
    }

    private void initViews(View root) {
        iconProfile = root.findViewById(R.id.imgProfile);
        edtSearch = root.findViewById(R.id.edtSearch);
        chipGroup = root.findViewById(R.id.chipGroup);
        recyclerPopular = root.findViewById(R.id.recyclerPopular);
        recyclerTours = root.findViewById(R.id.recyclerTours);

        recyclerPopular.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerTours.setLayoutManager(new GridLayoutManager(getContext(), 2));

        adapter = new TourAdapter(new ArrayList<>(), true);
        recyclerTours.setAdapter(adapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getPhotoUrl() != null) {
            Picasso.get().load(user.getPhotoUrl()).into(iconProfile);
        }
        iconProfile.setOnClickListener(v -> showLogoutDialog());

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTours(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filterTours("");
            } else {
                Chip chip = root.findViewById(checkedIds.get(0));
                String category = chip.getText().toString();
                if (category.equals("Tất cả")) filterTours("");
                else filterTours(category);
            }
        });
    }

    private void filterTours(String query) {
        if (originalList == null || adapter == null) return;

        if (query.isEmpty()) {
            adapter.updateList(originalList);
            return;
        }

        List<Tour> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (Tour t : originalList) {
            boolean isMatch = false;
            if (t.getTitle() != null && t.getTitle().toLowerCase().contains(lowerQuery)) isMatch = true;

            if (!isMatch && t.getWaypoints() != null) {
                for (Tour.Waypoint wp : t.getWaypoints()) {
                    if (wp.getLocationName() != null && wp.getLocationName().toLowerCase().contains(lowerQuery)) {
                        isMatch = true;
                        break;
                    }
                }
            }
            if (isMatch) filtered.add(t);
        }
        adapter.updateList(filtered);
    }

    private void loadTours() {
        // TODO: set USE_MOCK_DATA = false khi backend sẵn sàng
        if (MockDataProvider.USE_MOCK_DATA) {
            originalList = MockDataProvider.getMockTours();
            adapter.updateList(originalList);

            List<Tour> popularList = originalList.subList(0, Math.min(originalList.size(), 4));
            popularAdapter = new PopularAdapter(getContext(), popularList);
            recyclerPopular.setAdapter(popularAdapter);
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        apiService.getSharedTours().enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    originalList = response.body();
                    adapter.updateList(originalList);

                    List<Tour> popularList = originalList.subList(0, Math.min(originalList.size(), 4));
                    popularAdapter = new PopularAdapter(getContext(), popularList);
                    recyclerPopular.setAdapter(popularAdapter);
                }
            }
            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Network Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle("Log out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    startActivity(new Intent(getContext(), LoginActivity.class));
                    requireActivity().finish();
                })
                .setNegativeButton("No", null).show();
    }
}
