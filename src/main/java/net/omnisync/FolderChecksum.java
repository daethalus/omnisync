package net.omnisync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

public class FolderChecksum {

    public enum FileDifference {
        CHANGED,
        EQUALS,
        ONLY_EXISTS_IN_SERVER,
        ONLY_EXISTS_IN_CLIENT
    }

    private final File folder;
    private final Gson gson;
    private final File modsFile;
    private final Logger logger;

    public FolderChecksum(File folder, Logger logger) {
        this.folder = folder;
        this.logger = logger;
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        this.gson = gsonBuilder.create();
        this.modsFile = new File(this.folder, "mods.json");
    }

    public Map<String, Object> generate() {
        Map<String, Object> map = new HashMap<>();
        for (String folder : OmniSyncMod.folderToCheck) {
            map.put(folder, verifyFolder(new File(this.folder, folder)));
        }
        return map;
    }


    public Map<String, Object> loadJsonFile() {
        try {
            Type type = new TypeToken<Map<String, Object>>(){}.getType();
            return gson.fromJson(new FileReader(this.modsFile), type);
        } catch (Exception ex) {
            //Utils.registerException(ex);
            ex.printStackTrace();
        }
        return null;
    }

    public void saveJsonFile() {
        try {
            if (modsFile.exists()) {
                modsFile.delete();
            }
            PrintWriter writer = new PrintWriter(modsFile);
            writer.print(gson.toJson(this.generate()));
            writer.close();
        } catch (Exception ex) {
            logger.error("error on saveJsonFile", ex);
        }
    }

    public Map<String, Object> verifyFolder(File folder) {
        Map<String, Object> map = new HashMap<>();
        logger.info("Verify folder " + folder);
        if (folder.exists()) {
            for (File file: folder.listFiles()) {
                if (file.isDirectory()) {
                    map.put(file.getName(), verifyFolder(file));
                } else {
                    try (InputStream is = Files.newInputStream(file.toPath())) {
                        map.put(file.getName(), DigestUtils.sha256Hex(is));
                    } catch (IOException e) {
                        logger.error("error on verifyFolder", e);
                    }
                }
            }
        } else {
            logger.info("Folder " + folder + " not exists");
        }
        return map;
    }

    public Map<String, FileDifference> compareJsonFile(Map<String, Object> server, Map<String, Object> client) {
        return compareJsonFile(server, client, "");
    }


    public Map<String, FileDifference> compareJsonFile(Map<String, Object> server, Map<String, Object> client, String parentFolder) {
        Map<String, FileDifference> differences = new HashMap<>();

        for(String file: server.keySet()) {

            Object serverValue = server.get(file);
            Object clientValue = null;
            if (client != null) {
                clientValue = client.get(file);
            }
            if (serverValue instanceof String) {
                if (client == null || !client.containsKey(file)) {
                    differences.put(parentFolder + "/" + file, FileDifference.ONLY_EXISTS_IN_SERVER);
                    logger.info(" {} ONLY_EXISTS_IN_SERVER", parentFolder + "/" + file);
                } else if (!serverValue.equals(clientValue)) {
                    differences.put(parentFolder + "/" + file, FileDifference.CHANGED);
                    logger.info(" {} CHANGED sha256 server{} sha256 client {} ", parentFolder + "/" + file, serverValue, clientValue);
                }
            } else {
                Map<String, Object> serverMap = (Map<String, Object>) serverValue;
                Map<String, Object> clientMap = (Map<String, Object>) clientValue;
                differences.putAll(compareJsonFile(serverMap, clientMap,parentFolder + "/" + file));
            }
        }

        if (client != null) {
            for(String file: client.keySet()) {
                if (!server.containsKey(file)) {
                    logger.info(" {} ONLY_EXISTS_IN_CLIENT", parentFolder + "/" + file);
                    differences.put(parentFolder + "/" + file, FileDifference.ONLY_EXISTS_IN_CLIENT);
                }
            }
        }

        return differences;
    }

}
