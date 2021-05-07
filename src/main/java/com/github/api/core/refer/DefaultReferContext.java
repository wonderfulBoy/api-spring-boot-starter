package com.github.api.core.refer;

import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newHashSet;
import static org.springframework.http.HttpStatus.OK;
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
        responses.put(GET, Collections.singletonList(new ResponseMessage(OK.value(), "Success")));
        responses.put(PUT, Collections.singletonList(new ResponseMessage(OK.value(), "Success")));
        responses.put(POST, Collections.singletonList(new ResponseMessage(OK.value(), "Success")));
        responses.put(DELETE, Collections.singletonList(new ResponseMessage(OK.value(), "Success")));
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
