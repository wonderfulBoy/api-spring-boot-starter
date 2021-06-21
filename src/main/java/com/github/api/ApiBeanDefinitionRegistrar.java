package com.github.api;

import com.github.api.core.ApiDocumentationScanner;
import com.github.api.core.DefaultRequestHandlerEjector;
import com.github.api.core.IRequestHandlerEjector;
import com.github.api.core.WebRequestHandlerProvider;
import com.github.api.core.arch.ApiDocumentArchives;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * ApiBeanDefinitionRegistrar
 * Add the API to parse the required bean definitions and assemble them as needed
 *
 * @author echils
 */
public class ApiBeanDefinitionRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {

        RootBeanDefinition webRequestHandlerProviderBeanDefinition
                = new RootBeanDefinition(WebRequestHandlerProvider.class);
        webRequestHandlerProviderBeanDefinition.setLazyInit(true);
        webRequestHandlerProviderBeanDefinition.setSynthetic(true);
        registry.registerBeanDefinition(WebRequestHandlerProvider.class.getName(),
                webRequestHandlerProviderBeanDefinition);

        RootBeanDefinition apiDocumentArchivesBeanDefinition
                = new RootBeanDefinition(ApiDocumentArchives.class);
        apiDocumentArchivesBeanDefinition.setLazyInit(true);
        apiDocumentArchivesBeanDefinition.setSynthetic(true);
        registry.registerBeanDefinition(ApiDocumentArchives.class.getName(),
                apiDocumentArchivesBeanDefinition);

        RootBeanDefinition defaultRequestHandlerEjectorBeanDefinition
                = new RootBeanDefinition(DefaultRequestHandlerEjector.class);
        defaultRequestHandlerEjectorBeanDefinition.setLazyInit(true);
        defaultRequestHandlerEjectorBeanDefinition.setSynthetic(true);
        registry.registerBeanDefinition(IRequestHandlerEjector.class.getName(),
                defaultRequestHandlerEjectorBeanDefinition);

        RootBeanDefinition apiDocumentationScannerBeanDefinition
                = new RootBeanDefinition(ApiDocumentationScanner.class);
        apiDocumentationScannerBeanDefinition.setLazyInit(true);
        apiDocumentationScannerBeanDefinition.setSynthetic(true);
        registry.registerBeanDefinition(ApiDocumentationScanner.class.getName(),
                apiDocumentationScannerBeanDefinition);
    }

}
