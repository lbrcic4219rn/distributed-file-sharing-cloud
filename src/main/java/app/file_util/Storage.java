package app.file_util;

import app.AppConfig;

import java.io.File;
import java.util.*;

/**
 *
 */
public class Storage {

    private final Map<String, FileInfo> index = new TreeMap<>();
    private final Map<String, FileInfo> owned = new TreeMap<>();
    private final Map<Integer, Map<String, FileInfo>> backups = new HashMap<>();
    private final Map<Integer, Long> backupVersions = new HashMap<>();
    private long myBackupVersion = 0;

    public static String parentOf(String path) {
        int separatorPos = path.lastIndexOf(File.separatorChar);
        return separatorPos <= 0 ? null : path.substring(0, separatorPos);
    }

    public synchronized Map<String, FileInfo> getIndexSnapshot() {
        return new HashMap<>(index);
    }

    public synchronized void replaceIndex(Map<String, FileInfo> newIndex) {
        index.clear();
        index.putAll(newIndex);
    }

    public synchronized boolean contains(String path) {
        return index.containsKey(path);
    }

    public synchronized FileInfo getEntry(String path) {
        return index.get(path);
    }

    public synchronized FileInfo getOwned(String path) {
        return owned.get(path);
    }

    public synchronized Map<String, FileInfo> getOwnedSnapshot() {
        return new HashMap<>(owned);
    }

    public synchronized void addEntry(FileInfo entry) {
        FileInfo existing = index.get(entry.getPath());
        if (existing == null) {
            index.put(entry.getPath(), entry.withoutContent());
        } else if (existing.isDirectory() && entry.isDirectory()) {
            List<String> merged = new ArrayList<>(existing.getSubFiles());
            for (String subFile : entry.getSubFiles()) {
                if (!merged.contains(subFile)) {
                    merged.add(subFile);
                }
            }
            index.put(entry.getPath(), existing.withSubFiles(merged));
        }
        linkToParent(entry.getPath(), entry.getOgNode());
    }

    private void linkToParent(String path, int owner) {
        String parentPath = parentOf(path);
        if (parentPath == null) {
            return;
        }
        FileInfo parent = index.get(parentPath);
        if (parent == null) {
            index.put(parentPath, new FileInfo(parentPath, List.of(path), owner));
            linkToParent(parentPath, owner);
        } else if (parent.isDirectory() && !parent.getSubFiles().contains(path)) {
            List<String> subFiles = new ArrayList<>(parent.getSubFiles());
            subFiles.add(path);
            index.put(parentPath, parent.withSubFiles(subFiles));
        }
    }

    public synchronized void addOwned(FileInfo file) {
        owned.put(file.getPath(), file);
        addEntry(file);
    }

    public synchronized List<String> collectPaths(String path) {
        List<String> paths = new ArrayList<>();
        collect(path, paths);
        return paths;
    }

    public synchronized List<String> collectFiles(String path) {
        List<String> files = new ArrayList<>();
        for (String collected : collectPaths(path)) {
            if (index.get(collected).isFile()) {
                files.add(collected);
            }
        }
        return files;
    }

    private void collect(String path, List<String> paths) {
        FileInfo entry = index.get(path);
        if (entry == null || paths.contains(path)) {
            return;
        }
        paths.add(path);
        for (String subFile : entry.getSubFiles()) {
            collect(subFile, paths);
        }
    }

    public synchronized boolean removeEntries(Collection<String> paths) {
        boolean removedOwned = false;
        for (String path : paths) {
            index.remove(path);
            removedOwned |= owned.remove(path) != null;

            String parentPath = parentOf(path);
            FileInfo parent = parentPath == null ? null : index.get(parentPath);
            if (parent != null && parent.getSubFiles().contains(path)) {
                List<String> subFiles = new ArrayList<>(parent.getSubFiles());
                subFiles.remove(path);
                index.put(parentPath, parent.withSubFiles(subFiles));
            }
        }
        return removedOwned;
    }

    public synchronized boolean hasEntriesOwnedBy(int ownerId) {
        return index.values().stream().anyMatch(entry -> entry.getOgNode() == ownerId);
    }

    public synchronized void reassignOwner(int oldOwner, int newOwner) {
        index.replaceAll((path, entry) -> entry.getOgNode() == oldOwner ? entry.withOwner(newOwner) : entry);
    }

    public synchronized void adopt(Map<String, FileInfo> files) {
        int myId = AppConfig.myServentInfo.getChordId();
        for (FileInfo file : files.values()) {
            owned.put(file.getPath(), file.withOwner(myId));
        }
    }

    public synchronized long nextBackupVersion() {
        return ++myBackupVersion;
    }

    public synchronized void putBackup(int ownerId, long version, Map<String, FileInfo> files) {
        if (version > backupVersions.getOrDefault(ownerId, 0L)) {
            backupVersions.put(ownerId, version);
            backups.put(ownerId, new HashMap<>(files));
        }
    }

    public synchronized boolean hasBackup(int ownerId) {
        return backups.containsKey(ownerId);
    }

    public synchronized Map<String, FileInfo> removeBackup(int ownerId) {
        backupVersions.remove(ownerId);
        return backups.remove(ownerId);
    }

    public synchronized void dropBackups(Set<Integer> ownerIds) {
        for (Integer ownerId : ownerIds) {
            removeBackup(ownerId);
        }
    }

    public synchronized Set<Integer> getBackupOwners() {
        return Set.copyOf(backups.keySet());
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("index:");
        index.values().forEach(entry -> sb.append("\n  ").append(entry));
        sb.append("\nowned files: ").append(owned.keySet());
        sb.append("\nbackups:");
        backups.forEach((ownerId, files) -> sb.append("\n  of node ").append(ownerId).append(": ").append(new TreeMap<>(files).keySet()));
        return sb.toString();
    }
}
