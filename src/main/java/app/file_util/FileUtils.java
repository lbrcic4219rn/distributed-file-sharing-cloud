package app.file_util;

import app.AppConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FileUtils {


    public static boolean isPathFile(String rootDirectory, String path) {
        return new File(rootDirectory + File.separator + path).isFile();
    }

    public static FileInfo getFileInfoFromPath(String rootDirectory, String path) {
        path = rootDirectory + File.separator + path;
        File f = new File(path);

        if (!f.exists()) {
            AppConfig.timestampedErrorPrint("File " + path + " doesn't exist.");
            return null;
        }
        if (f.isDirectory()) {
            AppConfig.timestampedErrorPrint(path + " is a directory and not a file.");
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String filePath = path.replace(rootDirectory + File.separator, "");

            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }

            if (!fileContent.isEmpty())
                fileContent.deleteCharAt(fileContent.length() - 1);

            return new FileInfo(filePath, fileContent.toString(), AppConfig.myServentInfo.getChordId());
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't read " + path + ".");
        }

        return null;

    }

    public static List<FileInfo> getDirectoryInfoFromPath(String rootDirectory, String path) {

        List<FileInfo> fileInfoList = new ArrayList<>();

        path = rootDirectory + File.separator + path;
        File f = new File(path);
        if (!f.exists()) {
            AppConfig.timestampedErrorPrint("Directory " + path + " doesn't exist.");
            return fileInfoList;
        }

        if (f.isFile()) {
            AppConfig.timestampedErrorPrint(path + " is a file and not a directory.");
            return fileInfoList;
        }

        Queue<String> dirs = new LinkedList<>();
        dirs.add(path);

        while (!dirs.isEmpty()) {
            String dirPath = dirs.poll();
            List<String> subFiles = new ArrayList<>();

            File[] dirFiles = new File(dirPath).listFiles();
            if (dirFiles == null) {
                AppConfig.timestampedErrorPrint("Couldn't list " + dirPath + ".");
                continue;
            }
            for (File file : dirFiles) {
                String filePath = file.getPath().replace(rootDirectory + File.separator, "");
                subFiles.add(filePath);

                if (file.isFile()) {
                    FileInfo fileInfo = getFileInfoFromPath(rootDirectory, filePath);
                    if (fileInfo != null) {
                        fileInfoList.add(fileInfo);
                    }
                } else {
                    dirs.add(file.getPath());
                }
            }

            dirPath = dirPath.replace(rootDirectory + File.separator, "");
            fileInfoList.add(new FileInfo(dirPath, subFiles, AppConfig.myServentInfo.getChordId()));
        }
        return fileInfoList;
    }

}
