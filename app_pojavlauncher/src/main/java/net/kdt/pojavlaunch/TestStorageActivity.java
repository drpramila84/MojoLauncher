package net.kdt.pojavlaunch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.tasks.AsyncAssetManager;

import net.kdt.witherlauncher.R;

public class TestStorageActivity extends Activity {
    private final int REQUEST_STORAGE_REQUEST_CODE = 1;
    private final int REQUEST_MANAGE_STORAGE_CODE = 2;
    private AlertDialog mPermissionRequestDialog;
    private boolean mPermsRequired = false;
    private boolean mPermsDialogShown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPermsDialogShown = false;
        if (!isStorageAllowed(this)) {
            mPermsRequired = true;
        } else {
            exit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mPermsRequired) return;
        // Re-check in case user just granted permission from system settings
        if (isStorageAllowed(this)) {
            mPermsRequired = false;
            exit();
            return;
        }
        if (!mPermsDialogShown) requestStoragePermission();
        else showRerequestDialog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mPermissionRequestDialog != null) mPermissionRequestDialog.dismiss();
    }

    private void showRerequestDialog() {
        if (mPermissionRequestDialog != null) mPermissionRequestDialog.dismiss();
        mPermissionRequestDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.global_error)
                .setMessage(R.string.toast_permission_denied)
                .setPositiveButton(android.R.string.ok, (d, i) -> requestStoragePermission())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mPermsRequired = false;
                exit();
            } else {
                mPermsDialogShown = true;
                showRerequestDialog();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                mPermsRequired = false;
                exit();
            } else {
                mPermsDialogShown = true;
                showRerequestDialog();
            }
        }
    }

    public static boolean isStorageAllowed(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: need MANAGE_EXTERNAL_STORAGE for persistent shared storage
            return Environment.isExternalStorageManager();
        }
        // Android 6-10: classic READ/WRITE_EXTERNAL_STORAGE
        int result1 = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int result2 = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE);
        return result1 == PackageManager.PERMISSION_GRANTED &&
                result2 == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        mPermsDialogShown = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: send user to the "Allow all files access" system page
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE_CODE);
            } catch (Exception e) {
                // Some devices don't support the per-app intent, fall back to global page
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, REQUEST_MANAGE_STORAGE_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, REQUEST_STORAGE_REQUEST_CODE);
        }
    }

    private void exit() {
        if (!Tools.checkStorageRoot(this)) {
            startActivity(new Intent(this, MissingStorageActivity.class));
            return;
        }
        LauncherPreferences.loadPreferences(this);
        AsyncAssetManager.unpackComponents(this);
        AsyncAssetManager.unpackSingleFiles(this);

        Intent intent = new Intent(this, LauncherActivity.class);
        startActivity(intent);
        finish();
    }
}
