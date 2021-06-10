package com.github.api.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static com.github.api.ApiDocumentContext.*;

/**
 * Some general or messy tools and methods in the parsing process
 *
 * @author echils
 * @since 2021-03-08 20:55:34
 */
public class CommonParseUtils {


    /**
     * List all java source files of the project
     *
     * @param projectFile the project file
     */
    public static List<File> listJavaSourceFiles(File projectFile) {

        return listFiles(projectFile, file -> file.getPath().endsWith(SUPPORT_PARSE_FILE_TYPE));
    }


    /**
     * Recursively enumerate all files in the specified file or directory, support filtering
     *
     * @param file      the target file
     * @param predicate {@link Predicate}
     */
    public static List<File> listFiles(File file, Predicate<File> predicate) {

        if (file == null) {
            return Collections.emptyList();
        }
        File[] childFiles = file.listFiles();
        if (childFiles == null || childFiles.length == 0) {
            return Collections.emptyList();
        }
        List<File> result = new ArrayList<>();
        for (File childFile : childFiles) {
            if (childFile.isDirectory()) {
                result.addAll(listFiles(childFile, predicate));
            }
            if (predicate.test(childFile)) {
                result.add(childFile);
            }
        }
        return result;
    }

    /**
     * Concatenate file paths through file separators to adapt to different operating systems
     *
     * @param filePaths the path need to be concatenated
     */
    public static String fileSeparatorJoin(String... filePaths) {

        if (filePaths == null || filePaths.length == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains(DEFAULT_OS_DIRECTORY)) {
            for (int i = 0; i < filePaths.length; i++) {
                String filePath = filePaths[i];
                if (filePath.contains("/")) {
                    filePath = filePath.replace("/", "\\");
                }
                if (i != 0 && !(filePaths[i - 1].endsWith(File.separator) || filePath.startsWith(File.separator))) {
                    result.append(File.separator);
                }
                result.append(filePath);
            }
        } else {
            for (int i = 0; i < filePaths.length; i++) {
                String filePath = filePaths[i];
                if (filePath.contains("\\")) {
                    filePath = filePath.replace("\\", "/");
                }
                if (i != 0 && !filePaths[i - 1].endsWith(File.separator) && !filePath.startsWith(File.separator)) {
                    result.append(File.separator);
                }
                result.append(filePath);
            }
        }
        return result.toString();
    }


    /**
     * Trim all blank
     *
     * @param source the value need be trim
     */
    public static String trimAll(String source) {

        if (StringUtils.isBlank(source)) {
            return source;
        }
        return source.replace(" ", "");
    }


    /**
     * whether in jar
     *
     * @param clazz the class need to judge
     * @return whether in jar
     */
    public static boolean inJar(Class<?> clazz) {

        if (clazz == null) {
            return false;
        }
        String resourcePath = clazz.getResource("/").toString();
        return resourcePath.contains(SPRING_BOOT_JAR_TAG);
    }

}
