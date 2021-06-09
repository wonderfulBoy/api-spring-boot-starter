package com.github.api.core.arch;

import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.core.Documentation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;

import static com.github.api.ApiDocumentContext.MAVEN_DEFAULT_ARTIFACT_ID_SEPARATOR;

/**
 * Api archives
 *
 * @author echils
 * @since 2021-02-27 20:05:58
 */
public class ApiDocumentArchives {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ApiDocumentArchives.class);

    @Autowired
    private ApiDocumentProperties apiDocumentProperties;

    /**
     * Convert the document into a general yaml file
     * and persist it in a suitable directory.
     * And decide whether to submit the final file
     * to git management according to the user-defined configuration
     *
     * @param documentation api doc
     */
    public void depict(Documentation documentation) {

        if (documentation == null) {
            logger.warn("Api document is null,there is no need to start the persistence process");
            return;
        }

        ApiDocumentContext.documentation = documentation;
        String outputPath = ApiDocumentContext.resourceDirectory + File.separator +
                ApiDocumentContext.DEFAULT_ARCHIVES_DIRECTORY + File.separator +
                filenameBuild() + ApiDocumentContext.SUPPORT_OUTPUT_DOCUMENT_TYPE;
        logger.info("Persistence path:{}", outputPath);
        if (StringUtils.isBlank(outputPath)) {
            logger.warn("Persistence path is blank,there is no need to start the persistence process");
            return;
        }

        File outputFile = new File(outputPath);
        outputFile.getParentFile().mkdirs();
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputStream.write(documentation.toYaml().getBytes());
            outputStream.flush();
        } catch (Exception e) {
            logger.error("Api document persistence process occur error:{}", e.getMessage());
            throw new PersistenceException(e);
        }

        GitRepositoryManager gitRepositoryManager
                = new GitRepositoryManager(ApiDocumentContext.projectDirectory);

        if (gitRepositoryManager.isGitProject()) {
            gitRepositoryManager.addFile(apiDocumentProperties.getStruct().getResource() + File.separator
                    + ApiDocumentContext.DEFAULT_ARCHIVES_DIRECTORY);
        }
    }

    /**
     * Build the api document filename,if the api title contains {@link ApiDocumentContext#MAVEN_DEFAULT_ARTIFACT_ID_SEPARATOR},
     * then will take the last part as the file name
     *
     * @return the api document filename
     */
    private String filenameBuild() {
        String title = apiDocumentProperties.getInfo().getTitle();
        if (title.contains(MAVEN_DEFAULT_ARTIFACT_ID_SEPARATOR)) {
            String[] titleNameArray = title.split(MAVEN_DEFAULT_ARTIFACT_ID_SEPARATOR);
            return titleNameArray[titleNameArray.length - 1].toLowerCase();
        }
        return title;
    }

}
