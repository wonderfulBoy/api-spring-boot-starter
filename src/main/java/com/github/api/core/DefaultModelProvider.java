package com.github.api.core;

import com.fasterxml.classmate.MemberResolver;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.ResolvedTypeWithMembers;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.RawField;
import com.fasterxml.classmate.members.ResolvedMethod;
import com.fasterxml.classmate.types.ResolvedArrayType;
import com.fasterxml.classmate.types.ResolvedObjectType;
import com.fasterxml.classmate.types.ResolvedPrimitiveType;
import com.github.api.core.refer.ModelReference;
import com.github.api.core.refer.ParameterReference;
import com.github.api.core.refer.Types;
import com.sun.javadoc.ClassDoc;
import com.sun.tools.javadoc.MethodDocImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.*;
import java.util.*;

import static com.github.api.core.refer.DefaultReferContext.*;
import static com.github.api.core.refer.Types.typeNameFor;
import static com.google.common.collect.Lists.newArrayList;

/**
 * DefaultModelProvider
 *
 * @author echils
 * @since 2021-03-17 21:35:12
 */
@Component
public class DefaultModelProvider {


    private static final Logger logger = LoggerFactory.getLogger(DefaultModelProvider.class);


    @Autowired
    private TypeResolver typeResolver;


    private Map<Class, List<ResolvedMethod>> methodsResolvedForHostClasses = new HashMap<>();


    /**
     * build return model
     *
     * @param handlerMethod
     * @param classDocListMap
     * @param methodDoc
     */
    ModelReference returnModelBuild(HandlerMethod handlerMethod,
                                    Map<String, ClassDoc> classDocListMap,
                                    MethodDocImpl methodDoc) {

        Class hostClass = obtainControllerClass(handlerMethod);

        Optional<ResolvedMethod> resolvedMethodOptional
                = matchedMethod(handlerMethod.getMethod(), getMemberMethods(hostClass));

        ResolvedType returnType = resolvedMethodOptional.map(ResolvedMethod::getReturnType)
                .orElse(typeResolver.resolve(Void.TYPE));

        ModelReference modelReference = new ModelReference();
        modelReference.setType(returnType);
        modelReference.setName(typeName(returnType));
        List<RawField> memberFields = returnType.getMemberFields();

        if (!(Types.isBaseType(returnType) || Types.isVoid(returnType) || CollectionUtils.isEmpty(memberFields))) {
            Class<?> erasedType = returnType.getErasedType();
            ClassDoc classDoc = classDocListMap.get(erasedType.getName());
            if (classDoc != null) {
                modelReference.setDescription(classDoc.commentText());
            }
            for (RawField memberField : memberFields) {
                ResolvedType declaringType = memberField.getDeclaringType();
                Field rawMember = memberField.getRawMember();
                ResolvedType resolve = typeResolver.resolve(rawMember.getGenericType());
                ModelReference propertyReference = new ModelReference();
            }
        }
        List<ResolvedType> typeParameters = returnType.getTypeParameters();
        if (!CollectionUtils.isEmpty(typeParameters)) {
            for (ResolvedType typeParameter : typeParameters) {

            }
        }

        return null;
    }


    /**
     * build params model
     *
     * @param handlerMethod
     * @param classDocListMap
     * @param methodDoc
     */
    List<ParameterReference> paramModelBuild(HandlerMethod handlerMethod,
                                             Map<String, ClassDoc> classDocListMap,
                                             MethodDocImpl methodDoc) {


        return null;
    }


    /**
     * Obtain controller class
     *
     * @param handlerMethod
     */
    private Class obtainControllerClass(HandlerMethod handlerMethod) {
        Class<?> beanTypeClass = handlerMethod.getBeanType();
        Class<?> declaringClass = handlerMethod.getMethod().getDeclaringClass();
        if (Proxy.class.isAssignableFrom(beanTypeClass)) {
            return declaringClass;
        }
        if (Class.class.getName().equals(beanTypeClass.getName())) {
            return declaringClass;
        }
        return beanTypeClass;
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


    /**
     * Get all member methods in the class
     *
     * @param hostClass
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


    private String typeName(ResolvedType type) {
        if (isContainerType(type)) {
            return containerType(type);
        }
        return innerTypeName(type);
    }

    private String innerTypeName(ResolvedType type) {
        if (type.getTypeParameters().size() > 0 && type.getErasedType().getTypeParameters().length > 0) {
            return genericTypeName(type);
        }
        return simpleTypeName(type);
    }

    private String simpleTypeName(ResolvedType type) {
        Class<?> erasedType = type.getErasedType();
        if (type instanceof ResolvedPrimitiveType) {
            return typeNameFor(erasedType);
        } else if (erasedType.isEnum()) {
            return "string";
        } else if (type instanceof ResolvedArrayType) {
            return String.format("Array%s%s%s", OPEN,
                    simpleTypeName(type.getArrayElementType()), CLOSE);
        } else if (type instanceof ResolvedObjectType) {
            String typeName = typeNameFor(erasedType);
            if (typeName != null) {
                return typeName;
            }
        }
        return erasedType.getSimpleName();
    }

    private String genericTypeName(ResolvedType resolvedType) {
        Class<?> erasedType = resolvedType.getErasedType();
        String simpleName = Optional.ofNullable(typeNameFor(erasedType)).orElse(erasedType.getSimpleName());
        StringBuilder sb = new StringBuilder(String.format("%s%s", simpleName, OPEN));
        boolean first = true;
        for (int index = 0; index < erasedType.getTypeParameters().length; index++) {
            ResolvedType typeParam = resolvedType.getTypeParameters().get(index);
            if (first) {
                sb.append(innerTypeName(typeParam));
                first = false;
            } else {
                sb.append(String.format("%s%s", DELIM, innerTypeName(typeParam)));
            }
        }
        sb.append(CLOSE);
        return sb.toString();
    }


    /**
     * Determine whether it is a container type
     *
     * @param type
     */
    private boolean isContainerType(ResolvedType type) {
        return List.class.isAssignableFrom(type.getErasedType()) ||
                Set.class.isAssignableFrom(type.getErasedType()) ||
                (Collection.class.isAssignableFrom(type.getErasedType())
                        && !Map.class.isAssignableFrom(type.getErasedType()) ||
                        type.isArray());
    }

    /**
     * Determine  container type
     *
     * @param type
     */
    private String containerType(ResolvedType type) {
        if (List.class.isAssignableFrom(type.getErasedType())) {
            return "List";
        } else if (Set.class.isAssignableFrom(type.getErasedType())) {
            return "Set";
        } else if (type.isArray()) {
            return "Array";
        } else if (Collection.class.isAssignableFrom(type.getErasedType()) && !Map.class.isAssignableFrom(type.getErasedType())) {
            return "List";
        } else {
            throw new UnsupportedOperationException(String.format("Type is not collection type %s", type));
        }
    }


}
