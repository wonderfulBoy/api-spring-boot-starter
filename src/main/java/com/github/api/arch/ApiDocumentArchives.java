package com.github.api.arch;

import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.core.refer.Documentation;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Api archives
 *
 * @author echils
 * @since 2021-02-27 20:05:58
 */
public class ApiDocumentArchives {

    private static final Logger logger = LoggerFactory.getLogger(ApiDocumentArchives.class);

    private ApiDocumentProperties apiDocumentProperties;

    @Autowired
    public ApiDocumentArchives(ApiDocumentProperties apiDocumentProperties) {
        this.apiDocumentProperties = apiDocumentProperties;
    }

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
        String outputPath = ApiDocumentContext.resourceDirectory + File.separator +
                ApiDocumentContext.DEFAULT_ARCHIVES_DIRECTORY + File.separator +
                apiDocumentProperties.getInfo().getTitle() + ApiDocumentContext.SUPPORT_OUTPUT_DOCUMENT_TYPE;
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
            gitRepositoryManager.addFile(outputPath);
        }
    }

}
