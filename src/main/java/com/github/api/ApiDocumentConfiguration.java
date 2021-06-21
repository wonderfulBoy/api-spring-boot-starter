package com.github.api;

import com.fasterxml.classmate.TypeResolver;
import com.github.api.core.ApiReporterBootstrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * This configuration class is used to manage api document
 * parsing components in the spring container
 *
 * @author echils
 */
@Configuration
@EnableConfigurationProperties(ApiDocumentProperties.class)
@Import(ApiBeanDefinitionRegistrar.class)
public class ApiDocumentConfiguration {

    @Bean
    public ApiInfoBeanPostProcessor apiBeanPostProcessor() {
        return new ApiInfoBeanPostProcessor();
    }

    @Bean
    public ApiReporterBootstrapper apiReporterBootstrapper() {
        return new ApiReporterBootstrapper();
    }

    @Bean
    public ApiDocumentFileProvider apiDocumentFileProvider() {
        return new ApiDocumentFileProvider();
    }

    @Bean
    @ConditionalOnMissingBean(name = "typeResolver")
    public TypeResolver typeResolver() {
        return new TypeResolver();
    }

}
