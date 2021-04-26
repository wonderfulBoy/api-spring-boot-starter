package com.github.api.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * ControllerParseUtils
 *
 * @author echils
 * @since 2021-03-01 22:55:44
 */
public class ControllerParseUtils {


    /**
     * Determine whether the controller request has been deprecated
     *
     * @param handlerMethod {@link HandlerMethod}
     */
    public static boolean isDeprecatedMethod(HandlerMethod handlerMethod) {
        Deprecated annotation = handlerMethod.getMethod().getAnnotation(Deprecated.class);
        return annotation != null;
    }


    /**
     * Resolve the name of the controller into an api group
     *
     * @param handlerMethod {@link HandlerMethod}
     */
    public static String controllerNameAsGroup(HandlerMethod handlerMethod) {
        Class<?> controllerClass = handlerMethod.getBeanType();
        return splitCamelCase(controllerClass.getSimpleName(), "-")
                .replace("/", "")
                .toLowerCase();
    }


    private static String splitCamelCase(String s, String separator) {
        if (StringUtils.isBlank(s)) {
            return "";
        }
        return s.replaceAll(String.format("%s|%s|%s", "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])", "(?<=[A-Za-z])(?=[^A-Za-z])"), separator);
    }

}
