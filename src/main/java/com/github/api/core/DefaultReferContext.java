package com.github.api.core;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeBindings;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.members.RawField;
import com.fasterxml.classmate.types.ResolvedArrayType;
import com.fasterxml.classmate.types.ResolvedObjectType;
import com.fasterxml.classmate.types.ResolvedPrimitiveType;
import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.FieldDoc;
import com.google.common.collect.ImmutableMap;
import io.swagger.models.ArrayModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.parameters.*;
import io.swagger.models.properties.*;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.github.api.ApiDocumentContext.CLASS_DOC_MAP;
import static com.google.common.collect.Sets.newHashSet;
import static io.swagger.models.ModelImpl.OBJECT;
import static org.springframework.web.bind.annotation.ValueConstants.DEFAULT_NONE;

/**
 * DefaultReferContext
 *
 * @author echils
 */
class DefaultReferContext {

    private static final Logger logger = LoggerFactory.getLogger(DefaultReferContext.class);

    //The symbol represented before the transfer: "«";
    private static final String OPEN = "Of";
    //The symbol represented before the transfer:  "»";
    private static final String CLOSE = "";
    //The symbol represented before the transfer:  ",";
    private static final String DELIMITER = "And";
    //The class tag
    private static final String JAVA_CLASS = "class";

    //Stores the association type name
    private static final Map<String, ResolvedType> refTypeNameCache = new HashMap<>();

    //Default type resolver
    private static final TypeResolver typeResolver = new TypeResolver();

    /**
     * Build the class definitions of the method
     *
     * @return the related class definitions
     */
    static Map<String, Model> definitions() {

        Map<String, Model> definitionMap = new ConcurrentHashMap<>();
        Map<String, ClassDoc> classDocMap = CLASS_DOC_MAP.values().parallelStream()
                .collect(Collectors.toMap(ClassDoc::typeName, classDoc -> classDoc));

        while (refTypeNameCache.size() != 0 && definitionMap.size() != refTypeNameCache.size()) {
            refTypeNameCache.keySet().stream().filter(definitionMap::containsKey)
                    .collect(Collectors.toSet()).forEach(refTypeNameCache::remove);
            HashMap<String, ResolvedType> refTypeCacheCenter = new HashMap<>(refTypeNameCache);
            for (Map.Entry<String, ResolvedType> resolvedTypeEntry : refTypeCacheCenter.entrySet()) {
                String typeName = resolvedTypeEntry.getKey();
                ModelImpl model = new ModelImpl();
                model.setType(OBJECT);
                model.setName(typeName);
                ResolvedType resolvedType = resolvedTypeEntry.getValue();
                Map<String, String> fieldCommentMap = new HashMap<>();

                ResolvedType parentResolvedType = resolvedType.getParentClass();
                while (parentResolvedType != null && parentResolvedType.getErasedType() != Object.class) {
                    Map<String, String> parentFieldCommentMap = new HashMap<>();
                    String parentTypeName = Types.typeName(parentResolvedType);
                    if (classDocMap.containsKey(parentTypeName)) {
                        ClassDoc parentClassDoc = classDocMap.get(parentTypeName);
                        parentFieldCommentMap.putAll(Arrays.stream(parentClassDoc.fields(false))
                                .collect(Collectors.toMap(FieldDoc::name, FieldDoc::commentText)));
                    }
                    List<RawField> parentMemberFields = parentResolvedType.getMemberFields();
                    for (RawField parentMemberField : parentMemberFields) {
                        Property property = convertFieldProperty(parentMemberField);
                        property.description(parentFieldCommentMap.get(parentMemberField.getName()));
                        model.addProperty(parentMemberField.getName(), property);
                    }
                    parentResolvedType = parentResolvedType.getParentClass();
                }

                List<RawField> memberFields = resolvedType.getMemberFields();
                if (!CollectionUtils.isEmpty(memberFields)) {
                    ClassDoc classDoc = null;
                    if (classDocMap.containsKey(typeName)) {
                        classDoc = classDocMap.get(typeName);
                    } else {
                        TypeBindings typeBindings = resolvedType.getTypeBindings();
                        if (typeBindings != null && !CollectionUtils.isEmpty(typeBindings.getTypeParameters())) {
                            String externalTypeName = Types.typeName(typeResolver.resolve(resolvedType.getErasedType()));
                            if (!StringUtils.isEmpty(externalTypeName) && classDocMap.containsKey(externalTypeName)) {
                                classDoc = classDocMap.get(externalTypeName);
                            }
                        }
                    }
                    if (classDoc != null) {
                        model.setDescription(classDoc.commentText());
                        fieldCommentMap.putAll(Arrays.stream(classDoc.fields(false))
                                .collect(Collectors.toMap(FieldDoc::name, FieldDoc::commentText)));
                    }
                    for (RawField memberField : memberFields) {
                        Property property = convertFieldProperty(memberField);
                        property.description(fieldCommentMap.get(memberField.getName()));
                        model.addProperty(memberField.getName(), property);
                    }
                }
                definitionMap.put(typeName, model);
            }
        }
        return definitionMap;
    }


    /**
     * Parse field to property
     *
     * @param rawField {@link RawField}
     */
    private static Property convertFieldProperty(RawField rawField) {
        Property property;
        ResolvableType resolvableType = ResolvableType.forField(rawField.getRawMember());
        ResolvedType memberResolvedType = typeResolver.resolve(resolvableType.getType());

        //Whether to refer to an internal class of the project
        String memberResolvedTypeName = Types.typeName(memberResolvedType);
        if (refTypeNameCache.containsKey(memberResolvedTypeName)) {
            return new RefProperty(memberResolvedTypeName);
        }

        //Maybe generic type
        ResolvableType[] generics = resolvableType.getGenerics();
        if (Types.isContainerType(memberResolvedType) || generics.length == 0) {
            Type genericType = rawField.getRawMember().getGenericType();
            if (!genericType.toString().contains(JAVA_CLASS) && !Types.isBaseType(genericType)) {
                TypeBindings typeBindings = rawField.getDeclaringType().getTypeBindings();
                List<ResolvedType> typeParameters = typeBindings.getTypeParameters();
                if (!CollectionUtils.isEmpty(typeParameters)) {
                    memberResolvedType = typeParameters.get(0);
                }
            }
        } else if (generics.length == 1 && generics[0].getType().toString().contains(JAVA_CLASS)
                && !Types.isBaseType(generics[0].getType())) {
            memberResolvedType = typeResolver.resolve(generics[0].getType());
        }

        Property fieldProperty = getProperty(memberResolvedType);
        if (fieldProperty instanceof StringProperty) {
            Class<?> erasedType = memberResolvedType.getErasedType();
            if (erasedType.isEnum()) {
                ClassDoc classDoc = CLASS_DOC_MAP.get(erasedType.getName());
                if (classDoc == null) {
                    ((StringProperty) fieldProperty)._enum(Arrays.stream(erasedType.getFields())
                            .map(Field::getName).collect(Collectors.toList()));
                } else {
                    ((StringProperty) fieldProperty)._enum(getEnumValue(erasedType, classDoc));
                }
            }
        }
        property = fieldProperty;
        return property;
    }


    /**
     * Get the request parameter
     *
     * @param argumentType    param type
     * @param methodParameter {@link MethodParameter}
     * @return {@link Annotation}
     */
    static Parameter getParameter(MethodParameter methodParameter, ResolvedType argumentType) {

        Annotation[] parameterAnnotations = methodParameter.getParameterAnnotations();
        if (ArrayUtils.isEmpty(parameterAnnotations)) {
            return null;
        }
        java.lang.reflect.Parameter reflectParameter = getReflectParameter(methodParameter);
        for (Annotation parameterAnnotation : parameterAnnotations) {
            Class<? extends Annotation> annotationType = parameterAnnotation.annotationType();
            if (annotationType.isAssignableFrom(PathVariable.class)) {
                PathParameter pathParameter = new PathParameter();
                if (reflectParameter != null) {
                    PathVariable annotation = reflectParameter.getAnnotation(PathVariable.class);
                    String name = StringUtils.isEmpty(annotation.value()) ? annotation.name() : annotation.value();
                    pathParameter.setName(name);
                    pathParameter.setRequired(annotation.required());
                    String parameterType = getParameterType(argumentType);
                    if (StringUtils.isEmpty(parameterType)) {
                        pathParameter.setType(Types.Common.STRING.getValue());
                    } else {
                        pathParameter.setType(parameterType);
                    }
                    if (Types.Common.ARRAY.getValue().equals(parameterType)) {
                        pathParameter.setItems(new StringProperty());
                    }
                    if (argumentType.getErasedType().isEnum()) {
                        pathParameter._enum(Arrays.stream(argumentType.getErasedType()
                                .getFields()).map(Field::getName).collect(Collectors.toList()));
                    }
                }
                return pathParameter;
            } else if (annotationType.isAssignableFrom(RequestBody.class)) {
                if (Types.isFileType(argumentType) || Types.isListOfFiles(argumentType)) {
                    FormParameter formParameter = new FormParameter();
                    formParameter.type(Types.Common.FILE.getValue());
                    if (reflectParameter != null) {
                        RequestBody annotation = reflectParameter.getAnnotation(RequestBody.class);
                        if (annotation != null) {
                            formParameter.setRequired(annotation.required());
                        }
                    }
                    return formParameter;
                }
                BodyParameter bodyParameter = new BodyParameter();
                if (reflectParameter != null) {
                    RequestBody annotation = reflectParameter.getAnnotation(RequestBody.class);
                    if (annotation != null) {
                        bodyParameter.setRequired(annotation.required());
                    }
                    String parameterType = getParameterType(argumentType);
                    if (StringUtils.isEmpty(parameterType)) {
                        RefModel refModel = new RefModel();
                        String refTypeName = Types.typeName(argumentType);
                        refModel.set$ref(refTypeName);
                        refTypeNameCache.put(refTypeName, argumentType);
                        bodyParameter.schema(refModel);
                    } else if (parameterType.equals(Types.Common.ARRAY.getValue())) {
                        ArrayModel model = new ArrayModel();
                        ResolvedType arrayElementType = argumentType.getArrayElementType();
                        if (arrayElementType == null) {
                            List<ResolvedType> typeParameters = argumentType.getTypeBindings().getTypeParameters();
                            if (!CollectionUtils.isEmpty(typeParameters)) {
                                ResolvedType resolvedType = typeParameters.get(0);
                                model.items(getProperty(resolvedType));
                            }
                        } else {
                            model.items(getProperty(arrayElementType));
                        }
                        bodyParameter.schema(model);
                    } else {
                        ModelImpl model = new ModelImpl();
                        model.setType(parameterType);
                        bodyParameter.schema(model);
                    }
                }
                return bodyParameter;
            } else if (annotationType.isAssignableFrom(RequestParam.class)) {
                if (Types.isFileType(argumentType) || Types.isListOfFiles(argumentType)) {
                    FormParameter formParameter = new FormParameter();
                    formParameter.type(Types.Common.FILE.getValue());
                    if (reflectParameter != null) {
                        RequestParam annotation = reflectParameter.getAnnotation(RequestParam.class);
                        if (annotation != null) {
                            formParameter.setRequired(annotation.required());
                            if (!DEFAULT_NONE.equals(annotation.defaultValue())) {
                                formParameter.setDefaultValue(annotation.defaultValue());
                            }
                        }
                    }
                    return formParameter;
                }
                QueryParameter queryParameter = new QueryParameter();
                if (reflectParameter != null) {
                    RequestParam annotation = reflectParameter.getAnnotation(RequestParam.class);
                    if (annotation != null) {
                        queryParameter.setRequired(annotation.required());
                        if (!annotation.defaultValue().equals(DEFAULT_NONE)) {
                            queryParameter.setDefaultValue(annotation.defaultValue());
                        }
                        String name = StringUtils.isEmpty(annotation.value()) ? annotation.name() : annotation.value();
                        queryParameter.setName(name);
                        if (!DEFAULT_NONE.equals(annotation.defaultValue())) {
                            queryParameter.setDefaultValue(annotation.defaultValue());
                        }
                        String parameterType = getParameterType(argumentType);
                        if (StringUtils.isEmpty(parameterType)) {
                            queryParameter.type(Types.Common.STRING.getValue());
                        } else if (parameterType.equals(Types.Common.ARRAY.getValue())) {
                            queryParameter.setType(parameterType);
                            StringProperty stringProperty = new StringProperty();
                            if (argumentType.isArray()) {
                                Class<?> erasedType = argumentType.getArrayElementType().getErasedType();
                                if (erasedType.isEnum()) {
                                    stringProperty._enum(Arrays.stream(erasedType.getFields())
                                            .map(Field::getName).collect(Collectors.toList()));
                                }
                            }
                            List<ResolvedType> typeParameters = argumentType.getTypeBindings().getTypeParameters();
                            if (!CollectionUtils.isEmpty(typeParameters) && typeParameters.size() == 1) {
                                Class<?> erasedType = typeParameters.get(0).getErasedType();
                                if (erasedType.isEnum()) {
                                    stringProperty._enum(Arrays.stream(erasedType.getFields())
                                            .map(Field::getName).collect(Collectors.toList()));
                                }
                            }
                            queryParameter.setItems(stringProperty);
                        } else {
                            queryParameter.setType(parameterType);
                        }
                        if (argumentType.getErasedType().isEnum()) {
                            Class<?> erasedType = argumentType.getErasedType();
                            queryParameter._enum(Arrays.stream(erasedType.getFields())
                                    .map(Field::getName).collect(Collectors.toList()));
                        }
                    }
                }
                return queryParameter;
            }
        }
        logger.warn("This version does not support the annotation {} at this time",Arrays.toString(parameterAnnotations));
        return null;
    }

    /**
     * Get the enum value,not support inner class
     *
     * @param enumClass the enum class
     * @return the list of enum value
     */
    private static List<String> getEnumValue(Class<?> enumClass, ClassDoc classDoc) {
        List<String> enumValues = new ArrayList<>();
        if (classDoc != null) {
            FieldDoc[] fieldDocs = classDoc.fields();
            for (Field field : enumClass.getFields()) {
                String enumValue = field.getName();
                if (fieldDocs != null && fieldDocs.length != 0) {
                    for (FieldDoc fieldDoc : fieldDocs) {
                        if (enumValue.equals(fieldDoc.name())) {
                            if (!StringUtils.isEmpty(fieldDoc.commentText())) {
                                enumValue = String.format("%s (%s)", enumValue, fieldDoc.commentText());
                            }
                            break;
                        }
                    }
                }
                enumValues.add(enumValue);
            }
        }
        return enumValues;
    }

    /**
     * Get the param name
     *
     * @param methodParameter {@link MethodParameter}
     * @return the param name
     */
    static java.lang.reflect.Parameter getReflectParameter(MethodParameter methodParameter) {

        Method method = methodParameter.getMethod();
        if (method != null && ArrayUtils.isNotEmpty(method.getParameters())) {
            return method.getParameters()[methodParameter.getParameterIndex()];
        }
        return null;
    }

    /**
     * Get the response corresponding property
     *
     * @param returnType {@link Type}
     * @return {@link Property}
     */
    static Property getProperty(ResolvedType returnType) {
        Property responseProperty = new UntypedProperty();
        if (returnType != null && !Types.isIgnoredType(returnType)) {
            String typeName = Types.typeName(returnType);
            TypeBindings typeBindings = returnType.getTypeBindings();
            if (Types.isBaseType(returnType)) {
                responseProperty = getBaseProperty(typeName);
            } else if (Types.isContainerType(returnType)) {
                ArrayProperty containerProperty = new ArrayProperty();
                //Array Type: int[],object[]
                if (returnType.isArray()) {
                    ResolvedType elementType = returnType.getArrayElementType();
                    String elementTypeName = Types.typeName(elementType);
                    if (Types.isBaseType(elementType)) {
                        containerProperty.items(getBaseProperty(elementTypeName));
                    } else {
                        refTypeNameCache.put(typeName, returnType);
                        containerProperty.items(new RefProperty(elementTypeName));
                    }
                } else {
                    //Collection type: list,set
                    List<ResolvedType> typeParameters = typeBindings.getTypeParameters();
                    //Generics belong to the default type: List list=new ArrayList();
                    if (CollectionUtils.isEmpty(typeParameters)) {
                        containerProperty.items(new ObjectProperty());
                    } else {
                        ResolvedType resolvedType = typeParameters.get(0);
                        containerProperty.items(getProperty(resolvedType));
                    }
                }
                responseProperty = containerProperty;
            } else if (Types.isMapType(returnType)) {
                MapProperty mapProperty = new MapProperty();
                if (!CollectionUtils.isEmpty(typeBindings.getTypeParameters())) {
                    ResolvedType valueResolvedType = typeBindings.getTypeParameters().get(1);
                    mapProperty.setAdditionalProperties(getProperty(valueResolvedType));
                }
                responseProperty = mapProperty;
            } else {
                if (typeName.equals(Types.Common.STRING.getValue())) {
                    responseProperty = new StringProperty();
                } else {
                    refTypeNameCache.put(typeName, returnType);
                    responseProperty = new RefProperty(typeName);
                }

            }
        }
        return responseProperty;
    }

    /**
     * Get the parameter type
     *
     * @param argumentType {@link ResolvedType}
     * @return the parameter type
     */
    private static String getParameterType(ResolvedType argumentType) {

        if (Types.isContainerType(argumentType)) {
            return "array";
        }
        if (argumentType.getErasedType().isEnum()) {
            return "string";
        }
        String typeName = Types.typeName(argumentType);
        if (Arrays.asList("float", "double", "bigdecimal").contains(typeName)) {
            return "number";
        } else if (Arrays.asList("byte", "int", "long", "biginteger").contains(typeName)) {
            return "integer";
        } else if ("boolean".equals(typeName)) {
            return "boolean";
        } else if ("string".equals(typeName)) {
            return "string";
        } else if ("object".equals(typeName)) {
            return "object";
        }
        return null;
    }

    /**
     * Get the base type corresponding property
     *
     * @param typeName the base type name
     * @return {@link Property}
     */
    private static Property getBaseProperty(String typeName) {

        if (Arrays.asList("float", "double", "bigdecimal").contains(typeName)) {
            return new DecimalProperty();
        } else if (Arrays.asList("byte", "int", "long", "biginteger").contains(typeName)) {
            return new IntegerProperty();
        } else if ("boolean".equals(typeName)) {
            return new BooleanProperty();
        } else if ("string".equals(typeName)) {
            return new StringProperty();
        } else if ("object".equals(typeName)) {
            return new ObjectProperty();
        } else if ("date".equals(typeName)) {
            return new DateTimeProperty();
        } else if ("__file".equals(typeName)) {
            return new FileProperty();
        } else if ("uuid".equals(typeName)) {
            return new UUIDProperty();
        } else {
            return new UntypedProperty();
        }
    }

    /**
     * Param Types
     */
    static class Types {

        private static final HashSet<Class<?>> ignored = new HashSet<Class<?>>() {{
            add(ServletRequest.class);
            add(Class.class);
            add(Void.class);
            add(Void.TYPE);
            add(HttpServletRequest.class);
            add(HttpServletResponse.class);
            add(HttpHeaders.class);
            add(BindingResult.class);
            add(ServletContext.class);
            add(UriComponentsBuilder.class);
        }};
        private static final Set<String> baseTypes = newHashSet(
                "byte",
                "int",
                "long",
                "float",
                "double",
                "boolean",
                "string",
                "object",
                "date",
                "__file",
                "biginteger",
                "bigdecimal",
                "uuid");
        private static final Map<Type, String> typeNameLookup = ImmutableMap.<Type, String>builder()
                .put(Byte.TYPE, "byte")
                .put(Short.TYPE, "int")
                .put(Integer.TYPE, "int")
                .put(Long.TYPE, "long")
                .put(Float.TYPE, "float")
                .put(Double.TYPE, "double")
                .put(Boolean.TYPE, "boolean")
                .put(Character.TYPE, "string")

                .put(Character.class, "string")
                .put(Byte.class, "byte")
                .put(Short.class, "int")
                .put(Integer.class, "int")
                .put(Long.class, "long")
                .put(Float.class, "float")
                .put(Double.class, "double")
                .put(Boolean.class, "boolean")
                .put(String.class, "string")
                .put(Currency.class, "string")

                .put(Date.class, "date")
                .put(java.sql.Date.class, "date")
                .put(Object.class, "object")
                .put(BigDecimal.class, "bigdecimal")
                .put(BigInteger.class, "biginteger")
                .put(UUID.class, "uuid")
                .put(MultipartFile.class, "__file")
                .build();

        private Types() {
            throw new UnsupportedOperationException();
        }

        static String typeNameFor(Type type) {
            return typeNameLookup.get(type);
        }

        static boolean isBaseType(ResolvedType type) {
            return isBaseType(type.getErasedType());
        }

        static boolean isBaseType(Class<?> clazz) {
            return baseTypes.contains(typeNameFor(clazz));
        }

        static boolean isBaseType(Type type) {
            return baseTypes.contains(typeNameFor(type));
        }

        static boolean isIgnoredType(ResolvedType type) {
            return isIgnoredType(type.getErasedType());
        }

        static boolean isIgnoredType(Class<?> clazz) {
            return ignored.contains(clazz);
        }

        static boolean isMapType(ResolvedType type) {
            return isMapType(type.getErasedType());
        }

        static boolean isMapType(Class<?> clazz) {
            return Map.class.isAssignableFrom(clazz);
        }

        static boolean isListOfFiles(ResolvedType parameterType) {
            return isContainerType(parameterType) && isFileType(collectionElementType(parameterType));
        }

        static boolean isFileType(ResolvedType parameterType) {
            if (parameterType == null) {
                return false;
            }
            return MultipartFile.class.isAssignableFrom(parameterType.getErasedType());
        }

        static boolean isContainerType(ResolvedType type) {
            return List.class.isAssignableFrom(type.getErasedType()) ||
                    Set.class.isAssignableFrom(type.getErasedType()) ||
                    ((Collection.class.isAssignableFrom(type.getErasedType()) && !Map.class.isAssignableFrom(type.getErasedType())) ||
                            type.isArray());
        }

        static String containerType(ResolvedType type) {
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

        static String typeName(ResolvedType type) {
            if (Types.isContainerType(type)) {
                return Types.containerType(type);
            }
            return innerTypeName(type);
        }

        static String innerTypeName(ResolvedType type) {
            if (type.getTypeParameters().size() > 0 && type.getErasedType().getTypeParameters().length > 0) {
                return genericTypeName(type);
            }
            return simpleTypeName(type);
        }

        static String simpleTypeName(ResolvedType type) {
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

        static String genericTypeName(ResolvedType resolvedType) {
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
                    sb.append(String.format("%s%s", DELIMITER, innerTypeName(typeParam)));
                }
            }
            sb.append(CLOSE);
            return sb.toString();
        }

        static ResolvedType collectionElementType(ResolvedType type) {
            if (List.class.isAssignableFrom(type.getErasedType())) {
                return elementType(type, List.class);
            } else if (Set.class.isAssignableFrom(type.getErasedType())) {
                return elementType(type, Set.class);
            } else if (type.isArray()) {
                return type.getArrayElementType();
            } else if ((Collection.class.isAssignableFrom(type.getErasedType()) && !Map.class.isAssignableFrom(type.getErasedType()))) {
                return elementType(type, Collection.class);
            } else {
                return null;
            }
        }

        private static <T extends Collection<?>> ResolvedType elementType(ResolvedType container, Class<T> collectionType) {
            List<ResolvedType> resolvedTypes = container.typeParametersFor(collectionType);
            if (resolvedTypes.size() == 1) {
                return resolvedTypes.get(0);
            }
            return new TypeResolver().resolve(Object.class);
        }

        enum Common {

            STRING("string"),
            ARRAY("array"),
            FILE("file");

            private final String value;

            Common(String value) {
                this.value = value;
            }

            public String getValue() {
                return value;
            }
        }

    }

}
