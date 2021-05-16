package com.github.api.core;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeBindings;
import com.fasterxml.classmate.types.ResolvedArrayType;
import com.fasterxml.classmate.types.ResolvedObjectType;
import com.fasterxml.classmate.types.ResolvedPrimitiveType;
import com.google.common.collect.ImmutableMap;
import io.swagger.models.properties.*;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

import static com.google.common.collect.Sets.newHashSet;

/**
 * DefaultReferContext
 *
 * @author echils
 * @since 2021-03-01 21:06:09
 */
class DefaultReferContext {

    private static final String OPEN = "«";
    private static final String CLOSE = "»";
    private static final String DELIMITER = ",";

    /**
     * Get the response   corresponding property
     *
     * @param returnType {@link Type}
     * @return {@link Property}
     */
    static Property getProperty(ResolvedType returnType, Property responseProperty) {

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
                        containerProperty.items(getProperty(resolvedType, new UntypedProperty()));
                    }
                }
                responseProperty = containerProperty;
            } else if (Types.isMapType(returnType)) {
                MapProperty mapProperty = new MapProperty();
                ResolvedType valueResolvedType = typeBindings.getTypeParameters().get(1);
                mapProperty.setAdditionalProperties(getProperty(valueResolvedType, new UntypedProperty()));
                responseProperty = mapProperty;
            } else {
                responseProperty = new RefProperty(typeName);
            }
        }
        return responseProperty;
    }

    /**
     * Get the base type corresponding property
     *
     * @param typeName the base type name
     * @return {@link Property}
     */
    private static Property getBaseProperty(String typeName) {
        if (Arrays.asList("byte", "int", "long", "float", "double", "bigdecimal", "biginteger").contains(typeName)) {
            return new DecimalProperty();
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

        private static final HashSet<Class> ignored = new HashSet<Class>() {{
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
            return baseTypes.contains(typeNameFor(type.getErasedType()));
        }

        static boolean isIgnoredType(ResolvedType type) {
            return ignored.contains(type.getErasedType());
        }

        public static boolean isMapType(ResolvedType type) {
            return Map.class.isAssignableFrom(type.getErasedType());
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

    }

}
