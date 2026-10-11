package app.file_util;

import app.AppConfig;
import app.ChordState;
import app.ServentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StorageTest {

    private static final String S = File.separator;
    private static final String DIR = "dir";
    private static final String SUB_DIR = DIR + S + "sub";
    private static final String FILE_A = DIR + S + "a.txt";
    private static final String FILE_B = SUB_DIR + S + "b.txt";

    private Storage storage;
    private int myId;

    @BeforeEach
    void setUp() {
        ChordState.CHORD_SIZE = 64;
        AppConfig.myServentInfo = new ServentInfo("localhost", 1000);
        myId = AppConfig.myServentInfo.getChordId();
        storage = new Storage();
    }

    @Test
    void addingAFileCreatesItsParentDirectories() {
        storage.addOwned(new FileInfo(FILE_B, "content b", myId));

        assertTrue(storage.getEntry(DIR).isDirectory());
        assertEquals(List.of(SUB_DIR), storage.getEntry(DIR).getSubFiles());
        assertEquals(List.of(FILE_B), storage.getEntry(SUB_DIR).getSubFiles());
        assertEquals("content b", storage.getOwned(FILE_B).getContent());
    }

    @Test
    void indexNeverHoldsContent() {
        storage.addOwned(new FileInfo(FILE_A, "content a", myId));

        assertEquals("", storage.getEntry(FILE_A).getContent());
        assertEquals("", storage.getIndexSnapshot().get(FILE_A).getContent());
    }

    @Test
    void existingFileIsNotReplacedAndDirectoriesAreMerged() {
        storage.addEntry(new FileInfo(FILE_A, "", 7));
        storage.addEntry(new FileInfo(FILE_A, "", 9));
        assertEquals(7, storage.getEntry(FILE_A).getOgNode());

        storage.addEntry(new FileInfo(DIR, List.of(SUB_DIR), 9));
        assertEquals(Set.of(FILE_A, SUB_DIR), Set.copyOf(storage.getEntry(DIR).getSubFiles()));
    }

    @Test
    void directoryIsCollectedAndRemovedRecursively() {
        storage.addOwned(new FileInfo(FILE_A, "a", myId));
        storage.addEntry(new FileInfo(FILE_B, "", 7));
        storage.addOwned(new FileInfo("other.txt", "o", myId));

        assertEquals(Set.of(DIR, SUB_DIR, FILE_A, FILE_B), Set.copyOf(storage.collectPaths(DIR)));
        assertEquals(Set.of(FILE_A, FILE_B), Set.copyOf(storage.collectFiles(DIR)));

        assertTrue(storage.removeEntries(storage.collectPaths(DIR)));

        assertFalse(storage.contains(DIR));
        assertFalse(storage.contains(FILE_B));
        assertNull(storage.getOwned(FILE_A));
        assertTrue(storage.contains("other.txt"));
    }

    @Test
    void removingOneFileUnlinksItFromItsDirectory() {
        storage.addEntry(new FileInfo(FILE_A, "", 7));
        storage.addEntry(new FileInfo(FILE_B, "", 7));

        assertFalse(storage.removeEntries(List.of(FILE_B)), "we didn't store that file");

        assertEquals(List.of(), storage.getEntry(SUB_DIR).getSubFiles());
        assertEquals(List.of(FILE_A), storage.collectFiles(DIR));
    }

    @Test
    void takingOverTheFilesOfAnotherNode() {
        int deadId = (myId + 1) % ChordState.CHORD_SIZE;
        storage.addEntry(new FileInfo(FILE_A, "", deadId));
        storage.putBackup(deadId, 1, Map.of(FILE_A, new FileInfo(FILE_A, "a", deadId)));
        assertTrue(storage.hasBackup(deadId));
        assertTrue(storage.hasEntriesOwnedBy(deadId));

        storage.adopt(storage.removeBackup(deadId));
        storage.reassignOwner(deadId, myId);

        assertFalse(storage.hasBackup(deadId));
        assertFalse(storage.hasEntriesOwnedBy(deadId));
        assertEquals(myId, storage.getEntry(FILE_A).getOgNode());
        assertEquals(myId, storage.getEntry(DIR).getOgNode());
        assertEquals("a", storage.getOwned(FILE_A).getContent());
        assertEquals(myId, storage.getOwned(FILE_A).getOgNode());
    }

    @Test
    void olderBackupDoesNotReplaceNewerOne() {
        storage.putBackup(5, 2, Map.of(FILE_A, new FileInfo(FILE_A, "new", 5)));
        storage.putBackup(5, 1, Map.of());

        Map<String, FileInfo> backup = storage.removeBackup(5);
        assertNotNull(backup);
        assertEquals("new", backup.get(FILE_A).getContent());
    }

    @Test
    void backupsOfGivenOwnersAreDropped() {
        storage.putBackup(5, 1, Map.of());
        storage.putBackup(6, 1, Map.of());

        storage.dropBackups(Set.of(5));

        assertEquals(Set.of(6), storage.getBackupOwners());
    }
}
