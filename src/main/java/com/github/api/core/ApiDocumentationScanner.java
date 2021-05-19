package com.github.api.core;

import com.fasterxml.classmate.MemberResolver;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.ResolvedMethod;
import com.github.api.ApiDocumentContext;
import com.github.api.ApiDocumentProperties;
import com.github.api.utils.CommonParseUtils;
import com.github.api.utils.ControllerParseUtils;
import com.sun.javadoc.*;
import com.sun.tools.javadoc.MethodDocImpl;
import io.swagger.models.*;
import io.swagger.models.Tag;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.UntypedProperty;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.collect.Lists.newArrayList;

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

    private Map<Class, List<ResolvedMethod>> methodsResolvedForHostClasses = new HashMap<>();

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
        Map<String, Model> definitions = new HashMap<>();
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
        body.setDefinitions(definitions);
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
        for (int i = 0; i < methodParameters.length; i++) {
            MethodParameter methodParameter = methodParameters[i];
            ResolvedType argumentType = resolvedMethod.getArgumentType(i);
            Parameter parameter = DefaultReferContext.getParameter(methodParameter, argumentType);
            if (parameter != null) {
                java.lang.reflect.Parameter reflectParameter = DefaultReferContext.getReflectParameter(methodParameter);
                if (reflectParameter != null) {
                    if (StringUtils.isBlank(parameter.getName())) {
                        parameter.setName(reflectParameter.getName());
                    }
                    String[] handlerMethodSplit = handlerMethod.toString().split(" ");
                    String methodDesc = handlerMethodSplit[handlerMethodSplit.length - 1];
                    handlerMethodSplit = methodDesc.split("\\(");
                    Map<String, MethodDocImpl> methodDocMap = classMethodDocMap.get(controllerClass.getName());
                    for (Map.Entry<String, MethodDocImpl> methodDocEntry : methodDocMap.entrySet()) {
                        String methodDocEntryKey = methodDocEntry.getKey();
                        String[] methodDocEntryKeySplit = methodDocEntryKey.split("\\(");
                        if (methodDocEntryKeySplit[0].equals(handlerMethodSplit[0])) {
                            if (methodDocEntryKeySplit[1].split(",").length == handlerMethodSplit[1].split(",").length) {
                                MethodDocImpl methodDoc = methodDocEntry.getValue();
                                for (ParamTag paramTag : methodDoc.paramTags()) {
                                    if (paramTag.parameterName().equals(reflectParameter.getName())) {
                                        parameter.setDescription(paramTag.parameterComment());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
                parameters.add(parameter);
            }
        }


        //TODO
        QueryParameter parameter = new QueryParameter();
        parameter.setRequired(false);
        parameter.setDescription("测试");
        parameter.setName("test");
        parameter.setType("string");
        return parameters;
    }

    /**
     * Build request response info
     *
     * @param handlerMethod {@link HandlerMethod}
     * @return {@link Response}
     */
    private Response responseBuild(HandlerMethod handlerMethod) {
        Class<?> controllerClass = handlerMethod.getBeanType();
        Optional<ResolvedMethod> resolvedMethodOptional
                = matchedMethod(handlerMethod.getMethod(), getMemberMethods(controllerClass));
        ResolvedType returnType = resolvedMethodOptional.map(ResolvedMethod::getReturnType)
                .orElse(typeResolver.resolve(Void.TYPE));
        Response response = new Response().description(HttpStatus.OK.getReasonPhrase());
        response.schema(DefaultReferContext.getProperty(returnType, new UntypedProperty()));
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
                Annotation[] handlerMethodAnnotations = handlerMethod.getMethod().getAnnotations();
                AnnotationDesc[] methodDocAnnotations = value.annotations();
                if (handlerMethodAnnotations.length == methodDocAnnotations.length) {
                    //TODO 根据注解匹配方法
                    AnnotationDesc.ElementValuePair[] elementValuePairs = methodDocAnnotations[0].elementValues();
                    if (Arrays.toString(handlerMethodAnnotations).equals(Arrays.toString(methodDocAnnotations))) {
                        System.out.println();
                    }
                }

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
        String controllerClass = handlerMethod.getBeanType().getSimpleName();
        String uuid = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10);
        return String.format("%s#%sUsing%s@%s", controllerClass, handlerMethod.getMethod().getName(),
                getRequestMethod(requestMappingInfo).name(), uuid);
    }


    /**
     * Get all member methods in the class
     *
     * @param hostClass the controller class
     */
    private List<ResolvedMethod> getMemberMethods(Class hostClass) {
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
                        if (!((genericParameterType instanceof Class && ((Class<?>) genericParameterType)
                                .isAssignableFrom(resolvedArgumentType.getErasedType()))
                                || (genericParameterType instanceof ParameterizedType && resolvedArgumentType.getErasedType()
                                .isAssignableFrom((Class<?>) ((ParameterizedType) genericParameterType).getRawType())))) {
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


