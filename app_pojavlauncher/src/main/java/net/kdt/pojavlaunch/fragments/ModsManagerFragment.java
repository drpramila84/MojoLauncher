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
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModInstallApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.io.File;

public class ModsManagerFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "ModsManagerFragment";
    public static final String EXTRA_INSTANCE_ROOT = "instance_root";

    private View mOverlay;
    private float mOverlayTopCache;

    private final RecyclerView.OnScrollListener mOverlayScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerView;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;
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

        // Auto-filter by the instance's Minecraft version
        String versionId = instance.versionId;
        if (versionId != null
                && !Instance.VERSION_LATEST_RELEASE.equals(versionId)
                && !Instance.VERSION_LATEST_SNAPSHOT.equals(versionId)) {
            mSearchFilters.mcVersion = versionId;
        }

        ModInstallApi modInstallApi = new ModInstallApi(instance.getGameDirectory());
        mModItemAdapter = new ModItemAdapter(getResources(), modInstallApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay            = view.findViewById(R.id.mods_overlay);
        mSearchEditText     = view.findViewById(R.id.mods_search_edittext);
        mSearchProgressBar  = view.findViewById(R.id.mods_progressbar);
        mRecyclerView       = view.findViewById(R.id.mods_list);
        mStatusTextView     = view.findViewById(R.id.mods_status_text);
        mFilterButton       = view.findViewById(R.id.mods_filter_button);

        // Show instance name in header
        TextView titleView = view.findViewById(R.id.mods_title);
        String instanceName = Tools.validOrNullString(instance.name);
        if (instanceName == null) instanceName = Tools.validOrNullString(instance.versionId);
        if (instanceName == null) instanceName = "Instance";
        titleView.setText(getString(R.string.mods_for_instance, instanceName));

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRecyclerView.setAdapter(mModItemAdapter);
        mRecyclerView.addOnScrollListener(mOverlayScrollListener);

        mOverlay.post(() -> {
            int h = mOverlay.getHeight();
            mRecyclerView.setPadding(
                    mRecyclerView.getPaddingLeft(),
                    mRecyclerView.getPaddingTop() + h,
                    mRecyclerView.getPaddingRight(),
                    mRecyclerView.getPaddingBottom());
        });

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mFilterButton.setOnClickListener(v -> showFilterDialog());

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerView.removeOnScrollListener(mOverlayScrollListener);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.mods_search_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.mods_search_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mStatusTextView.setVisibility(View.GONE);
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
