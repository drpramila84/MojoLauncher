package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.net.Uri;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WorldBackupManager {

    private static final String ZIP_PREFIX_WORLDS = "worlds/";
    private static final String ZIP_PREFIX_RESOURCEPACKS = "resourcepacks/";
    private static final String ZIP_PREFIX_CONTROLMAP = "controlmap/";

    public interface BackupCallback {
        void onComplete(boolean success, String errorMessage);
    }

    public static void exportBackup(Context context, Uri destinationUri, BackupCallback callback) {
        OutputStream rawOs;
        try {
            rawOs = context.getContentResolver().openOutputStream(destinationUri);
        } catch (IOException e) {
            callback.onComplete(false, e.getMessage());
            return;
        }
        if (rawOs == null) {
            callback.onComplete(false, "Could not open output stream for the selected file");
            return;
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(rawOs))) {
            File savesDir = new File(Tools.DIR_GAME_NEW, "saves");
            if (savesDir.exists() && savesDir.isDirectory()) {
                addDirectoryToZip(zos, savesDir, ZIP_PREFIX_WORLDS);
            }

            File resourcePacksDir = new File(Tools.DIR_GAME_NEW, "resourcepacks");
            if (resourcePacksDir.exists() && resourcePacksDir.isDirectory()) {
                addDirectoryToZip(zos, resourcePacksDir, ZIP_PREFIX_RESOURCEPACKS);
            }

            File controlMapDir = new File(Tools.CTRLMAP_PATH);
            if (controlMapDir.exists() && controlMapDir.isDirectory()) {
                addDirectoryToZip(zos, controlMapDir, ZIP_PREFIX_CONTROLMAP);
            }

            callback.onComplete(true, null);
        } catch (IOException e) {
            callback.onComplete(false, e.getMessage());
        }
    }

    public static void importBackup(Context context, Uri sourceUri, BackupCallback callback) {
        InputStream rawIs;
        try {
            rawIs = context.getContentResolver().openInputStream(sourceUri);
        } catch (IOException e) {
            callback.onComplete(false, e.getMessage());
            return;
        }
        if (rawIs == null) {
            callback.onComplete(false, "Could not open the selected backup file");
            return;
        }

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(rawIs))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (containsPathTraversal(name)) {
                    zis.closeEntry();
                    continue;
                }

                File destFile = resolveDestinationFile(name);
                if (destFile == null) {
                    zis.closeEntry();
                    continue;
                }

                if (!isUnderAllowedRoot(destFile)) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!destFile.exists() && !destFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + destFile.getAbsolutePath());
                    }
                } else {
                    FileUtils.ensureParentDirectory(destFile);
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(destFile))) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }

            callback.onComplete(true, null);
        } catch (IOException e) {
            callback.onComplete(false, e.getMessage());
        }
    }

    private static void addDirectoryToZip(ZipOutputStream zos, File directory, String zipPrefix) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = zipPrefix + file.getName();
            if (file.isDirectory()) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
                addDirectoryToZip(zos, file, entryName + "/");
            } else {
                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private static File resolveDestinationFile(String zipPath) {
        if (zipPath.startsWith(ZIP_PREFIX_WORLDS)) {
            String relative = zipPath.substring(ZIP_PREFIX_WORLDS.length());
            if (relative.isEmpty()) return null;
            return new File(Tools.DIR_GAME_NEW + "/saves/", relative);
        } else if (zipPath.startsWith(ZIP_PREFIX_RESOURCEPACKS)) {
            String relative = zipPath.substring(ZIP_PREFIX_RESOURCEPACKS.length());
            if (relative.isEmpty()) return null;
            return new File(Tools.DIR_GAME_NEW + "/resourcepacks/", relative);
        } else if (zipPath.startsWith(ZIP_PREFIX_CONTROLMAP)) {
            String relative = zipPath.substring(ZIP_PREFIX_CONTROLMAP.length());
            if (relative.isEmpty()) return null;
            return new File(Tools.CTRLMAP_PATH + "/", relative);
        }
        return null;
    }

    private static boolean isUnderAllowedRoot(File file) {
        try {
            String canonical = file.getCanonicalPath();
            String savesRoot = new File(Tools.DIR_GAME_NEW + "/saves/").getCanonicalPath();
            String resourcePacksRoot = new File(Tools.DIR_GAME_NEW + "/resourcepacks/").getCanonicalPath();
            String controlMapRoot = new File(Tools.CTRLMAP_PATH + "/").getCanonicalPath();
            return canonical.startsWith(savesRoot)
                    || canonical.startsWith(resourcePacksRoot)
                    || canonical.startsWith(controlMapRoot);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean containsPathTraversal(String zipPath) {
        return zipPath.contains("..") || zipPath.startsWith("/");
    }
}
