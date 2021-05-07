package com.github.api.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.github.api.ApiDocumentContext.*;

/**
 * DefaultRequestHandlerEjector
 * Request used to exclude the application framework
 *
 * @author echils
 * @since 2021-02-28 18:40:30
 */
public class DefaultRequestHandlerEjector implements IRequestHandlerEjector {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(DefaultRequestHandlerEjector.class);

    private List<String> excludePatternList;

    public DefaultRequestHandlerEjector() {
        excludePatternList = new ArrayList<>();
        excludePatternList.add(DEFAULT_INNER_ERROR_REQUEST);
        excludePatternList.add(DEFAULT_API_QUERY_REQUEST);
        excludePatternList.add(DEFAULT_API_DOWNLOAD_REQUEST);
    }

    @Override
    public void handle(Set<RequestMappingInfo> requestMappingInfos) {
        if (!CollectionUtils.isEmpty(requestMappingInfos)) {
            requestMappingInfos.removeIf(requestMappingInfo -> {
                Set<String> patterns = requestMappingInfo.getPatternsCondition().getPatterns();
                for (String pattern : patterns) {
                    if (excludePatternList.contains(pattern)) {
                        logger.debug("Default exclude request:{}", pattern);
                        return true;
                    }
                }
                return false;
            });
        }
    }

}
