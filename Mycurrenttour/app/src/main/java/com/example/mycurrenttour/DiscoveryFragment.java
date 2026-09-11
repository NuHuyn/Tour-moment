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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Calendar;
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
    private ImageView imgHeaderBg;
    private NestedScrollView scrollContent;
    private TextView txtWelcome, txtUserName;
    private EditText edtSearch;
    private RecyclerView recyclerTours, recyclerPopular;
    private ProgressBar progressDiscovery;
    private TourAdapter adapter;
    private PopularAdapter popularAdapter;
    private ChipGroup chipGroup;

    // Must match imgDiscoveryHeaderBg's height in fragment_discovery.xml: the header art fades
    // from fully visible at scrollY=0 to fully gone by this many dp of scroll (linear both ways -
    // scrolling back up brings it back), instead of either staying pinned under the scrolling
    // content or scrolling rigidly with it.
    private static final int HEADER_FADE_DISTANCE_DP = 320;

    private List<Tour> originalList = new ArrayList<>();
    // Bumped on every loadTours() call and captured per-request, same pattern as
    // MyTourFragment.loadRequestId - guards against a slower/older response landing after a
    // newer one (e.g. re-entering this tab fires another load before the previous one resolved)
    // and overwriting more current data.
    private int loadRequestId = 0;
    // Last search box text / selected chip category passed to filterTours (both funnel through
    // the same "query" string - see filterTours). Re-applied after every loadTours() reload so
    // returning to this tab (onResume now re-fetches) doesn't silently drop back to "All Tours"
    // while the search box text or a non-"All" chip still shows as active - the same
    // UI/data-desync bug as MyTourFragment's filter chips, just for search/region filtering here.
    private String currentQuery = "";

    // Cloud hint above the chatbot FAB: shows once per app process ("session"), not once per
    // visit to this tab - a static flag survives this Fragment being recreated on every tab
    // switch, but resets naturally when the app process is killed and relaunched.
    private static boolean cloudHintShownThisSession = false;
    private final Handler cloudHintHandler = new Handler(Looper.getMainLooper());
    private View cloudBubbleContainer;

    // Fed manually (not via BottomNavScrollHelper.attach()) since scrollContent already owns its
    // one OnScrollChangeListener slot for the header fade - see setupHeaderFade().
    private BottomNavScrollHelper.Tracker navScrollTracker;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discovery, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);

        view.findViewById(R.id.btnChatbotFab).setOnClickListener(v -> openChatbot());
        setupChatbotHint(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Was only called from onViewCreated(), i.e. once when the tab's view is first created -
        // switching to another tab and back reused the same (now stale) list instead of
        // re-fetching, e.g. a tour added from elsewhere wouldn't show up here until the app was
        // restarted. onResume() fires every time this tab becomes visible again, same pattern as
        // MyTourFragment.loadMyTours().
        loadTours();
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
        String[] cloudHints = getResources().getStringArray(R.array.chatbot_cloud_hints);
        txtCloudBubble.setText(cloudHints[new Random().nextInt(cloudHints.length)]);

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

    /** Fades imgDiscoveryHeaderBg out/in as scrollContent scrolls, instead of leaving it either
     *  pinned in place (transparent scrolled-over content lets it bleed through) or scrolling
     *  rigidly with the content column (needs an opaque backing to hide that same bleed-through,
     *  which reads as a hard edge). Linear both directions - NestedScrollView.OnScrollChangeListener
     *  fires on every scroll delta, up or down, so scrolling back up smoothly brings it back. */
    private void setupHeaderFade() {
        if (getContext() == null) return;
        float fadeDistancePx = HEADER_FADE_DISTANCE_DP * getResources().getDisplayMetrics().density;
        navScrollTracker = new BottomNavScrollHelper.Tracker((HomeActivity) requireActivity());

        scrollContent.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            float alpha = 1f - (scrollY / fadeDistancePx);
            imgHeaderBg.setAlpha(Math.max(0f, Math.min(1f, alpha)));

            if (scrollY <= 0) ((HomeActivity) requireActivity()).showBottomNav();
            navScrollTracker.onScrolled(scrollY - oldScrollY);
        });
    }

    private void initViews(View root) {
        iconProfile = root.findViewById(R.id.imgProfile);
        imgHeaderBg = root.findViewById(R.id.imgDiscoveryHeaderBg);
        scrollContent = root.findViewById(R.id.scrollDiscoveryContent);
        txtWelcome = root.findViewById(R.id.txtWelcome);
        txtUserName = root.findViewById(R.id.txtUserName);
        edtSearch = root.findViewById(R.id.edtSearch);
        chipGroup = root.findViewById(R.id.chipGroup);
        recyclerPopular = root.findViewById(R.id.recyclerPopular);
        recyclerTours = root.findViewById(R.id.recyclerTours);
        progressDiscovery = root.findViewById(R.id.progressDiscovery);

        setupHeaderFade();

        recyclerPopular.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        // Full-width vertical list (was a 2-column grid, which squished item_tour_discovery.xml's
        // image-left/info-right row into half-width and made its text overlap/wrap).
        recyclerTours.setLayoutManager(new LinearLayoutManager(getContext()));
        // The whole screen scrolls as one NestedScrollView (see fragment_discovery.xml) -
        // without this, recyclerTours would grab vertical touch/fling events for itself as soon
        // as a swipe starts over "All Tours", instead of letting them bubble up to that outer
        // scroll container.
        recyclerTours.setNestedScrollingEnabled(false);

        adapter = new TourAdapter(new ArrayList<>(), true);
        recyclerTours.setAdapter(adapter);

        displayGreeting();
        displayAvatar();
        iconProfile.setOnClickListener(v -> showLogoutDialog());

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString();
                filterTours(currentQuery);
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentQuery = "";
            } else {
                Chip chip = root.findViewById(checkedIds.get(0));
                String category = chip.getText().toString();
                currentQuery = category.equals(getString(R.string.chip_all)) ? "" : category;
            }
            filterTours(currentQuery);
        });
    }

    /** Time-of-day greeting ("Chào buổi sáng," 5h-11h / "Chào buổi trưa," 11h-13h / "Chào buổi
     *  chiều," 13h-18h / "Chào buổi tối," 18h-5h) + bold display name, taken from the same
     *  SessionManager the Profile tab uses (Guest vs local Google Sign-In result). */
    private void displayGreeting() {
        if (getContext() == null) return;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 5 && hour < 11) greeting = getString(R.string.greeting_morning);
        else if (hour >= 11 && hour < 13) greeting = getString(R.string.greeting_midday);
        else if (hour >= 13 && hour < 18) greeting = getString(R.string.greeting_afternoon);
        else greeting = getString(R.string.greeting_evening);
        txtWelcome.setText(greeting);

        SessionManager.SignInType signInType = SessionManager.getSignInType(getContext());
        String name = signInType == SessionManager.SignInType.GOOGLE
                ? SessionManager.getDisplayName(getContext())
                : null;
        String fallback = getString(signInType == SessionManager.SignInType.GOOGLE
                ? R.string.default_name_traveler : R.string.default_name_guest);
        txtUserName.setText((name != null ? name : fallback) + "!");
    }

    /** Avatar: Guest -> avt_guest, Google -> the real photo from the (local-only) Google
     *  Sign-In result saved at login - exact same SessionManager mechanism as ProfileFragment,
     *  not reimplemented here. */
    private void displayAvatar() {
        if (getContext() == null) return;

        if (SessionManager.getSignInType(getContext()) == SessionManager.SignInType.GOOGLE) {
            Glide.with(this)
                    .load(SessionManager.getPhotoUrl(getContext()))
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(iconProfile);
        } else {
            Glide.with(this)
                    .load(R.drawable.avt_guest)
                    .circleCrop()
                    .into(iconProfile);
        }
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
            filterTours(currentQuery);

            List<Tour> popularList = originalList.subList(0, Math.min(originalList.size(), 4));
            popularAdapter = new PopularAdapter(getContext(), popularList);
            recyclerPopular.setAdapter(popularAdapter);
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        // Makes the wait visible instead of the screen sitting static, most noticeably during a
        // Cloud Run cold start (first request after idle can take a couple seconds).
        if (progressDiscovery != null) progressDiscovery.setVisibility(View.VISIBLE);

        final int requestId = ++loadRequestId;

        apiService.getSharedTours(DeviceIdProvider.getOrCreate(requireContext())).enqueue(new Callback<List<Tour>>() {
            @Override
            public void onResponse(Call<List<Tour>> call, Response<List<Tour>> response) {
                if (!isAdded()) return;
                if (requestId != loadRequestId) return; // superseded by a newer loadTours() call
                if (progressDiscovery != null) progressDiscovery.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    originalList = response.body();
                    filterTours(currentQuery);

                    List<Tour> popularList = originalList.subList(0, Math.min(originalList.size(), 4));
                    popularAdapter = new PopularAdapter(getContext(), popularList);
                    recyclerPopular.setAdapter(popularAdapter);
                }
            }
            @Override
            public void onFailure(Call<List<Tour>> call, Throwable t) {
                if (!isAdded()) return;
                if (requestId != loadRequestId) return;
                if (progressDiscovery != null) progressDiscovery.setVisibility(View.GONE);
                Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLogoutDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.dialog_yes, (d, w) -> {
                    SessionManager.clear(getContext());
                    startActivity(new Intent(getContext(), LoginActivity.class));
                    requireActivity().finish();
                })
                .setNegativeButton(R.string.dialog_no, null).show();
    }
}
