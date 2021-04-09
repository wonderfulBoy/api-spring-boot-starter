package com.github.api.core;

import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.Set;

/**
 * Support for user customization of requests I that need to be excluded
 *
 * @author echils
 * @since 2021-02-28 17:02:32
 */
public interface IRequestHandlerEjector {

    void handle(Set<RequestMappingInfo> requestMappingInfos);

}
