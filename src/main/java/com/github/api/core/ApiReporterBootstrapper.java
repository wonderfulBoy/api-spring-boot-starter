package com.github.api.core;

import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.core.arch.ApiDocumentArchives;
import com.github.api.utils.CommonParseUtils;
import com.github.api.utils.RootDocParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ApiReporterBootstrapper
 *
 * @author echils
 * @since 2020-12-01 20:41
 */
@Component
public class ApiReporterBootstrapper implements SmartLifecycle {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ApiReporterBootstrapper.class);

    @Autowired
    private WebRequestHandlerProvider webRequestHandlerProvider;

    @Autowired
    private ApiDocumentArchives apiDocumentArchives;

    @Autowired
    private ApiDocumentationScanner apiDocumentationScanner;

    @Autowired
    private ApiDocumentProperties apiDocumentProperties;

    private AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void start() {
        if (initialized.compareAndSet(false, true)) {
            logger.info("Context refreshed,start the api document parsing process");
            try {
                List<File> javaSourceFiles = CommonParseUtils
                        .listJavaSourceFiles(new File(ApiDocumentContext.sourceDirectory));
                ApiDocumentContext.rootDoc = RootDocParseUtils.parse(javaSourceFiles);
                Documentation documentation = apiDocumentationScanner.scan(
                        webRequestHandlerProvider.list(), ApiDocumentContext.rootDoc);
                apiDocumentArchives.depict(documentation);
            } catch (Exception e) {
                logger.error("Api document parse failed:{}", e.getMessage());
            }
        }
    }

    @Override
    public boolean isAutoStartup() {
        return apiDocumentProperties.getProfile() == ApiDocumentProperties.Profile.LOCAL;
    }

    @Override
    public void stop(Runnable callback) {
        callback.run();
    }

    @Override
    public void stop() {
        initialized.getAndSet(false);
    }

    @Override
    public boolean isRunning() {
        return initialized.get();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

}
