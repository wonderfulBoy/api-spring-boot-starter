package com.github.api;

import com.github.api.core.refer.Documentation;
import com.sun.javadoc.RootDoc;

/**
 * ApiDocumentContext apply the runtime context
 * and agree on common configurations and parameters
 *
 * @author echils
 * @since 2021-02-25 23:15:18
 */
public class ApiDocumentContext {


    public static final String DEFAULT_API_QUERY_REQUEST = "/api/show";


    public static final String DEFAULT_API_DOWNLOAD_REQUEST = "/api/download";


    public static final String DEFAULT_INNER_ERROR_REQUEST = "/error";


    public static final String SUPPORT_PARSE_FILE_TYPE = ".java";


    public static final String SUPPORT_OUTPUT_DOCUMENT_TYPE = ".yaml";


    public static final String DEFAULT_MAVEN_FILENAME = "pom.xml";


    public static final String DEFAULT_ARCHIVES_DIRECTORY = "archives";


    public static final String DEFAULT_OS_DIRECTORY = "windows";


    public static RootDoc rootDoc;


    public static String projectDirectory;


    public static String sourceDirectory;


    public static String resourceDirectory;


    public static String pomDirectory;


    public static Documentation documentation;


    public static final String SPRING_BOOT_TAG = "BOOT-INF";


    public static String DEFAULT_MATCHING_PATH = "/BOOT-INF/classes/archives/*.yaml";


    public static String DEFAULT_API_INFO_TITTLE = "API DOCUMENT DELEGATE";


    public static String DEFAULT_API_INFO_VERSION = "1.0-SNAPSHOT";

}
