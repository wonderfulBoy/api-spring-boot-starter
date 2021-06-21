package com.github.api;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * This properties file is used to configure the project custom directory structure path
 * and the basic information of the api document. If there is no customized operation,the project
 * directory structure will be parsed according to the maven standard structure by default,and the basic
 * information of the api document will be built according to the <artifactId> ,<version>,<description>
 * tags in pom.xml
 *
 * @author echils
 */
@Data
@ConfigurationProperties("api")
public class ApiDocumentProperties {


    /**
     * Api documentation basic information
     */
    private ApiInfo info = new ApiInfo();


    /**
     * Project structure basic information
     */
    private Project struct = new Project();


    /**
     * Specifies the runtime environment for API document parsing
     */
    private Profile profile;


    /**
     * Api runtime profile
     */
    public enum Profile {

        /**
         * If you select the current configuration, the program will re-parsed and update the api documentation,
         * the current running environment must contains source code.
         */
        LOCAL,

        /**
         * If you select the current configuration, the program will not re-parse the API
         * and use the previously generated documentation
         */
        CLOUD
    }


    /**
     * Api base information
     */
    @Data
    public class ApiInfo {

        /**
         * Project title
         */
        private String title;

        /**
         * Project version
         */
        private String version;


        public boolean isBlank() {
            return StringUtils.isBlank(version) && StringUtils.isBlank(title);
        }
    }


    /**
     * Project directory structure information,
     * default maven standard project directory structure
     */
    @Data
    public class Project {

        /**
         * Project source path,used to load and parse source file information
         */
        private String source = "/src/main/java";

        /**
         * Project resource path,the final generated API document is stored under this path
         */
        private String resource = "/src/main/resources";

        /**
         * Exclude direction from project
         */
        private String exclude = "/target";

    }

}
