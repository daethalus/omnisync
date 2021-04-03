package net.omnisync;

import java.util.ArrayList;
import java.util.List;

public class SyncConfig {
    private List<String> ignoredFiles;

    public List<String> getIgnoredFiles() {
        return ignoredFiles;
    }

    public void setIgnoredFiles(List<String> ignoredFiles) {
        this.ignoredFiles = ignoredFiles;
    }

    public void addIgnoredFiles(String file) {
        if (ignoredFiles== null ) {
            ignoredFiles = new ArrayList<>();
        }
        ignoredFiles.add(file);
    }
}
