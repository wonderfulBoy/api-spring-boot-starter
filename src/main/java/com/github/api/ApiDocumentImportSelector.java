package com.github.api;

import com.fasterxml.classmate.TypeResolver;
import com.github.api.core.arch.ApiDocumentArchives;
import com.github.api.core.ApiDocumentationScanner;
import com.github.api.core.DefaultRequestHandlerEjector;
import com.github.api.core.WebRequestHandlerProvider;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

/**
 * ApiDocumentImportSelector
 *
 * @author echils
 * @since 2021-03-11 23:08:19
 */
public class ApiDocumentImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{WebRequestHandlerProvider.class.getName(), ApiDocumentArchives.class.getName(),
                DefaultRequestHandlerEjector.class.getName(), ApiDocumentationScanner.class.getName(),
                TypeResolver.class.getName()};
    }

}
