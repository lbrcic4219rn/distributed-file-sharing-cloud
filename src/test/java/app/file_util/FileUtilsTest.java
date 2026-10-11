package app.file_util;

import app.AppConfig;
import app.ChordState;
import app.ServentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    private static final String S = File.separator;

    @TempDir
    Path root;

    @BeforeEach
    void setUp() throws IOException {
        ChordState.CHORD_SIZE = 64;
        AppConfig.myServentInfo = new ServentInfo("localhost", 1000);

        Files.createDirectories(root.resolve("dir/sub"));
        Files.writeString(root.resolve("dir/a.txt"), "line 1\nline 2\n");
        Files.writeString(root.resolve("dir/sub/b.txt"), "b");
    }

    @Test
    void tellsFilesFromDirectories() {
        assertTrue(FileUtils.isPathFile(root.toString(), "dir" + S + "a.txt"));
        assertFalse(FileUtils.isPathFile(root.toString(), "dir"));
        assertFalse(FileUtils.isPathFile(root.toString(), "missing.txt"));
    }

    @Test
    void readsAFileRelativeToTheRoot() {
        FileInfo file = FileUtils.getFileInfoFromPath(root.toString(), "dir" + S + "a.txt");

        assertNotNull(file);
        assertEquals("dir" + S + "a.txt", file.getPath());
        assertEquals("line 1\nline 2", file.getContent());
        assertTrue(file.isFile());
        assertEquals(AppConfig.myServentInfo.getChordId(), file.getOgNode());
    }

    @Test
    void missingFileAndDirectoryGiveNothing() {
        assertNull(FileUtils.getFileInfoFromPath(root.toString(), "missing.txt"));
        assertNull(FileUtils.getFileInfoFromPath(root.toString(), "dir"));
        assertTrue(FileUtils.getDirectoryInfoFromPath(root.toString(), "missing").isEmpty());
    }

    @Test
    void readsADirectoryRecursively() {
        List<FileInfo> found = FileUtils.getDirectoryInfoFromPath(root.toString(), "dir");
        Map<String, FileInfo> byPath = found.stream().collect(Collectors.toMap(FileInfo::getPath, Function.identity()));

        assertEquals(Set.of("dir", "dir" + S + "sub", "dir" + S + "a.txt", "dir" + S + "sub" + S + "b.txt"), byPath.keySet());
        assertEquals(Set.of("dir" + S + "sub", "dir" + S + "a.txt"), Set.copyOf(byPath.get("dir").getSubFiles()));
        assertTrue(byPath.get("dir" + S + "sub").isDirectory());
        assertEquals("b", byPath.get("dir" + S + "sub" + S + "b.txt").getContent());
    }
}
