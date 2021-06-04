package com.github.api;

import com.github.api.utils.CommonParseUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.FileInputStream;

import static com.github.api.ApiDocumentContext.*;

/**
 * ApiInfoBeanPostProcessor mainly used for post processing of API document information,
 * if the user does not customize the description by configuration,then the class will
 * parse the contents of the pom file to fill in the necessary information
 *
 * @author echils
 * @since 2021-03-11 21:26:45
 */
public class ApiInfoBeanPostProcessor implements BeanPostProcessor {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ApiInfoBeanPostProcessor.class);

    @Autowired
    private ApiDocumentProperties apiDocumentProperties;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {

        Class<?> beanClass = bean.getClass();
        SpringBootApplication springBootApplication = beanClass.getAnnotation(SpringBootApplication.class);
        if (springBootApplication != null ) {

            if (apiDocumentProperties.getProfile() == ApiDocumentProperties.Profile.CLOUD) {
                return bean;
            }

            String classPath = beanClass.getResource("/").getPath();
            if (classPath.contains(SPRING_BOOT_TAG)) {
                logger.debug("Program running with jar...");
                apiDocumentProperties.setProfile(ApiDocumentProperties.Profile.CLOUD);
                return bean;
            }

            apiDocumentProperties.setProfile(ApiDocumentProperties.Profile.LOCAL);
            String projectPath = obtainProjectRoot(new File(classPath));
            if (StringUtils.isBlank(projectPath)) {
                String excludePath = apiDocumentProperties.getStruct().getExclude();
                if (StringUtils.isNoneBlank(excludePath) && classPath.contains(excludePath)) {
                    projectPath = classPath.substring(0, classPath.indexOf(excludePath));
                }
            }

            if (StringUtils.isBlank(projectPath)) {
                logger.error("Obtain project path failed...");
                apiDocumentProperties.setProfile(ApiDocumentProperties.Profile.CLOUD);
                return bean;
            }

            ApiDocumentContext.projectDirectory = projectPath;

            ApiDocumentProperties.Project projectStruct = apiDocumentProperties.getStruct();

            ApiDocumentContext.sourceDirectory
                    = CommonParseUtils.fileSeparatorJoin(projectPath, projectStruct.getSource());

            ApiDocumentContext.resourceDirectory
                    = CommonParseUtils.fileSeparatorJoin(projectPath, projectStruct.getResource());

            ApiDocumentContext.pomDirectory
                    = CommonParseUtils.fileSeparatorJoin(projectPath, DEFAULT_MAVEN_FILENAME);

            defaultApiInfoBuild(new File(ApiDocumentContext.pomDirectory));

        }
        return bean;
    }


    /**
     * Recursively find the project path through the path of
     * the class file corresponding to the project startup class.
     * Normally, the path where the pom file is located is the project root directory
     *
     * @param applicationFile {@link SpringBootApplication}
     */
    private String obtainProjectRoot(File applicationFile) {

        if (applicationFile == null) {
            logger.warn("Find project path by application file failed");
            return null;
        }
        File[] files = applicationFile.listFiles();
        if (files != null && files.length > 0) {
            for (File childFile : files) {
                if (childFile.getPath().contains(DEFAULT_MAVEN_FILENAME)) {
                    return applicationFile.getAbsolutePath();
                }
            }
        }
        return obtainProjectRoot(applicationFile.getParentFile());
    }


    /**
     * If the user does not have custom documentation information,
     * the default API documentation information is built from the POM file information
     *
     * @param pomFile the pom.xml
     */
    private void defaultApiInfoBuild(File pomFile) {

        ApiDocumentProperties.ApiInfo apiInfo = apiDocumentProperties.getInfo();
        if (pomFile == null || !pomFile.exists()) {
            if (apiInfo.isBlank()) {
                logger.info("Api document base info not configured,will use default");
                apiInfo.setTitle(DEFAULT_API_INFO_TITTLE);
                apiInfo.setVersion(DEFAULT_API_INFO_VERSION);
            }
            return;
        }

        try {
            Model model = new MavenXpp3Reader().read(new FileInputStream(pomFile));
            if (model != null && apiInfo.isBlank()) {
                if (StringUtils.isNotBlank(model.getName())) {
                    apiInfo.setTitle(model.getName());
                } else {
                    apiInfo.setTitle(model.getArtifactId().toUpperCase());
                }
                apiInfo.setVersion(model.getVersion());
            }
        } catch (Exception e) {
            logger.warn("Api document base info build with pom failed");
        }

    }

}
