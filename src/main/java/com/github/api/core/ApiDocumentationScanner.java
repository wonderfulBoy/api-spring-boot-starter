package com.github.api.core;

import com.fasterxml.classmate.MemberResolver;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.ResolvedMethod;
import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.javadoc.MethodDocImpl;
import com.github.api.utils.CommonParseUtils;
import com.github.api.utils.ControllerParseUtils;
import io.swagger.models.Tag;
import io.swagger.models.*;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.Parameter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.api.ApiDocumentContext.*;
import static com.github.api.core.DefaultReferContext.definitions;
import static com.google.common.collect.Lists.newArrayList;

/**
 * ApiDocumentationScanner
 *
 * @author echils
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

    private Map<Class<?>, List<ResolvedMethod>> methodsResolvedForHostClasses = new HashMap<>();

    private LocalVariableTableParameterNameDiscoverer variableDiscoverer = new LocalVariableTableParameterNameDiscoverer();


    Documentation scan(Map<RequestMappingInfo, HandlerMethod> requestMappingMap, RootDoc rootDoc) {

        Swagger body = swaggerInit();
        CLASS_DOC_MAP.putAll(Stream.of(rootDoc.classes())
                .collect(Collectors.toMap(ClassDoc::toString, classDoc -> classDoc)));
        Map<String, Tag> tagMap = new HashMap<>();
        Map<String, Path> pathMap = new HashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : requestMappingMap.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            RequestMappingInfo requestMappingInfo = entry.getKey();
            String className = handlerMethod.getBeanType().getName();
            Tag requestTag = tagMap.computeIfAbsent(className, cn -> {
                Tag tag = new Tag();
                tag.name(ControllerParseUtils.controllerNameAsGroup(handlerMethod));
                if (CLASS_DOC_MAP.containsKey(cn)) {
                    ClassDoc classDoc = CLASS_DOC_MAP.get(cn);
                    tag.description(classDoc.commentText());
                }
                return tag;
            });
            String requestPath = getRequestPath(requestMappingInfo);
            Path path = Optional.ofNullable(pathMap.get(requestPath)).orElse(new Path());
            pathMap.put(requestPath, pathBuild(path, getRequestMethod(requestMappingInfo),
                    operationBuild(requestTag, requestMappingInfo, handlerMethod)));
        }
        body.paths(pathMap);
        body.setDefinitions(definitions());
        body.tags(new ArrayList<>(tagMap.values()));
        return new Documentation(body);
    }


    /**
     * Get request method
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return The request method
     */
    private RequestMethod getRequestMethod(RequestMappingInfo requestMappingInfo) {
        Iterator<RequestMethod> requestMethodIterator = requestMappingInfo.getMethodsCondition().getMethods().iterator();
        return requestMethodIterator.hasNext() ? requestMethodIterator.next() : RequestMethod.GET;
    }


    /**
     * Get request path
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return the url path
     */
    private String getRequestPath(RequestMappingInfo requestMappingInfo) {
        Iterator<String> iterator = requestMappingInfo.getPatternsCondition().getPatterns().iterator();
        String requestPath = iterator.next();
        if (requestPath.contains(REQUEST_PATH_REGULAR_1)) {
            requestPath = requestPath.replaceAll("\\+", "");
            requestPath = requestPath.replaceAll(REQUEST_PATH_REGULAR_2, "");
        }
        return requestPath;
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
     * @param path          {@link Path}
     * @param requestMethod The request method
     * @param operation     {@link Operation}
     * @return {@link Path}
     */
    private Path pathBuild(Path path, RequestMethod requestMethod, Operation operation) {
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
        List<Parameter> parameters = parametersBuild(handlerMethod);
        operation.setParameters(parameters);
        operation.produces(parseProduces(requestMappingInfo));
        operation.consumes(parseConsumes(requestMappingInfo, parameters));
        operation.response(HttpStatus.OK.value(), responseBuild(handlerMethod));
        operation.deprecated(ControllerParseUtils.isDeprecatedMethod(handlerMethod));
        return operation;
    }


    /**
     * Build request parameter info
     *
     * @param handlerMethod {@link HandlerMethod}
     * @return {@link Parameter}
     */
    private List<Parameter> parametersBuild(HandlerMethod handlerMethod) {

        Class<?> controllerClass = handlerMethod.getBeanType();
        Optional<ResolvedMethod> resolvedMethodOptional
                = matchedMethod(handlerMethod.getMethod(), getMemberMethods(controllerClass));
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
        if (!resolvedMethodOptional.isPresent() || ArrayUtils.isEmpty(methodParameters)) {
            return Collections.emptyList();
        }

        ResolvedMethod resolvedMethod = resolvedMethodOptional.get();
        List<Parameter> parameters = new ArrayList<>();
        Map<Method, String[]> methodParamNameMap = new HashMap<>();
        for (int i = 0; i < methodParameters.length; i++) {
            MethodParameter methodParameter = methodParameters[i];
            ResolvedType argumentType = resolvedMethod.getArgumentType(i);
            Parameter parameter = DefaultReferContext.getParameter(methodParameter, argumentType);
            Method method = methodParameter.getMethod();
            if (parameter != null && method != null) {
                String[] parameterNames = methodParamNameMap.computeIfAbsent(method,
                        reflectMethod -> variableDiscoverer.getParameterNames(reflectMethod));
                if (parameterNames != null && parameterNames.length > methodParameter.getParameterIndex()) {
                    String parameterName = parameterNames[methodParameter.getParameterIndex()];
                    if (StringUtils.isBlank(parameter.getName())) {
                        parameter.setName(parameterName);
                    }
                    Map<String, MethodDocImpl> methodDocMap = CLASS_METHOD_DOC_MAP.get(controllerClass.getName());
                    methodDocMap.values().stream().filter(methodDoc
                            -> isMatched(handlerMethod, methodDoc)).findFirst().flatMap(methodDoc
                            -> Arrays.stream(methodDoc.paramTags()).filter(paramTag
                            -> paramTag.parameterName().equals(parameterName)).findFirst()).ifPresent(paramTag
                            -> parameter.setDescription(paramTag.parameterComment()));
                }
                parameters.add(parameter);
            }
        }
        return parameters;
    }


    /**
     * Build request response info
     *
     * @param handlerMethod {@link HandlerMethod}
     * @return {@link Response}
     */
    @SuppressWarnings("deprecation")
    private Response responseBuild(HandlerMethod handlerMethod) {

        Class<?> controllerClass = handlerMethod.getBeanType();
        Optional<ResolvedMethod> resolvedMethodOptional
                = matchedMethod(handlerMethod.getMethod(), getMemberMethods(controllerClass));
        ResolvedType returnType = resolvedMethodOptional.map(ResolvedMethod::getReturnType)
                .orElse(typeResolver.resolve(Void.TYPE));
        Response response = new Response().description(HttpStatus.OK.getReasonPhrase());
        response.schema(DefaultReferContext.getProperty(returnType));
        return response;
    }


    /**
     * Parse request consumes
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @return The request consumes
     */
    private List<String> parseConsumes(RequestMappingInfo requestMappingInfo, List<Parameter> parameters) {
        Set<MediaType> consumes = requestMappingInfo.getConsumesCondition().getConsumableMediaTypes();
        if (CollectionUtils.isEmpty(consumes)) {
            if (!CollectionUtils.isEmpty(parameters)) {
                List<Parameter> formParameters = parameters.stream().filter(parameter
                        -> parameter instanceof FormParameter).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(formParameters)) {
                    return Collections.singletonList(MediaType.MULTIPART_FORM_DATA_VALUE);
                }
            }
            return Collections.singletonList(MediaType.APPLICATION_JSON_VALUE);
        }
        return consumes.stream().map(MediaType::toString).collect(Collectors.toList());
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
        return produces.stream().map(MediaType::toString).collect(Collectors.toList());
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
        if (CLASS_DOC_MAP.containsKey(className)) {
            logger.debug("Class {} matched", className);
            ClassDoc classDoc = CLASS_DOC_MAP.get(className);
            List<MethodDoc> methodDocs = Arrays.asList(classDoc.methods());

            Map<String, MethodDocImpl> methodDocMap = methodDocs.stream().collect(Collectors.toMap(
                    methodDoc -> CommonParseUtils.trimAll(methodDoc.toString()),
                    methodDoc -> (MethodDocImpl) methodDoc));
            CLASS_METHOD_DOC_MAP.put(className, methodDocMap);
            for (Map.Entry<String, MethodDocImpl> entry : methodDocMap.entrySet()) {
                MethodDocImpl methodDoc = entry.getValue();
                if (isMatched(handlerMethod, methodDoc)) {
                    summary = methodDoc.commentText();
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
     * Determine whether the two methods match
     *
     * @param handlerMethod {@link HandlerMethod}
     * @param methodDoc     {@link MethodDocImpl}
     * @return the result of match
     */
    private boolean isMatched(HandlerMethod handlerMethod, MethodDocImpl methodDoc) {

        AnnotationDesc[] methodDocAnnotations = methodDoc.annotations();
        Annotation[] handlerMethodAnnotations = handlerMethod.getMethod().getAnnotations();
        if (handlerMethodAnnotations.length == methodDocAnnotations.length) {
            for (int i = 0; i < methodDocAnnotations.length; i++) {
                AnnotationDesc methodDocAnnotation = methodDocAnnotations[i];
                Annotation handlerMethodAnnotation = handlerMethodAnnotations[i];
                if (methodDocAnnotation.elementValues() == null || methodDocAnnotation.elementValues().length == 0) {
                    continue;
                }
                String methodDocPath = methodDocAnnotation.elementValues()[0].value().toString();
                if (methodDocPath.startsWith("\"") && methodDocPath.endsWith("\"")) {
                    methodDocPath = methodDocPath.replaceAll("\"", "");
                }
                if (handlerMethodAnnotation instanceof GetMapping &&
                        methodDocAnnotation.toString().contains(GetMapping.class.getName())) {
                    return ((GetMapping) handlerMethodAnnotation).value()[0].equals(methodDocPath);
                }
                if (handlerMethodAnnotation instanceof PostMapping &&
                        methodDocAnnotation.toString().contains(PostMapping.class.getName())) {
                    return ((PostMapping) handlerMethodAnnotation).value()[0].equals(methodDocPath);
                }
                if (handlerMethodAnnotation instanceof PutMapping &&
                        methodDocAnnotation.toString().contains(PutMapping.class.getName())) {
                    return ((PutMapping) handlerMethodAnnotation).value()[0].equals(methodDocPath);
                }
                if (handlerMethodAnnotation instanceof DeleteMapping &&
                        methodDocAnnotation.toString().contains(DeleteMapping.class.getName())) {
                    return ((DeleteMapping) handlerMethodAnnotation).value()[0].equals(methodDocPath);
                }
                if (handlerMethodAnnotation instanceof RequestMapping &&
                        methodDocAnnotation.toString().contains(RequestMapping.class.getName())) {
                    RequestMapping requestMapping = (RequestMapping) handlerMethodAnnotation;
                    return requestMapping.value()[0].equals(methodDocPath) &&
                            methodDocAnnotation.toString().contains(requestMapping.method()[0].toString());
                }
            }
        }
        return false;
    }


    /**
     * Generate unique operation id of the request
     *
     * @param requestMappingInfo {@link RequestMappingInfo}
     * @param handlerMethod      {@link HandlerMethod}
     * @return Unique operation id
     */
    private String uniqueOperationId(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod) {

        String controllerClass = handlerMethod.getBeanType().getSimpleName();
        return String.format("%s#%sUsing%s@%s", controllerClass, handlerMethod.getMethod().getName(),
                getRequestMethod(requestMappingInfo).name(), Math.abs(getRequestPath(requestMappingInfo).hashCode()));
    }


    /**
     * Get all member methods in the class
     *
     * @param hostClass the controller class
     */
    private List<ResolvedMethod> getMemberMethods(Class<?> hostClass) {

        if (!methodsResolvedForHostClasses.containsKey(hostClass)) {
            ResolvedType beanType = typeResolver.resolve(hostClass);
            MemberResolver resolver = new MemberResolver(typeResolver);
            resolver.setIncludeLangObject(false);
            ResolvedTypeWithMembers typeWithMembers = resolver.resolve(beanType, null, null);
            methodsResolvedForHostClasses.put(hostClass, newArrayList(typeWithMembers.getMemberMethods()));
        }
        return methodsResolvedForHostClasses.get(hostClass);
    }


    /**
     * Matched the method with all member methods in class.
     * The method cannot be matched by URL, so it can only be matched according to the overload mechanism
     */
    private Optional<ResolvedMethod> matchedMethod(Method method, List<ResolvedMethod> filteredMethod) {

        for (ResolvedMethod resolvedMethod : filteredMethod) {
            //same method name
            if (resolvedMethod.getName().equals(method.getName())) {
                //same argument length of method
                if (resolvedMethod.getArgumentCount() == method.getParameterTypes().length) {
                    boolean same = true;
                    //the parameter type of the parameter at the same index position is the same
                    for (int index = 0; index < resolvedMethod.getArgumentCount(); index++) {
                        ResolvedType resolvedArgumentType = resolvedMethod.getArgumentType(index);
                        Type genericParameterType = method.getGenericParameterTypes()[index];
                        try {
                            if (!((genericParameterType instanceof Class && ((Class<?>) genericParameterType)
                                    .isAssignableFrom(resolvedArgumentType.getErasedType())) ||
                                    (genericParameterType instanceof ParameterizedType && resolvedArgumentType.getErasedType()
                                    .isAssignableFrom((Class<?>) ((ParameterizedType) genericParameterType).getRawType())) || (
                                    ResolvableType.forType(genericParameterType).resolve() == resolvedArgumentType.getErasedType()))) {
                                same = false;
                                break;
                            }
                        } catch (Exception e) {
                            logger.error("match method failed:{}", method);
                            same = false;
                            break;
                        }
                    }
                    if (same) return Optional.of(resolvedMethod);
                }
            }
        }
        return Optional.empty();
    }

}


