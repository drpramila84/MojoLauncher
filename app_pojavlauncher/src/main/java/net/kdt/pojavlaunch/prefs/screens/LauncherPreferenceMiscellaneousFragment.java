package net.kdt.pojavlaunch.prefs.screens;

import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.witherlauncher.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.RendererCompatUtil;
import net.kdt.pojavlaunch.utils.WorldBackupManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {

    private ActivityResultLauncher<String> mExportLauncher;
    private ActivityResultLauncher<String[]> mImportLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mExportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/zip"),
                uri -> {
                    if (uri == null) return;
                    runExport(uri);
                }
        );

        mImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) return;
                    confirmAndRunImport(uri);
                }
        );
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);

        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = RendererCompatUtil.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);

        requirePreference("browse_game_files").setOnPreferenceClickListener(preference -> {
            openGameFolder();
            return true;
        });

        requirePreference("backup_export").setOnPreferenceClickListener(preference -> {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String suggestedName = getString(R.string.backup_default_filename) + "-" + timestamp + ".zip";
            mExportLauncher.launch(suggestedName);
            return true;
        });

        requirePreference("backup_import").setOnPreferenceClickListener(preference -> {
            mImportLauncher.launch(new String[]{"application/zip", "application/octet-stream"});
            return true;
        });
    }

    private void openGameFolder() {
        // Build a content URI pointing to the game home directory via the DocumentsProvider.
        // This lets any SAF-compatible file manager (Files by Google, MiXplorer, etc.)
        // open and browse the folder directly.
        String gameHomePath = Tools.DIR_GAME_HOME;
        String authority = getString(R.string.storageProviderAuthorities);
        Uri folderUri = DocumentsContract.buildDocumentUri(authority, gameHomePath);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.preference_browse_game_files_title)));
        } catch (ActivityNotFoundException e) {
            // No file manager installed — show the raw path as a fallback
            Toast.makeText(requireContext(),
                    getString(R.string.preference_browse_game_files_no_app, gameHomePath),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void runExport(Uri uri) {
        ProgressDialog progress = buildProgressDialog(R.string.backup_export_in_progress);
        progress.show();

        Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            WorldBackupManager.exportBackup(appContext, uri, (success, error) ->
                    Tools.runOnUiThread(() -> {
                        progress.dismiss();
                        if (success) {
                            showInfoDialog(getString(R.string.backup_export_success));
                        } else {
                            showInfoDialog(getString(R.string.backup_export_failed, error));
                        }
                    })
            );
        });
    }

    private void confirmAndRunImport(Uri uri) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.backup_import_confirm_title)
                .setMessage(R.string.backup_import_confirm_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> runImport(uri))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runImport(Uri uri) {
        ProgressDialog progress = buildProgressDialog(R.string.backup_import_in_progress);
        progress.show();

        Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            WorldBackupManager.importBackup(appContext, uri, (success, error) ->
                    Tools.runOnUiThread(() -> {
                        progress.dismiss();
                        if (success) {
                            showInfoDialog(getString(R.string.backup_import_success));
                        } else {
                            showInfoDialog(getString(R.string.backup_import_failed, error));
                        }
                    })
            );
        });
    }

    @SuppressWarnings("deprecation")
    private ProgressDialog buildProgressDialog(int messageRes) {
        ProgressDialog dialog = new ProgressDialog(requireContext());
        dialog.setMessage(getString(messageRes));
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        return dialog;
    }

    private void showInfoDialog(String message) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
