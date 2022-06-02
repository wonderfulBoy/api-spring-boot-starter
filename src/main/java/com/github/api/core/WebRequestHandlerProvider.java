package com.github.api.core;

import com.google.common.collect.Maps;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;

/**
 * WebRequestHandlerProvider
 * Provide all urls that need to be resolved
 *
 * @author echils
 */
public class WebRequestHandlerProvider {

    @Autowired
    private List<IRequestHandlerPostProcessor> requestHandlerPostProcessors;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * List the filtered requests
     */
    Map<RequestMappingInfo, HandlerMethod> list() {

        Map<RequestMappingInfo, HandlerMethod> handlerMethodMap
                = new LinkedHashMap<>(requestMappingHandlerMapping.getHandlerMethods());
        Set<RequestMappingInfo> requestMappingInfos = handlerMethodMap.keySet();
        if (!CollectionUtils.isEmpty(requestHandlerPostProcessors)) {
            requestHandlerPostProcessors.forEach(ejector -> ejector.handle(requestMappingInfos));
        }
        handlerMethodMap.entrySet().removeIf(entry -> !requestMappingInfos.contains(entry.getKey()));
        LinkedHashMap<RequestMappingInfo, HandlerMethod> result
                = Maps.newLinkedHashMapWithExpectedSize(handlerMethodMap.size());

        handlerMethodMap.entrySet().stream().sorted(Comparator.comparing(o -> o.getKey().toString()))
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

}
