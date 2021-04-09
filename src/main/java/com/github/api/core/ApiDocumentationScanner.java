package com.github.api.core;

import com.github.api.core.refer.*;
import com.github.api.utils.CommonParseUtils;
import com.github.api.utils.ControllerParseUtils;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.javadoc.MethodDocImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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


    private static final Logger logger = LoggerFactory.getLogger(ApiDocumentationScanner.class);

    @Autowired
    private DefaultModelProvider defaultModelProvider;

    private Map<String, RequestMappingContext> mappingContextMap;

    public ApiDocumentationScanner() {
        mappingContextMap = new ConcurrentHashMap<>();
    }


    Documentation scan(Map<RequestMappingInfo, HandlerMethod> requestMappingMap, RootDoc rootDoc) {

        Map<String, ClassDoc> classDocListMap = Stream.of(rootDoc.classes())
                .collect(Collectors.toMap(ClassDoc::toString, classDoc -> classDoc));
        Map<String, Map<String, MethodDocImpl>> methodDocListMap = new HashMap<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : requestMappingMap.entrySet()) {
            HandlerMethod handlerMethod = entry.getValue();
            RequestMappingInfo requestMappingInfo = entry.getKey();
            String className = handlerMethod.getBeanType().getName();

            RequestMappingContext requestMappingContext = mappingContextMap.computeIfAbsent(className, clazz -> {
                RequestMappingContext context = new RequestMappingContext();
                context.setTagName(ControllerParseUtils.controllerNameAsGroup(handlerMethod));
                if (classDocListMap.containsKey(clazz)) {
                    logger.debug("Class {} matched", className);
                    ClassDoc classDoc = classDocListMap.get(clazz);
                    List<MethodDoc> methodDocs = Arrays.asList(classDoc.methods());
                    Map<String, MethodDocImpl> methodDocMap = methodDocs.stream()
                            .collect(Collectors.toMap(MethodDoc::toString, methodDoc -> (MethodDocImpl) methodDoc));
                    methodDocListMap.put(className, methodDocMap);
                    context.setDescription(classDoc.commentText());
                }
                return context;
            });

            ApiListingReference apiListingReference = new ApiListingReference();
            apiListingReference.setConsumes(requestMappingInfo.getConsumesCondition().getConsumableMediaTypes());
            apiListingReference.setProduces(requestMappingInfo.getProducesCondition().getProducibleMediaTypes());
            apiListingReference.setDeprecated(ControllerParseUtils.isDeprecatedMethod(handlerMethod));

            //Normally, there is only one request requestMethod
            Iterator<RequestMethod> requestMethodIterator = requestMappingInfo.getMethodsCondition().getMethods().iterator();
            RequestMethod requestMethod = requestMethodIterator.next();
            apiListingReference.setRequestMethod(requestMethod);

            //Normally, there is only one request requestMethod
            Iterator<String> patternIterator = requestMappingInfo.getPatternsCondition().getPatterns().iterator();
            apiListingReference.setPath(patternIterator.next());

            String methodGeneralName = handlerMethod.getMethod().toString();
            Map<String, MethodDocImpl> methodDocMap = methodDocListMap.get(className);

            methodDocMap.forEach((methodName, methodDoc) -> {
                if (methodGeneralName.contains(CommonParseUtils.trimAll(methodName))) {
                    logger.debug("Class {} requestMethod {} matched", className, methodName);
                    apiListingReference.setMethodName(methodDoc.name());
                    apiListingReference.setDescription(methodDoc.commentText());

                    ModelReference returnModel = defaultModelProvider.returnModelBuild(handlerMethod, classDocListMap, methodDoc);
                    apiListingReference.setResponseModel(returnModel);

                    List<ParameterReference> paramModels = defaultModelProvider.paramModelBuild(handlerMethod, classDocListMap, methodDoc);
                    apiListingReference.setParameterReferences(paramModels);

                    apiListingReference.setResponseMessages(DefaultReferContext.responses.get(requestMethod));
                }
            });

            List<ApiListingReference> apiListingReferences = requestMappingContext.getApiListingReferences();
            apiListingReferences.add(apiListingReference);

        }
        return new Documentation();
    }

}


