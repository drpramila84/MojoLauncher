package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks GitHub Releases for a newer version of Wither Launcher.
 *
 * Strategy: fetch the latest release from the GitHub API. The release "id"
 * field is an ever-increasing integer assigned by GitHub — a higher id always
 * means a newer release. We persist the last id that the user dismissed so
 * the dialog only reappears when an even newer release is published.
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";
    private static final String API_URL =
            "https://api.github.com/repos/drpramila84/MojoLauncher/releases/latest";
    private static final String PREFS_NAME = "wither_update_checker";
    private static final String KEY_DISMISSED_ID = "last_dismissed_release_id";
    private static final long NO_DISMISSED = -1L;

    public interface Callback {
        /**
         * Called on the background thread when a new release is found.
         * @param releaseId   GitHub release id (store this when user dismisses)
         * @param releaseName Human-readable release title (e.g. "v1.2.3")
         * @param releaseUrl  URL to the release page (open in browser for download)
         */
        void onUpdateAvailable(long releaseId, String releaseName, String releaseUrl);
    }

    /**
     * Run the update check on the calling (background) thread.
     * Call this from {@code PojavApplication.sExecutorService} — never on the main thread.
     */
    public static void check(Context context, Callback callback) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "GitHub API returned HTTP " + code);
                return;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            long releaseId = json.getLong("id");
            String releaseName = json.optString("name", json.optString("tag_name", "New version"));
            String releaseUrl = json.optString("html_url", "");

            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long dismissedId = prefs.getLong(KEY_DISMISSED_ID, NO_DISMISSED);

            if (releaseId > dismissedId) {
                Log.i(TAG, "New release found: id=" + releaseId + " name=" + releaseName);
                callback.onUpdateAvailable(releaseId, releaseName, releaseUrl);
            } else {
                Log.i(TAG, "Already up-to-date (dismissedId=" + dismissedId + ")");
            }

        } catch (Exception e) {
            Log.w(TAG, "Update check failed: " + e.getMessage());
        }
    }

    /**
     * Persist that the user has dismissed this release so it won't show again
     * until a newer release is published.
     */
    public static void dismissRelease(Context context, long releaseId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_DISMISSED_ID, releaseId)
                .apply();
    }
}
