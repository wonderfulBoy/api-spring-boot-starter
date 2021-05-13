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
 * @since 2021-02-27 21:24:45
 */
public class WebRequestHandlerProvider {

    private List<IRequestHandlerEjector> requestHandlerEjectors;
    private RequestMappingHandlerMapping requestMappingHandlerMapping;


    @Autowired
    public WebRequestHandlerProvider(RequestMappingHandlerMapping requestMappingHandlerMapping,
                                     List<IRequestHandlerEjector> requestHandlerEjectors) {
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.requestHandlerEjectors = requestHandlerEjectors;
    }


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
