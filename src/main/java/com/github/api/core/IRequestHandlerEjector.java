package com.github.api.core;

import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.Set;

/**
 * Support for user customization of requests I that need to be excluded
 *
 * @author echils
 */
public interface IRequestHandlerEjector {

    /**
     * Exclude the request mapping form the list
     *
     * @param requestMappingInfos the list of request mapping
     */
    void handle(Set<RequestMappingInfo> requestMappingInfos);

}
