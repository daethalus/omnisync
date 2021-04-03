package net.omnisync;

import com.google.common.io.ByteStreams;
import com.google.common.net.UrlEscapers;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.launchwrapper.Launch;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

public class OmniLauncher {


    public static Config config;
    public static String appDatFolder = System.getenv("APPDATA");
    public static File minecraftFolder = new File(OmniLauncher.appDatFolder, ".minecraft");

    public static ProgressionFrame progressionFrame;
    public static SyncConfig syncConfig;

    private static final Logger log = LogManager.getLogger(OmniLauncher.class);

    public static void main(String[] args) {
        syncConfig = null;
        config = null;
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        boolean shouldCheck = true;

        try {
            config = new Config();
        } catch (Exception ex) {
            log.error("error on load config.properties ", ex);
        }

        progressionFrame = new ProgressionFrame();
        progressionFrame.init();
        progressionFrame.setProcessName("Download configuration file");
        progressionFrame.reset();
        progressionFrame.setMaximum(2);

        try {
            BufferedInputStream bis = new BufferedInputStream(getStream("/sync-config.json"));
            GsonBuilder gsonBuilder = new GsonBuilder();
            gsonBuilder.setPrettyPrinting();
            Gson gson = gsonBuilder.create();
            syncConfig = gson.fromJson(new String(ByteStreams.toByteArray(bis), Charsets.UTF_8), SyncConfig.class);
        } catch (Exception ex) {
            log.error(ex);
            log.info("not possible to download or process the sync-config.json file");
        }

        progressionFrame.incrementValue();

        log.info("checking mods.json");

        try {
            File modsJson = new File(minecraftFolder, "mods.json");
            BufferedInputStream bis = new BufferedInputStream(getStream("/mods.json"));
            FileUtils.copyInputStreamToFile(bis, modsJson);
        } catch (Exception ex) {
            log.error(ex);
            log.info("not possible to download the mods.json file");
            shouldCheck = false;
        }

        progressionFrame.incrementValue();

        if (shouldCheck) {

            FolderChecksum fileChecksum = new FolderChecksum(minecraftFolder, log);

            progressionFrame.setProcessName("Checking difference");
            Map<String, FolderChecksum.FileDifference> map = fileChecksum.compareJsonFile(fileChecksum.loadJsonFile(), fileChecksum.generate());

            progressionFrame.setProcessName("Download modpack files");
            progressionFrame.reset();
            progressionFrame.setMaximum(map.keySet().size());

            for (String file : map.keySet()) {
                if (file.toLowerCase().contains("omnisync")) {
                    continue;
                }
                progressionFrame.incrementValue();

                boolean isIgnored = false;
                if (syncConfig != null && syncConfig.getIgnoredFiles() != null) {
                    for (String ignored : syncConfig.getIgnoredFiles()) {
                        if (file.toLowerCase().contains(ignored.toLowerCase())) {
                            isIgnored = true;
                        }
                    }

                    if (isIgnored) {
                        continue;
                    }
                }

                File clientFile = new File(minecraftFolder, file);
                if (!map.get(file).equals(FolderChecksum.FileDifference.ONLY_EXISTS_IN_CLIENT)) {
                    log.info("only in server: " + clientFile.getName());

                    try {
                        progressionFrame.setProcessName("Downloading " + clientFile.getName());
                        log.info("Trying to download: {}", file);
                        BufferedInputStream bis = new BufferedInputStream(getStream(UrlEscapers.urlFragmentEscaper().escape(file)));
                        FileUtils.copyInputStreamToFile(bis, clientFile);
                        log.info("File: {} updated ", file);
                    } catch (Exception ex) {
                        log.error("A error happened ", ex);
                    }

                } else {
                    if (clientFile.exists()) {
                        if (clientFile.delete()) {
                            log.info("File: {} deleted ", file);
                        }
                    }
                }
            }
        } else {
            JOptionPane.showMessageDialog(null, "omnisync can't sync with the server");
        }

        progressionFrame.dispose();

        Launch.main(args);
    }

    public static InputStream getStream(String file) throws Exception {
        URL url = new URL(config.getServerURL() + file);
        log.info("trying to load file {}", url.toString());
        String str = config.getUser() + ":" + config.getPass();
        String encoding = Base64.getEncoder().encodeToString(str.getBytes());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Basic " + encoding);
        return connection.getInputStream();
    }
}
