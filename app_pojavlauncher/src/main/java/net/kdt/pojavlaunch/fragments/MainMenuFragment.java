package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.witherlauncher.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mModsButton = view.findViewById(R.id.mods_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v) -> openGameDirectory(v.getContext()));

        mModsButton.setOnClickListener(v -> openModsWithInstancePicker());
    }

    private void openModsWithInstancePicker() {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                List<Instance> instances = Instances.loadAllInstances();
                if (instances.isEmpty()) {
                    Tools.runOnUiThread(() ->
                            Toast.makeText(requireContext(), R.string.no_instance, Toast.LENGTH_LONG).show()
                    );
                    return;
                }

                String[] names = new String[instances.size()];
                for (int i = 0; i < instances.size(); i++) {
                    Instance inst = instances.get(i);
                    String name    = Tools.validOrNullString(inst.name);
                    String version = Tools.validOrNullString(inst.versionId);
                    if (name != null && version != null)      names[i] = name + " (" + version + ")";
                    else if (name != null)                    names[i] = name;
                    else if (version != null)                 names[i] = version;
                    else                                      names[i] = "Instance " + i;
                }

                Tools.runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.mods_select_instance_title)
                        .setItems(names, (dialog, which) -> {
                            Instance selected = instances.get(which);
                            Bundle bundle = new Bundle();
                            bundle.putString(
                                    ModsManagerFragment.EXTRA_INSTANCE_ROOT,
                                    selected.getInstanceRoot().getAbsolutePath()
                            );
                            Tools.swapFragment(requireActivity(),
                                    ModsManagerFragment.class,
                                    ModsManagerFragment.TAG,
                                    bundle);
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                );
            } catch (IOException e) {
                Tools.runOnUiThread(() -> Tools.showError(requireContext(), e));
            }
        });
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
