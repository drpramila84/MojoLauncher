package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.content.Context;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.witherlauncher.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A ModpackApi that installs individual mod JARs directly into an instance's mods/ folder.
 * Uses Modrinth for search and version listing.
 */
public class ModInstallApi implements ModpackApi {

    private final ModrinthApi mModrinthApi;
    private final File mModsDir;

    public ModInstallApi(File instanceGameDir) {
        mModrinthApi = new ModrinthApi();
        mModsDir = new File(instanceGameDir, "mods");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        return mModrinthApi.searchMod(searchFilters, previousPageResult);
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        return mModrinthApi.getModDetails(item);
    }

    @Override
    public void handleModpackInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                installModpack(modDetail, selectedVersion);
                Tools.runOnUiThread(() ->
                        Toast.makeText(context, R.string.mods_install_success, Toast.LENGTH_SHORT).show()
                );
            } catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    @Override
    public ModLoader installModpack(ModDetail modDetail, int selectedVersion) throws IOException {
        String url = modDetail.versionUrls[selectedVersion];

        // Extract a clean filename from the URL
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        int queryIdx = fileName.indexOf('?');
        if (queryIdx > 0) fileName = fileName.substring(0, queryIdx);
        if (!fileName.toLowerCase().endsWith(".jar")) fileName += ".jar";

        if (!mModsDir.exists() && !mModsDir.mkdirs()) {
            throw new IOException("Failed to create mods directory: " + mModsDir.getAbsolutePath());
        }

        File modFile = new File(mModsDir, fileName);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.mod_install_downloading);

        try (InputStream in = new URL(url).openStream();
             FileOutputStream out = new FileOutputStream(modFile)) {
            IOUtils.copy(in, out);
            out.flush();
        }

        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        return null;
    }

    @Override
    public ModLoader installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        throw new UnsupportedOperationException("Local modpack install not supported by ModInstallApi");
    }
}
