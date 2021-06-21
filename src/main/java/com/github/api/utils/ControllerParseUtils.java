package com.github.api.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * ControllerParseUtils
 *
 * @author echils
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
        String classSimpleName = controllerClass.getSimpleName();
        if (StringUtils.isBlank(classSimpleName)) {
            return "";
        }

        return classSimpleName.replaceAll(String.format("%s|%s|%s", "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])", "(?<=[A-Za-z])(?=[^A-Za-z])"), "-")
                .replace("/", "")
                .toLowerCase();
    }


}
