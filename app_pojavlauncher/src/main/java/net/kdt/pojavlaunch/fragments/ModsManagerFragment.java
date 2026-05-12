package net.kdt.pojavlaunch.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.witherlauncher.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.InstalledModAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModInstallApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ModsManagerFragment extends Fragment
        implements ModItemAdapter.SearchResultCallback, InstalledModAdapter.DeleteListener {

    public static final String TAG = "ModsManagerFragment";
    public static final String EXTRA_INSTANCE_ROOT = "instance_root";

    private static final int TAB_BROWSE    = 0;
    private static final int TAB_INSTALLED = 1;

    private int mCurrentTab = TAB_BROWSE;

    private View mOverlay;
    private float mOverlayTopCache;

    private final RecyclerView.OnScrollListener mOverlayScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (mCurrentTab == TAB_BROWSE) {
                mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
            }
        }
    };

    private Button mBrowseTab;
    private Button mInstalledTab;
    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private ProgressBar mSearchProgressBar;

    private RecyclerView mBrowseList;
    private TextView mBrowseStatusText;
    private ColorStateList mDefaultTextColor;

    private RecyclerView mInstalledList;
    private TextView mInstalledEmptyText;

    private ModItemAdapter mModItemAdapter;
    private InstalledModAdapter mInstalledModAdapter;

    private File mModsDir;
    private final SearchFilters mSearchFilters;

    public ModsManagerFragment() {
        super(R.layout.fragment_mods_manager);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = requireArguments();
        String instanceRootPath = args.getString(EXTRA_INSTANCE_ROOT);
        Instance instance = Instances.loadFromRoot(new File(instanceRootPath));

        if (instance == null) {
            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }

        mModsDir = new File(instance.getGameDirectory(), "mods");

        ModInstallApi modInstallApi = new ModInstallApi(instance.getGameDirectory());
        mModItemAdapter = new ModItemAdapter(getResources(), modInstallApi, this);
        mInstalledModAdapter = new InstalledModAdapter(scanInstalledMods(), this);

        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay           = view.findViewById(R.id.mods_overlay);
        mBrowseTab         = view.findViewById(R.id.mods_tab_browse);
        mInstalledTab      = view.findViewById(R.id.mods_tab_installed);
        mSearchEditText    = view.findViewById(R.id.mods_search_edittext);
        mFilterButton      = view.findViewById(R.id.mods_filter_button);
        mSearchProgressBar = view.findViewById(R.id.mods_progressbar);
        mBrowseList        = view.findViewById(R.id.mods_list);
        mBrowseStatusText  = view.findViewById(R.id.mods_status_text);
        mInstalledList     = view.findViewById(R.id.mods_installed_list);
        mInstalledEmptyText = view.findViewById(R.id.mods_installed_empty);

        TextView titleView = view.findViewById(R.id.mods_title);
        String instanceName = Tools.validOrNullString(instance.name);
        if (instanceName == null) instanceName = Tools.validOrNullString(instance.versionId);
        if (instanceName == null) instanceName = "Instance";
        titleView.setText(getString(R.string.mods_for_instance, instanceName));

        mDefaultTextColor = mBrowseStatusText.getTextColors();

        mBrowseList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBrowseList.setAdapter(mModItemAdapter);
        mBrowseList.addOnScrollListener(mOverlayScrollListener);

        mInstalledList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mInstalledList.setAdapter(mInstalledModAdapter);
        mInstalledList.addOnScrollListener(mOverlayScrollListener);

        mOverlay.post(() -> {
            int h = mOverlay.getHeight();
            int pl = mBrowseList.getPaddingLeft();
            int pr = mBrowseList.getPaddingRight();
            int pb = mBrowseList.getPaddingBottom();
            int pt = mBrowseList.getPaddingTop();
            mBrowseList.setPadding(pl, pt + h, pr, pb);
            mInstalledList.setPadding(pl, pt + h, pr, pb);
        });

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mFilterButton.setOnClickListener(v -> showFilterDialog());

        mBrowseTab.setOnClickListener(v -> switchTab(TAB_BROWSE));
        mInstalledTab.setOnClickListener(v -> switchTab(TAB_INSTALLED));

        applyTabStyle(TAB_BROWSE);
        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mBrowseList.removeOnScrollListener(mOverlayScrollListener);
        mInstalledList.removeOnScrollListener(mOverlayScrollListener);
    }

    private void switchTab(int tab) {
        if (mCurrentTab == tab) return;
        mCurrentTab = tab;
        applyTabStyle(tab);

        mOverlay.setY(mOverlayTopCache);

        if (tab == TAB_BROWSE) {
            mSearchEditText.setVisibility(View.VISIBLE);
            mFilterButton.setVisibility(View.VISIBLE);
            mBrowseList.setVisibility(View.VISIBLE);
            mBrowseStatusText.setVisibility(mBrowseStatusText.getText().length() > 0 ? View.VISIBLE : View.GONE);
            mInstalledList.setVisibility(View.GONE);
            mInstalledEmptyText.setVisibility(View.GONE);
        } else {
            mSearchEditText.setVisibility(View.GONE);
            mFilterButton.setVisibility(View.GONE);
            mSearchProgressBar.setVisibility(View.GONE);
            mBrowseList.setVisibility(View.GONE);
            mBrowseStatusText.setVisibility(View.GONE);
            refreshInstalledList();
            mInstalledList.setVisibility(View.VISIBLE);
        }
    }

    private void applyTabStyle(int activeTab) {
        float activeAlpha   = 1.0f;
        float inactiveAlpha = 0.45f;
        mBrowseTab.setAlpha(activeTab == TAB_BROWSE ? activeAlpha : inactiveAlpha);
        mInstalledTab.setAlpha(activeTab == TAB_INSTALLED ? activeAlpha : inactiveAlpha);
    }

    private void refreshInstalledList() {
        List<File> mods = scanInstalledMods();
        mInstalledModAdapter.refresh(mods);
        mInstalledEmptyText.setVisibility(mods.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<File> scanInstalledMods() {
        List<File> result = new ArrayList<>();
        if (!mModsDir.exists()) return result;
        File[] files = mModsDir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            String name = f.getName().toLowerCase();
            if (name.endsWith(".jar") || name.endsWith(".jar.disabled")) {
                result.add(f);
            }
        }
        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    @Override
    public void onDeleteRequested(int position, File modFile, String displayName) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mods_delete_confirm_title)
                .setMessage(getString(R.string.mods_delete_confirm_message, displayName))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (modFile.delete()) {
                        mInstalledModAdapter.removeAt(position);
                        if (mInstalledModAdapter.isEmpty()) {
                            mInstalledEmptyText.setVisibility(View.VISIBLE);
                        }
                    } else {
                        Toast.makeText(requireContext(),
                                R.string.mods_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mBrowseStatusText.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mBrowseStatusText.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mBrowseStatusText.setTextColor(Color.RED);
                mBrowseStatusText.setText(R.string.mods_search_error);
                break;
            case ERROR_NO_RESULTS:
                mBrowseStatusText.setTextColor(mDefaultTextColor);
                mBrowseStatusText.setText(R.string.mods_search_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mBrowseStatusText.setVisibility(View.GONE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void showFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        dialog.setOnShowListener(di -> {
            TextView selectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button selectVersionBtn  = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button applyBtn          = dialog.findViewById(R.id.search_mod_apply_filters);

            assert selectedVersion != null;
            assert selectVersionBtn != null;
            assert applyBtn != null;

            selectedVersion.setText(mSearchFilters.mcVersion);

            selectVersionBtn.setOnClickListener(v ->
                    VersionSelectorDialog.open(v.getContext(), true,
                            (id, snapshot) -> selectedVersion.setText(id)));

            applyBtn.setOnClickListener(v -> {
                mSearchFilters.mcVersion = selectedVersion.getText().toString();
                searchMods(mSearchEditText.getText().toString());
                di.dismiss();
            });
        });

        dialog.show();
    }
}
