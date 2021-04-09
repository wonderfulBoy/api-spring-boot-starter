package com.github.api;

import com.github.api.core.ApiReporterBootstrapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * This configuration class is used to manage api document
 * parsing components in the spring container
 *
 * @author echils
 * @since 2021-02-25 21:34:31
 */
@Configuration
@EnableConfigurationProperties(ApiDocumentProperties.class)
@Import(ApiDocumentImportSelector.class)
public class ApiDocumentConfiguration {


    @Bean
    public ApiInfoBeanPostProcessor apiBeanPostProcessor() {
        return new ApiInfoBeanPostProcessor();
    }


    @Bean
    public ApiReporterBootstrapper apiReporterBootstrapper() {
        return new ApiReporterBootstrapper();
    }


}