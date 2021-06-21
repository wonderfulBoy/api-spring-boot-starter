package com.github.api.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebRequestHandlerProvider
 * Provide all urls that need to be resolved
 *
 * @author echils
 */
public class WebRequestHandlerProvider {

    @Autowired
    private List<IRequestHandlerEjector> requestHandlerEjectors;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * List the filtered requests
     */
    Map<RequestMappingInfo, HandlerMethod> list() {

        Map<RequestMappingInfo, HandlerMethod> handlerMethodMap
                = new HashMap<>(requestMappingHandlerMapping.getHandlerMethods());
        Set<RequestMappingInfo> requestMappingInfos = handlerMethodMap.keySet();
        if (!CollectionUtils.isEmpty(requestHandlerEjectors)) {
            requestHandlerEjectors.forEach(ejector -> ejector.handle(requestMappingInfos));
        }
        handlerMethodMap.entrySet().removeIf(entry -> !requestMappingInfos.contains(entry.getKey()));
        return handlerMethodMap;
    }

}
