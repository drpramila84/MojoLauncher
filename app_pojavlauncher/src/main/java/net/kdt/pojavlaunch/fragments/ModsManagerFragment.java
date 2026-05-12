package net.kdt.pojavlaunch.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private TextView mVersionFilterChip;

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
        mVersionFilterChip = view.findViewById(R.id.mods_version_chip);
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
        mVersionFilterChip.setOnClickListener(v -> showFilterDialog());

        mBrowseTab.setOnClickListener(v -> switchTab(TAB_BROWSE));
        mInstalledTab.setOnClickListener(v -> switchTab(TAB_INSTALLED));

        applyTabStyle(TAB_BROWSE);

        // Auto-detect MC version (and loader) from the instance's versionId so the
        // user does not have to pick it manually. Fall back to the picker if unknown.
        String detectedMcVersion = extractMcVersion(instance.versionId);
        if (detectedMcVersion != null) {
            mSearchFilters.mcVersion = detectedMcVersion;
            String detectedLoader = extractLoader(instance.versionId);
            if (detectedLoader != null) mSearchFilters.modLoader = detectedLoader;
            updateVersionChip();
            searchMods(null);
        } else {
            updateVersionChip();
            showVersionPickerPrompt();
        }
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

    /**
     * Opens the version picker immediately when the Browse tab loads.
     * Mods are only fetched after the user selects a version, preventing
     * incompatible mod installations.
     */
    private void showVersionPickerPrompt() {
        mBrowseStatusText.setTextColor(mDefaultTextColor);
        mBrowseStatusText.setText(R.string.mods_select_version_prompt);
        mBrowseStatusText.setVisibility(View.VISIBLE);
        VersionSelectorDialog.open(requireContext(), true, (version, snapshot) -> {
            mSearchFilters.mcVersion = version;
            updateVersionChip();
            searchMods(mSearchEditText.getText().toString());
        });
    }

    /** Updates the filter chip text to reflect the current version + loader selection. */
    private void updateVersionChip() {
        StringBuilder sb = new StringBuilder();
        if (mSearchFilters.mcVersion != null && !mSearchFilters.mcVersion.isEmpty()) {
            sb.append("MC ").append(mSearchFilters.mcVersion);
        }
        if (mSearchFilters.modLoader != null && !mSearchFilters.modLoader.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            String loader = mSearchFilters.modLoader;
            sb.append(Character.toUpperCase(loader.charAt(0))).append(loader.substring(1));
        }
        if (sb.length() == 0) {
            mVersionFilterChip.setText(R.string.mods_version_chip_hint);
            mVersionFilterChip.setAlpha(0.55f);
        } else {
            mVersionFilterChip.setText(sb.toString());
            mVersionFilterChip.setAlpha(1.0f);
        }
    }

    private void showFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        dialog.setOnShowListener(di -> {
            TextView selectedVersion  = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button selectVersionBtn   = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button applyBtn           = dialog.findViewById(R.id.search_mod_apply_filters);
            RadioGroup loaderGroup    = dialog.findViewById(R.id.search_mod_loader_radio_group);

            assert selectedVersion != null;
            assert selectVersionBtn != null;
            assert applyBtn != null;
            assert loaderGroup != null;

            selectedVersion.setText(mSearchFilters.mcVersion);
            loaderGroup.check(loaderSlugToRadioId(mSearchFilters.modLoader));

            selectVersionBtn.setOnClickListener(v ->
                    VersionSelectorDialog.open(v.getContext(), true,
                            (id, snapshot) -> selectedVersion.setText(id)));

            applyBtn.setOnClickListener(v -> {
                mSearchFilters.mcVersion = selectedVersion.getText().toString();
                mSearchFilters.modLoader = radioIdToLoaderSlug(loaderGroup.getCheckedRadioButtonId());
                updateVersionChip();
                searchMods(mSearchEditText.getText().toString());
                di.dismiss();
            });
        });

        dialog.show();
    }

    private int loaderSlugToRadioId(String slug) {
        if (slug == null) return R.id.search_mod_loader_any;
        switch (slug) {
            case "fabric":   return R.id.search_mod_loader_fabric;
            case "forge":    return R.id.search_mod_loader_forge;
            case "quilt":    return R.id.search_mod_loader_quilt;
            case "neoforge": return R.id.search_mod_loader_neoforge;
            default:         return R.id.search_mod_loader_any;
        }
    }

    private String radioIdToLoaderSlug(int radioId) {
        if (radioId == R.id.search_mod_loader_fabric)   return "fabric";
        if (radioId == R.id.search_mod_loader_forge)    return "forge";
        if (radioId == R.id.search_mod_loader_quilt)    return "quilt";
        if (radioId == R.id.search_mod_loader_neoforge) return "neoforge";
        return null;
    }

    /**
     * Extracts a plain Minecraft version string from a raw versionId, handling every
     * modloader format the launcher produces:
     *
     *   vanilla          : "1.21.1"                          → "1.21.1"
     *   Fabric/Quilt     : "fabric-loader-0.19.2-1.21.11"   → "1.21.11"  (last segment)
     *   Legacy Fabric    : "legacy-fabric-loader-0.12-1.16.5"→ "1.16.5"  (last segment)
     *   Forge            : "1.21.1-forge-47.3.0"            → "1.21.1"   (before -forge-)
     *   NeoForge         : "neoforge-21.1.8"                → "1.21.1"   (NeoForge scheme)
     *
     * Returns null if no recognisable MC version can be determined.
     */
    private static String extractMcVersion(String versionId) {
        if (versionId == null || versionId.isEmpty()) return null;
        String lower = versionId.toLowerCase(java.util.Locale.ROOT);

        // fabric-loader-X.Y.Z-MC  /  quilt-loader-X.Y.Z-MC  /  legacy-fabric-loader-X.Y.Z-MC
        // The MC version is always the LAST dash-delimited segment.
        if (lower.contains("-loader-")) {
            int lastDash = versionId.lastIndexOf('-');
            if (lastDash >= 0 && lastDash < versionId.length() - 1) {
                String candidate = versionId.substring(lastDash + 1);
                if (candidate.matches("\\d+\\.\\d+(?:\\.\\d+)?")) return candidate;
            }
        }

        // {mcVersion}-forge-{loaderVersion}  →  MC is everything before "-forge-"
        if (lower.contains("-forge-")) {
            String[] parts = versionId.split("-forge-", 2);
            if (parts[0].matches("\\d+\\.\\d+(?:\\.\\d+)?")) return parts[0];
        }

        // neoforge-{loaderVersion}  where loaderVersion encodes MC as:
        //   21.1.8 → 1.21.1  (trim leading minor, keep up to the second dot)
        if (lower.startsWith("neoforge-")) {
            String neoVer = versionId.substring("neoforge-".length());
            int firstDot  = neoVer.indexOf('.');
            int secondDot = firstDot >= 0 ? neoVer.indexOf('.', firstDot + 1) : -1;
            if (firstDot >= 0 && secondDot >= 0) {
                return "1." + neoVer.substring(0, secondDot);
            }
        }

        // Vanilla plain version ("1.21.1", "1.12.2", etc.)
        if (versionId.matches("\\d+\\.\\d+(?:\\.\\d+)?")) return versionId;

        // Fallback: grab the first X.Y or X.Y.Z pattern
        Matcher m = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)").matcher(versionId);
        if (m.find()) return m.group(1);

        return null;
    }

    /**
     * Detects the modloader slug from a raw versionId string.
     * Returns "fabric", "forge", "quilt", "neoforge", or null for vanilla.
     */
    private static String extractLoader(String versionId) {
        if (versionId == null) return null;
        String lower = versionId.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("neoforge")) return "neoforge";
        if (lower.contains("forge"))    return "forge";
        if (lower.contains("fabric"))   return "fabric";
        if (lower.contains("quilt"))    return "quilt";
        return null;
    }
}
