package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.*;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.tools.doclets.internal.toolkit.Configuration;

import javax.tools.StandardLocation;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.util.*;

public class Util {

    public static ProgramElementDoc[] excludeDeprecatedMembers(
            ProgramElementDoc[] members) {
        return
                toProgramElementDocArray(excludeDeprecatedMembersAsList(members));
    }

    public static List<ProgramElementDoc> excludeDeprecatedMembersAsList(
            ProgramElementDoc[] members) {
        List<ProgramElementDoc> list = new ArrayList<ProgramElementDoc>();
        for (int i = 0; i < members.length; i++) {
            if (members[i].tags("deprecated").length == 0) {
                list.add(members[i]);
            }
        }
        Collections.sort(list);
        return list;
    }

    public static ProgramElementDoc[] toProgramElementDocArray(List<ProgramElementDoc> list) {
        ProgramElementDoc[] pgmarr = new ProgramElementDoc[list.size()];
        for (int i = 0; i < list.size(); i++) {
            pgmarr[i] = list.get(i);
        }
        return pgmarr;
    }

    public static boolean nonPublicMemberFound(ProgramElementDoc[] members) {
        for (int i = 0; i < members.length; i++) {
            if (!members[i].isPublic()) {
                return true;
            }
        }
        return false;
    }

    public static MethodDoc findMethod(ClassDoc cd, MethodDoc method) {
        MethodDoc[] methods = cd.methods();
        for (int i = 0; i < methods.length; i++) {
            if (executableMembersEqual(method, methods[i])) {
                return methods[i];
            }
        }
        return null;
    }

    public static boolean executableMembersEqual(ExecutableMemberDoc member1,
                                                 ExecutableMemberDoc member2) {
        if (!(member1 instanceof MethodDoc && member2 instanceof MethodDoc))
            return false;
        MethodDoc method1 = (MethodDoc) member1;
        MethodDoc method2 = (MethodDoc) member2;
        if (method1.isStatic() && method2.isStatic()) {
            Parameter[] targetParams = method1.parameters();
            Parameter[] currentParams;
            if (method1.name().equals(method2.name()) &&
                    (currentParams = method2.parameters()).length ==
                            targetParams.length) {
                int j;
                for (j = 0; j < targetParams.length; j++) {
                    if (!(targetParams[j].typeName().equals(
                            currentParams[j].typeName()) ||
                            currentParams[j].type() instanceof TypeVariable ||
                            targetParams[j].type() instanceof TypeVariable)) {
                        break;
                    }
                }
                return j == targetParams.length;
            }
            return false;
        } else {
            return method1.overrides(method2) ||
                    method2.overrides(method1) ||
                    member1 == member2;
        }
    }

    public static boolean isCoreClass(ClassDoc cd) {
        return cd.containingClass() == null || cd.isStatic();
    }

    public static boolean matches(ProgramElementDoc doc1,
                                  ProgramElementDoc doc2) {
        if (doc1 instanceof ExecutableMemberDoc &&
                doc2 instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc ed1 = (ExecutableMemberDoc) doc1;
            ExecutableMemberDoc ed2 = (ExecutableMemberDoc) doc2;
            return executableMembersEqual(ed1, ed2);
        } else {
            return doc1.name().equals(doc2.name());
        }
    }

    public static void copyDocFiles(Configuration configuration, PackageDoc pd) {
        copyDocFiles(configuration, DocPath.forPackage(pd).resolve(DocPaths.DOC_FILES));
    }

    public static void copyDocFiles(Configuration configuration, DocPath dir) {
        try {
            boolean first = true;
            for (DocFile f : DocFile.list(configuration, StandardLocation.SOURCE_PATH, dir)) {
                if (!f.isDirectory()) {
                    continue;
                }
                DocFile srcdir = f;
                DocFile destdir = DocFile.createFileForOutput(configuration, dir);
                if (srcdir.isSameFile(destdir)) {
                    continue;
                }
                for (DocFile srcfile : srcdir.list()) {
                    DocFile destfile = destdir.resolve(srcfile.getName());
                    if (srcfile.isFile()) {
                        if (destfile.exists() && !first) {
                            configuration.message.warning((SourcePosition) null,
                                    "doclet.Copy_Overwrite_warning",
                                    srcfile.getPath(), destdir.getPath());
                        } else {
                            configuration.message.notice(
                                    "doclet.Copying_File_0_To_Dir_1",
                                    srcfile.getPath(), destdir.getPath());
                            destfile.copyFile(srcfile);
                        }
                    } else if (srcfile.isDirectory()) {
                        if (configuration.copydocfilesubdirs
                                && !configuration.shouldExcludeDocFileDir(srcfile.getName())) {
                            copyDocFiles(configuration, dir.resolve(srcfile.getName()));
                        }
                    }
                }
                first = false;
            }
        } catch (SecurityException exc) {
            throw new DocletAbortException(exc);
        } catch (IOException exc) {
            throw new DocletAbortException(exc);
        }
    }

    public static List<Type> getAllInterfaces(Type type,
                                              Configuration configuration, boolean sort) {
        Map<ClassDoc, Type> results = sort ? new TreeMap<ClassDoc, Type>() : new LinkedHashMap<ClassDoc, Type>();
        Type[] interfaceTypes = null;
        Type superType = null;
        if (type instanceof ParameterizedType) {
            interfaceTypes = ((ParameterizedType) type).interfaceTypes();
            superType = ((ParameterizedType) type).superclassType();
        } else if (type instanceof ClassDoc) {
            interfaceTypes = ((ClassDoc) type).interfaceTypes();
            superType = ((ClassDoc) type).superclassType();
        } else {
            interfaceTypes = type.asClassDoc().interfaceTypes();
            superType = type.asClassDoc().superclassType();
        }
        for (int i = 0; i < interfaceTypes.length; i++) {
            Type interfaceType = interfaceTypes[i];
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (!(interfaceClassDoc.isPublic() ||
                    (configuration == null ||
                            isLinkable(interfaceClassDoc, configuration)))) {
                continue;
            }
            results.put(interfaceClassDoc, interfaceType);
            List<Type> superInterfaces = getAllInterfaces(interfaceType, configuration, sort);
            for (Iterator<Type> iter = superInterfaces.iterator(); iter.hasNext(); ) {
                Type t = iter.next();
                results.put(t.asClassDoc(), t);
            }
        }
        if (superType == null)
            return new ArrayList<Type>(results.values());

        addAllInterfaceTypes(results,
                superType,
                interfaceTypesOf(superType),
                false, configuration);
        List<Type> resultsList = new ArrayList<Type>(results.values());
        if (sort) {
            Collections.sort(resultsList, new TypeComparator());
        }
        return resultsList;
    }

    private static Type[] interfaceTypesOf(Type type) {
        if (type instanceof AnnotatedType)
            type = ((AnnotatedType) type).underlyingType();
        return type instanceof ClassDoc ?
                ((ClassDoc) type).interfaceTypes() :
                ((ParameterizedType) type).interfaceTypes();
    }

    public static List<Type> getAllInterfaces(Type type, Configuration configuration) {
        return getAllInterfaces(type, configuration, true);
    }

    private static void findAllInterfaceTypes(Map<ClassDoc, Type> results, ClassDoc c, boolean raw,
                                              Configuration configuration) {
        Type superType = c.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                interfaceTypesOf(superType),
                raw, configuration);
    }

    private static void findAllInterfaceTypes(Map<ClassDoc, Type> results, ParameterizedType p,
                                              Configuration configuration) {
        Type superType = p.superclassType();
        if (superType == null)
            return;
        addAllInterfaceTypes(results, superType,
                interfaceTypesOf(superType),
                false, configuration);
    }

    private static void addAllInterfaceTypes(Map<ClassDoc, Type> results, Type type,
                                             Type[] interfaceTypes, boolean raw,
                                             Configuration configuration) {
        for (int i = 0; i < interfaceTypes.length; i++) {
            Type interfaceType = interfaceTypes[i];
            ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (!(interfaceClassDoc.isPublic() ||
                    (configuration != null &&
                            isLinkable(interfaceClassDoc, configuration)))) {
                continue;
            }
            if (raw)
                interfaceType = interfaceType.asClassDoc();
            results.put(interfaceClassDoc, interfaceType);
            List<Type> superInterfaces = getAllInterfaces(interfaceType, configuration);
            for (Iterator<Type> iter = superInterfaces.iterator(); iter.hasNext(); ) {
                Type superInterface = iter.next();
                results.put(superInterface.asClassDoc(), superInterface);
            }
        }
        if (type instanceof AnnotatedType)
            type = ((AnnotatedType) type).underlyingType();
        if (type instanceof ParameterizedType)
            findAllInterfaceTypes(results, (ParameterizedType) type, configuration);
        else if (((ClassDoc) type).typeParameters().length == 0)
            findAllInterfaceTypes(results, (ClassDoc) type, raw, configuration);
        else
            findAllInterfaceTypes(results, (ClassDoc) type, true, configuration);
    }

    public static String quote(String filepath) {
        return ("\"" + filepath + "\"");
    }

    public static String getPackageName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
                DocletConstants.DEFAULT_PACKAGE_NAME : packageDoc.name();
    }

    public static String getPackageFileHeadName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
                DocletConstants.DEFAULT_PACKAGE_FILE_NAME : packageDoc.name();
    }

    public static String replaceText(String originalStr, String oldStr,
                                     String newStr) {
        if (oldStr == null || newStr == null || oldStr.equals(newStr)) {
            return originalStr;
        }
        return originalStr.replace(oldStr, newStr);
    }

    public static boolean isDocumentedAnnotation(AnnotationTypeDoc annotationDoc) {
        AnnotationDesc[] annotationDescList = annotationDoc.annotations();
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                    java.lang.annotation.Documented.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeclarationTarget(AnnotationDesc targetAnno) {

        ElementValuePair[] elems = targetAnno.elementValues();
        if (elems == null
                || elems.length != 1
                || !"value".equals(elems[0].element().name())
                || !(elems[0].value().value() instanceof AnnotationValue[]))
            return true;
        AnnotationValue[] values = (AnnotationValue[]) elems[0].value().value();
        for (int i = 0; i < values.length; i++) {
            Object value = values[i].value();
            if (!(value instanceof FieldDoc))
                return true;
            FieldDoc eValue = (FieldDoc) value;
            if (Util.isJava5DeclarationElementType(eValue)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDeclarationAnnotation(AnnotationTypeDoc annotationDoc,
                                                  boolean isJava5DeclarationLocation) {
        if (!isJava5DeclarationLocation)
            return false;
        AnnotationDesc[] annotationDescList = annotationDoc.annotations();

        if (annotationDescList.length == 0)
            return true;
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                    java.lang.annotation.Target.class.getName())) {
                if (isDeclarationTarget(annotationDescList[i]))
                    return true;
            }
        }
        return false;
    }

    public static boolean isLinkable(ClassDoc classDoc,
                                     Configuration configuration) {
        return
                ((classDoc.isIncluded() && configuration.isGeneratedDoc(classDoc))) ||
                        (configuration.extern.isExternal(classDoc) &&
                                (classDoc.isPublic() || classDoc.isProtected()));
    }

    public static Type getFirstVisibleSuperClass(ClassDoc classDoc,
                                                 Configuration configuration) {
        if (classDoc == null) {
            return null;
        }
        Type sup = classDoc.superclassType();
        ClassDoc supClassDoc = classDoc.superclass();
        while (sup != null &&
                (!(supClassDoc.isPublic() ||
                        isLinkable(supClassDoc, configuration)))) {
            if (supClassDoc.superclass().qualifiedName().equals(supClassDoc.qualifiedName()))
                break;
            sup = supClassDoc.superclassType();
            supClassDoc = supClassDoc.superclass();
        }
        if (classDoc.equals(supClassDoc)) {
            return null;
        }
        return sup;
    }

    public static ClassDoc getFirstVisibleSuperClassCD(ClassDoc classDoc,
                                                       Configuration configuration) {
        if (classDoc == null) {
            return null;
        }
        ClassDoc supClassDoc = classDoc.superclass();
        while (supClassDoc != null &&
                (!(supClassDoc.isPublic() ||
                        isLinkable(supClassDoc, configuration)))) {
            supClassDoc = supClassDoc.superclass();
        }
        if (classDoc.equals(supClassDoc)) {
            return null;
        }
        return supClassDoc;
    }

    public static String getTypeName(Configuration config,
                                     ClassDoc cd, boolean lowerCaseOnly) {
        String typeName = "";
        if (cd.isOrdinaryClass()) {
            typeName = "doclet.Class";
        } else if (cd.isInterface()) {
            typeName = "doclet.Interface";
        } else if (cd.isException()) {
            typeName = "doclet.Exception";
        } else if (cd.isError()) {
            typeName = "doclet.Error";
        } else if (cd.isAnnotationType()) {
            typeName = "doclet.AnnotationType";
        } else if (cd.isEnum()) {
            typeName = "doclet.Enum";
        }
        return config.getText(
                lowerCaseOnly ? typeName.toLowerCase() : typeName);
    }

    public static String replaceTabs(Configuration configuration, String text) {
        if (text.indexOf("\t") == -1)
            return text;
        final int tabLength = configuration.sourcetab;
        final String whitespace = configuration.tabSpaces;
        final int textLength = text.length();
        StringBuilder result = new StringBuilder(textLength);
        int pos = 0;
        int lineLength = 0;
        for (int i = 0; i < textLength; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\n':
                case '\r':
                    lineLength = 0;
                    break;
                case '\t':
                    result.append(text, pos, i);
                    int spaceCount = tabLength - lineLength % tabLength;
                    result.append(whitespace, 0, spaceCount);
                    lineLength += spaceCount;
                    pos = i + 1;
                    break;
                default:
                    lineLength++;
            }
        }
        result.append(text, pos, textLength);
        return result.toString();
    }

    public static String normalizeNewlines(String text) {
        StringBuilder sb = new StringBuilder();
        final int textLength = text.length();
        final String NL = DocletConstants.NL;
        int pos = 0;
        for (int i = 0; i < textLength; i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\n':
                    sb.append(text, pos, i);
                    sb.append(NL);
                    pos = i + 1;
                    break;
                case '\r':
                    sb.append(text, pos, i);
                    sb.append(NL);
                    if (i + 1 < textLength && text.charAt(i + 1) == '\n')
                        i++;
                    pos = i + 1;
                    break;
            }
        }
        sb.append(text, pos, textLength);
        return sb.toString();
    }

    public static void setEnumDocumentation(Configuration configuration,
                                            ClassDoc classDoc) {
        MethodDoc[] methods = classDoc.methods();
        for (int j = 0; j < methods.length; j++) {
            MethodDoc currentMethod = methods[j];
            if (currentMethod.name().equals("values") &&
                    currentMethod.parameters().length == 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(configuration.getText("doclet.enum_values_doc.main", classDoc.name()));
                sb.append("\n@return ");
                sb.append(configuration.getText("doclet.enum_values_doc.return"));
                currentMethod.setRawCommentText(sb.toString());
            } else if (currentMethod.name().equals("valueOf") &&
                    currentMethod.parameters().length == 1) {
                Type paramType = currentMethod.parameters()[0].type();
                if (paramType != null &&
                        paramType.qualifiedTypeName().equals(String.class.getName())) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(configuration.getText("doclet.enum_valueof_doc.main", classDoc.name()));
                    sb.append("\n@param name ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.param_name"));
                    sb.append("\n@return ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.return"));
                    sb.append("\n@throws IllegalArgumentException ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.throws_ila"));
                    sb.append("\n@throws NullPointerException ");
                    sb.append(configuration.getText("doclet.enum_valueof_doc.throws_npe"));
                    currentMethod.setRawCommentText(sb.toString());
                }
            }
        }
    }

    public static boolean isDeprecated(Doc doc) {
        if (doc.tags("deprecated").length > 0) {
            return true;
        }
        AnnotationDesc[] annotationDescList;
        if (doc instanceof PackageDoc)
            annotationDescList = ((PackageDoc) doc).annotations();
        else
            annotationDescList = ((ProgramElementDoc) doc).annotations();
        for (int i = 0; i < annotationDescList.length; i++) {
            if (annotationDescList[i].annotationType().qualifiedName().equals(
                    Deprecated.class.getName())) {
                return true;
            }
        }
        return false;
    }

    public static String propertyNameFromMethodName(String name) {
        String propertyName = null;
        if (name.startsWith("get") || name.startsWith("set")) {
            propertyName = name.substring(3);
        } else if (name.startsWith("is")) {
            propertyName = name.substring(2);
        }
        if ((propertyName == null) || propertyName.isEmpty()) {
            return "";
        }
        return propertyName.substring(0, 1).toLowerCase()
                + propertyName.substring(1);
    }

    public static ClassDoc[] filterOutPrivateClasses(final ClassDoc[] classes,
                                                     boolean javafx) {
        if (!javafx) {
            return classes;
        }
        final List<ClassDoc> filteredOutClasses =
                new ArrayList<ClassDoc>(classes.length);
        for (ClassDoc classDoc : classes) {
            if (classDoc.isPrivate() || classDoc.isPackagePrivate()) {
                continue;
            }
            Tag[] aspTags = classDoc.tags("treatAsPrivate");
            if (aspTags != null && aspTags.length > 0) {
                continue;
            }
            filteredOutClasses.add(classDoc);
        }
        return filteredOutClasses.toArray(new ClassDoc[0]);
    }

    public static boolean isJava5DeclarationElementType(FieldDoc elt) {
        return elt.name().contentEquals(ElementType.ANNOTATION_TYPE.name()) ||
                elt.name().contentEquals(ElementType.CONSTRUCTOR.name()) ||
                elt.name().contentEquals(ElementType.FIELD.name()) ||
                elt.name().contentEquals(ElementType.LOCAL_VARIABLE.name()) ||
                elt.name().contentEquals(ElementType.METHOD.name()) ||
                elt.name().contentEquals(ElementType.PACKAGE.name()) ||
                elt.name().contentEquals(ElementType.PARAMETER.name()) ||
                elt.name().contentEquals(ElementType.TYPE.name());
    }

    private static class TypeComparator implements Comparator<Type> {
        public int compare(Type type1, Type type2) {
            return type1.qualifiedTypeName().toLowerCase().compareTo(
                    type2.qualifiedTypeName().toLowerCase());
        }
    }
}
