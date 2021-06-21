package com.github.api;

import com.github.api.core.Documentation;
import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.RootDoc;
import com.github.api.sun.tools.javadoc.MethodDocImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * ApiDocumentContext apply the runtime context
 * and agree on common configurations and parameters
 *
 * @author echils
 */
public class ApiDocumentContext {


    public static final String DEFAULT_API_QUERY_REQUEST = "/api/show";


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


    public static final String SPRING_BOOT_JAR_TAG = ".jar!/BOOT-INF/classes/";


    public static String DEFAULT_MATCHING_PATH = "/BOOT-INF/classes/archives/*.yaml";


    public static String DEFAULT_LOCAL_MATCHING_PATH = "/archives/*.yaml";


    public static String DEFAULT_API_INFO_TITTLE = "API DOCUMENT DELEGATE";


    public static String DEFAULT_API_INFO_VERSION = "1.0-SNAPSHOT";


    public static final String COMMENT_NEWLINE_SEPARATOR = "\n ";


    public static final String MAVEN_DEFAULT_ARTIFACT_ID_SEPARATOR = "-";


    public static final String REQUEST_PATH_REGULAR_1 = ":.+";


    public static final String REQUEST_PATH_REGULAR_2 = ":.";


    public static final Map<String, ClassDoc> CLASS_DOC_MAP = new HashMap<>();


    public static final Map<String, Map<String, MethodDocImpl>> CLASS_METHOD_DOC_MAP = new HashMap<>();

}
