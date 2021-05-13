package com.github.api.core;

import com.fasterxml.classmate.ResolvedType;
import com.google.common.collect.ImmutableMap;
import org.springframework.http.HttpHeaders;
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
public class DefaultReferContext {

    public static final String OPEN = "«";
    public static final String CLOSE = "»";
    public static final String DELIMITER = ",";


    /**
     * default ignorable parameter types
     */
    public static HashSet<Class> ignored;


    static {
        initIgnorableTypes();
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


    /**
     * Param Types
     *
     */
    public static class Types {

        private static final Set<String> baseTypes = newHashSet(
                "int",
                "date",
                "string",
                "double",
                "float",
                "boolean",
                "byte",
                "object",
                "long",
                "date-time",
                "__file",
                "biginteger",
                "bigdecimal",
                "uuid");

        private static final Map<Type, String> typeNameLookup = ImmutableMap.<Type, String>builder()
                .put(Long.TYPE, "long")
                .put(Short.TYPE, "int")
                .put(Integer.TYPE, "int")
                .put(Double.TYPE, "double")
                .put(Float.TYPE, "float")
                .put(Byte.TYPE, "byte")
                .put(Boolean.TYPE, "boolean")
                .put(Character.TYPE, "string")

                .put(Date.class, "date-time")
                .put(java.sql.Date.class, "date")
                .put(String.class, "string")
                .put(Object.class, "object")
                .put(Long.class, "long")
                .put(Integer.class, "int")
                .put(Short.class, "int")
                .put(Double.class, "double")
                .put(Float.class, "float")
                .put(Boolean.class, "boolean")
                .put(Byte.class, "byte")
                .put(BigDecimal.class, "bigdecimal")
                .put(BigInteger.class, "biginteger")
                .put(Currency.class, "string")
                .put(UUID.class, "uuid")
                .put(MultipartFile.class, "__file")
                .build();

        private Types() {
            throw new UnsupportedOperationException();
        }

        public static String typeNameFor(Type type) {
            return typeNameLookup.get(type);
        }

        public static boolean isBaseType(ResolvedType type) {
            return baseTypes.contains(typeNameFor(type.getErasedType()));
        }

        public static boolean isVoid(ResolvedType returnType) {
            return Void.class.equals(returnType.getErasedType()) || Void.TYPE.equals(returnType.getErasedType());
        }

        public static boolean isMapType(ResolvedType type) {
            return Map.class.isAssignableFrom(type.getErasedType());
        }

    }


}
