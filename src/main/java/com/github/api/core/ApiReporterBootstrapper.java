package com.github.api.core;

import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.core.arch.ApiDocumentArchives;
import com.github.api.utils.CommonParseUtils;
import com.github.api.utils.RootDocParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ApiReporterBootstrapper
 *
 * @author echils
 */
@Component
public class ApiReporterBootstrapper implements SmartLifecycle, ApplicationContextAware {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ApiReporterBootstrapper.class);

    @Autowired
    private ApiDocumentProperties apiDocumentProperties;

    private ApplicationContext applicationContext;

    private WebRequestHandlerProvider webRequestHandlerProvider;

    private ApiDocumentArchives apiDocumentArchives;

    private ApiDocumentationScanner apiDocumentationScanner;

    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void start() {
        if (initialized.compareAndSet(false, true)) {
            logger.info("Context refreshed,start the api document parsing process");
            try {
                //Load the required dependencies when the actual execution begins
                dependenceLoad();
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
        //1024 is meaningless,just respect.
        return Integer.MAX_VALUE - 1024;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * Assembly all dependence bean
     */
    private void dependenceLoad() {
        if (applicationContext != null) {
            this.apiDocumentationScanner = (ApiDocumentationScanner)
                    this.applicationContext.getBean(ApiDocumentationScanner.class.getName());
            this.apiDocumentArchives = (ApiDocumentArchives)
                    this.applicationContext.getBean(ApiDocumentArchives.class.getName());
            this.webRequestHandlerProvider = (WebRequestHandlerProvider)
                    applicationContext.getBean(WebRequestHandlerProvider.class.getName());
        }
    }

}
