package com.github.api.core;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.utils.CommonParseUtils;
import com.github.api.utils.ControllerParseUtils;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javadoc.MethodDocImpl;
import io.swagger.models.*;
import io.swagger.models.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * ApiDocumentationScanner
 *
 * @author echils
 * @since 2021-03-08 22:16:28
 */
@Component
public class ApiDocumentationScanner {

    /**
     * Logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ApiDocumentationScanner.class);

    @Autowired
    private TypeResolver typeResolver;

    @Autowired
    private ApiDocumentProperties apiDocumentProperties;

    private Map<String, ClassDoc> classDocListMap;

    private Map<String, Map<String, MethodDocImpl>> classMethodDocMap;

    public ApiDocumentationScanner() {
        classDocListMap = new HashMap<>();
        classMethodDocMap = new HashMap<>();
    }

    Documentation scan(Map<RequestMappingInfo, HandlerMethod> requestMappingMap, RootDoc rootDoc) {

        Swagger body = swaggerInit();
        classDocListMap = Stream.of(rootDoc.classes())
                .collect(Collectors.toMap(ClassDoc::toString, classDoc -> classDoc));

        Map<String, Tag> tagMap = new HashMap<>();
        Map<String, Path> pathMap = new HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : requestMappingMap.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            RequestMappingInfo requestMappingInfo = entry.getKey();
            String className = handlerMethod.getBeanType().getName();

            Tag requestTag = tagMap.computeIfAbsent(className, cn -> {
                Tag tag = new Tag();
                tag.name(ControllerParseUtils.controllerNameAsGroup(handlerMethod));
                if (classDocListMap.containsKey(cn)) {
                    ClassDoc classDoc = classDocListMap.get(cn);
                    tag.description(classDoc.commentText());
                }
                return tag;
            });

            Path path = pathBuild(getRequestMethod(requestMappingInfo),
                    operationBuild(requestTag, requestMappingInfo, handlerMethod));
            pathMap.put(getRequestPath(requestMappingInfo), path);
        }

        body.paths(pathMap);
        body.tags(new ArrayList<>(tagMap.values()));
        return new Documentation(body);
    }


    /**
     * Get request method
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return {@link RequestMethod}
     */
    private RequestMethod getRequestMethod(RequestMappingInfo requestMappingInfo) {
        Iterator<RequestMethod> requestMethodIterator = requestMappingInfo.getMethodsCondition().getMethods().iterator();
        return requestMethodIterator.next();
    }


    /**
     * Get request path
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return the url path
     */
    private String getRequestPath(RequestMappingInfo requestMappingInfo) {
        Iterator<String> iterator = requestMappingInfo.getPatternsCondition().getPatterns().iterator();
        return iterator.next();
    }


    /**
     * Init the swagger info
     *
     * @return {@link Swagger}
     */
    private Swagger swaggerInit() {
        Swagger swagger = new Swagger();
        swagger.setInfo(apiInfo());
        return swagger;
    }


    /**
     * Wrapper the api info
     *
     * @return {@link Info}
     */
    private Info apiInfo() {
        Info info = new Info();
        ApiDocumentProperties.ApiInfo propertiesInfo = apiDocumentProperties.getInfo();
        info.title(propertiesInfo.getTitle());
        info.setVersion(propertiesInfo.getVersion());
        return info;
    }

    /**
     * Build the swagger path
     *
     * @param requestMethod {@link RequestMethod}
     * @param operation     {@link Operation}
     * @return {@link Path}
     */
    private Path pathBuild(RequestMethod requestMethod, Operation operation) {
        Path path = new Path();
        switch (requestMethod) {
            case GET:
                path.get(operation);
                break;
            case DELETE:
                path.delete(operation);
                break;
            case PUT:
                path.put(operation);
                break;
            case POST:
                path.post(operation);
                break;
        }
        return path;
    }


    /**
     * Build the swagger operation
     *
     * @param tag                {@link Tag}
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @param handlerMethod      {@link HandlerMethod}
     * @return {@link Operation}
     */
    private Operation operationBuild(Tag tag, RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        Operation operation = new Operation();
        operation.tag(tag.getName());
        operation.summary(summaryRequest(handlerMethod));
        operation.operationId(uniqueOperationId(requestMappingInfo, handlerMethod));
        operation.consumes(parseConsumes(requestMappingInfo));
        operation.produces(parseProduces(requestMappingInfo));
        operation.deprecated(ControllerParseUtils.isDeprecatedMethod(handlerMethod));
        operation.response(HttpStatus.OK.value(), responseBuild(handlerMethod));
        //TODO
        return operation;
    }

    /**
     * Build request response info
     *
     * @param handlerMethod {@link HandlerMethod}
     * @return {@link Response}
     */
    private Response responseBuild(HandlerMethod handlerMethod) {
        Response response = new Response().description(HttpStatus.OK.getReasonPhrase());
        Property responseProperty = null;
        Method handlerRequestMethod = handlerMethod.getMethod();



        Class<?> returnClassType = handlerRequestMethod.getReturnType();
        ResolvedType resolve = typeResolver.resolve(returnClassType);

        DefaultReferContext.Types.typeNameFor(returnClassType);
        System.out.println(handlerRequestMethod);


        return response;
    }

    /**
     * Parse request consumes
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return The request consumes
     */
    private List<String> parseConsumes(RequestMappingInfo requestMappingInfo) {
        Set<MediaType> consumes = requestMappingInfo.getConsumesCondition().getConsumableMediaTypes();
        if (CollectionUtils.isEmpty(consumes)) {
            return Collections.singletonList(MediaType.APPLICATION_JSON_VALUE);
        }
        return consumes.stream().map(MimeType::toString).collect(Collectors.toList());
    }

    /**
     * Parse request produces
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return The request produces
     */
    private List<String> parseProduces(RequestMappingInfo requestMappingInfo) {
        Set<MediaType> produces = requestMappingInfo.getConsumesCondition().getConsumableMediaTypes();
        if (CollectionUtils.isEmpty(produces)) {
            return Collections.singletonList(MediaType.ALL_VALUE);
        }
        return produces.stream().map(MimeType::toString).collect(Collectors.toList());
    }

    /**
     * Parse the summary of the request method
     *
     * @param handlerMethod {@link HandlerMethod}
     * @return The summary request of the method
     */
    private String summaryRequest(HandlerMethod handlerMethod) {

        String summary = null;
        String className = handlerMethod.getBeanType().getName();
        if (classDocListMap.containsKey(className)) {
            logger.debug("Class {} matched", className);
            ClassDoc classDoc = classDocListMap.get(className);
            List<MethodDoc> methodDocs = Arrays.asList(classDoc.methods());

            Map<String, MethodDocImpl> methodDocMap = methodDocs.stream().collect(Collectors.toMap(
                    methodDoc -> CommonParseUtils.trimAll(methodDoc.toString()),
                    methodDoc -> (MethodDocImpl) methodDoc));
            classMethodDocMap.put(className, methodDocMap);

            String methodGeneralName = handlerMethod.getMethod().toString();
            for (Map.Entry<String, MethodDocImpl> entry : methodDocMap.entrySet()) {
                String methodName = entry.getKey();
                MethodDocImpl value = entry.getValue();
                if (methodGeneralName.contains(CommonParseUtils.trimAll(methodName))) {
                    summary = value.commentText();
                    if (summary.contains(ApiDocumentContext.COMMENT_NEWLINE_SEPARATOR)) {
                        summary = summary.replaceAll(ApiDocumentContext.COMMENT_NEWLINE_SEPARATOR, "");
                    }
                    break;
                }
            }
        }
        return summary;
    }


    /**
     * Generate unique operation id of the request
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @param handlerMethod      {@link HandlerMethod}
     * @return Unique operation id
     */
    private String uniqueOperationId(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {
        return String.format("%sUsing%s", handlerMethod.getMethod().getName(), getRequestMethod(requestMappingInfo).name());
    }

}


