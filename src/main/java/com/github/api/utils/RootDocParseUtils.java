package com.github.api.utils;


import com.sun.javadoc.RootDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * RootDocParseUtils
 *
 * @author echils
 * @since 2021-03-06 11:30:43
 */
public class RootDocParseUtils {

    private static final Logger logger = LoggerFactory.getLogger(RootDocParseUtils.class);

    /**
     * javadoc message
     */
    private static RootDoc rootDoc = null;

    private static List<String> javadocArgs;

    static {
        javadocArgs = new ArrayList<>();
        javadocArgs.add("-doclet");
        javadocArgs.add(RootDocParseUtils.class.getName());
        javadocArgs.add("-encoding");
        javadocArgs.add("utf-8");
        javadocArgs.add("-quiet");
    }

    /**
     * Javadoc parse
     *
     * @param files Class source files
     * @return
     */
    public static RootDoc parse(List<File> files) {
        if (!CollectionUtils.isEmpty(files)) {
            files.forEach(file -> javadocArgs.add(file.getPath()));
            int size = javadocArgs.size();
            logger.info("The total number of files to parse is {}", size);
            logger.info("Begin parsing the source files api infos...");
            com.sun.tools.javadoc.Main.execute(javadocArgs
                    .toArray(new String[size]));
        }
        return rootDoc;
    }


    /**
     * This requestMethod is for JVM use only
     *
     * @param root javadoc message
     * @return
     */
    @Deprecated
    public static boolean start(RootDoc root) {
        rootDoc = root;
        return true;
    }

}
