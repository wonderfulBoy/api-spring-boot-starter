package com.github.api.core.refer;

import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static java.util.Arrays.asList;
import static org.springframework.http.HttpStatus.*;
import static org.springframework.web.bind.annotation.RequestMethod.*;

/**
 * DefaultReferContext
 *
 * @author echils
 * @since 2021-03-01 21:06:09
 */
public class DefaultReferContext {

    public static final String OPEN = "«";
    public static final String CLOSE = "»";
    public static final String DELIM = ",";


    /**
     * default ignorable parameter types
     */
    public static HashSet<Class> ignored;

    /**
     * default response messages
     */
    public static Map<RequestMethod, List<ResponseMessage>> responses;


    static {
        initIgnorableTypes();
        initResponseMessages();
    }

    private static void initResponseMessages() {
        responses = newLinkedHashMap();
        responses.put(GET, asList(new ResponseMessage(OK.value(), "Query success"),
                new ResponseMessage(NOT_FOUND.value(), "Not found"),
                new ResponseMessage(UNAUTHORIZED.value(), "Unauthorized")));
        responses.put(PUT, asList(new ResponseMessage(CREATED.value(), "Update success"),
                new ResponseMessage(NOT_FOUND.value(), "Not found"),
                new ResponseMessage(UNAUTHORIZED.value(), "Unauthorized")));
        responses.put(POST, asList(new ResponseMessage(CREATED.value(), "Create success"),
                new ResponseMessage(NOT_FOUND.value(), "Not found"),
                new ResponseMessage(UNAUTHORIZED.value(), "Unauthorized")));
        responses.put(DELETE, asList(new ResponseMessage(NO_CONTENT.value(), "Delete success"),
                new ResponseMessage(NOT_FOUND.value(), "Not found"),
                new ResponseMessage(UNAUTHORIZED.value(), "Unauthorized")));
    }


    private static void initIgnorableTypes() {
        ignored = newHashSet();
        ignored.add(ServletRequest.class);
        ignored.add(Class.class);
        ignored.add(Void.class);
        ignored.add(Void.TYPE);
        ignored.add(HttpServletRequest.class);
        ignored.add(HttpServletResponse.class);
        ignored.add(HttpHeaders.class);
        ignored.add(BindingResult.class);
        ignored.add(ServletContext.class);
        ignored.add(UriComponentsBuilder.class);
    }

}
