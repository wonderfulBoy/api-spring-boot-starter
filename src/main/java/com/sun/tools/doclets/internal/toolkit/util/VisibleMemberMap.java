package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Configuration;

import java.util.*;
import java.util.regex.Pattern;

public class VisibleMemberMap {
    public static final int INNERCLASSES = 0;
    public static final int ENUM_CONSTANTS = 1;
    public static final int FIELDS = 2;
    public static final int CONSTRUCTORS = 3;
    public static final int METHODS = 4;
    public static final int ANNOTATION_TYPE_FIELDS = 5;
    public static final int ANNOTATION_TYPE_MEMBER_OPTIONAL = 6;
    public static final int ANNOTATION_TYPE_MEMBER_REQUIRED = 7;
    public static final int PROPERTIES = 8;
    public static final int NUM_MEMBER_TYPES = 9;
    public static final String STARTLEVEL = "start";
    private static final Map<ClassDoc, ProgramElementDoc[]> propertiesCache =
            new HashMap<ClassDoc, ProgramElementDoc[]>();
    private static final Map<ProgramElementDoc, ProgramElementDoc> classPropertiesMap =
            new HashMap<ProgramElementDoc, ProgramElementDoc>();
    private static final Map<ProgramElementDoc, GetterSetter> getterSetterMap =
            new HashMap<ProgramElementDoc, GetterSetter>();
    private final List<ClassDoc> visibleClasses = new ArrayList<ClassDoc>();
    private final Map<Object, Map<ProgramElementDoc, String>> memberNameMap = new HashMap<Object, Map<ProgramElementDoc, String>>();
    private final Map<ClassDoc, ClassMembers> classMap = new HashMap<ClassDoc, ClassMembers>();
    private final ClassDoc classdoc;
    private final int kind;
    private final Configuration configuration;
    private boolean noVisibleMembers = true;

    public VisibleMemberMap(ClassDoc classdoc,
                            int kind,
                            Configuration configuration) {
        this.classdoc = classdoc;
        this.kind = kind;
        this.configuration = configuration;
        new ClassMembers(classdoc, STARTLEVEL).build();
    }

    public List<ClassDoc> getVisibleClassesList() {
        sort(visibleClasses);
        return visibleClasses;
    }

    public ProgramElementDoc getPropertyMemberDoc(ProgramElementDoc ped) {
        return classPropertiesMap.get(ped);
    }

    public ProgramElementDoc getGetterForProperty(ProgramElementDoc propertyMethod) {
        return getterSetterMap.get(propertyMethod).getGetter();
    }

    public ProgramElementDoc getSetterForProperty(ProgramElementDoc propertyMethod) {
        return getterSetterMap.get(propertyMethod).getSetter();
    }

    private List<ProgramElementDoc> getInheritedPackagePrivateMethods(Configuration configuration) {
        List<ProgramElementDoc> results = new ArrayList<ProgramElementDoc>();
        for (Iterator<ClassDoc> iter = visibleClasses.iterator(); iter.hasNext(); ) {
            ClassDoc currentClass = iter.next();
            if (currentClass != classdoc &&
                    currentClass.isPackagePrivate() &&
                    !Util.isLinkable(currentClass, configuration)) {


                results.addAll(getMembersFor(currentClass));
            }
        }
        return results;
    }

    public List<ProgramElementDoc> getLeafClassMembers(Configuration configuration) {
        List<ProgramElementDoc> result = getMembersFor(classdoc);
        result.addAll(getInheritedPackagePrivateMethods(configuration));
        return result;
    }

    public List<ProgramElementDoc> getMembersFor(ClassDoc cd) {
        ClassMembers clmembers = classMap.get(cd);
        if (clmembers == null) {
            return new ArrayList<ProgramElementDoc>();
        }
        return clmembers.getMembers();
    }

    private void sort(List<ClassDoc> list) {
        List<ClassDoc> classes = new ArrayList<ClassDoc>();
        List<ClassDoc> interfaces = new ArrayList<ClassDoc>();
        for (int i = 0; i < list.size(); i++) {
            ClassDoc cd = list.get(i);
            if (cd.isClass()) {
                classes.add(cd);
            } else {
                interfaces.add(cd);
            }
        }
        list.clear();
        list.addAll(classes);
        list.addAll(interfaces);
    }

    private void fillMemberLevelMap(List<ProgramElementDoc> list, String level) {
        for (int i = 0; i < list.size(); i++) {
            Object key = getMemberKey(list.get(i));
            Map<ProgramElementDoc, String> memberLevelMap = memberNameMap.get(key);
            if (memberLevelMap == null) {
                memberLevelMap = new HashMap<ProgramElementDoc, String>();
                memberNameMap.put(key, memberLevelMap);
            }
            memberLevelMap.put(list.get(i), level);
        }
    }

    private void purgeMemberLevelMap(List<ProgramElementDoc> list, String level) {
        for (int i = 0; i < list.size(); i++) {
            Object key = getMemberKey(list.get(i));
            Map<ProgramElementDoc, String> memberLevelMap = memberNameMap.get(key);
            if (level.equals(memberLevelMap.get(list.get(i))))
                memberLevelMap.remove(list.get(i));
        }
    }

    public boolean noVisibleMembers() {
        return noVisibleMembers;
    }

    private ClassMember getClassMember(MethodDoc member) {
        for (Iterator<?> iter = memberNameMap.keySet().iterator(); iter.hasNext(); ) {
            Object key = iter.next();
            if (key instanceof String) {
                continue;
            } else if (((ClassMember) key).isEqual(member)) {
                return (ClassMember) key;
            }
        }
        return new ClassMember(member);
    }

    private Object getMemberKey(ProgramElementDoc doc) {
        if (doc.isConstructor()) {
            return doc.name() + ((ExecutableMemberDoc) doc).signature();
        } else if (doc.isMethod()) {
            return getClassMember((MethodDoc) doc);
        } else if (doc.isField() || doc.isEnumConstant() || doc.isAnnotationTypeElement()) {
            return doc.name();
        } else {
            String classOrIntName = doc.name();

            classOrIntName = classOrIntName.indexOf('.') != 0 ? classOrIntName.substring(classOrIntName.lastIndexOf('.')) : classOrIntName;
            return "clint" + classOrIntName;
        }
    }

    private class ClassMember {
        private Set<ProgramElementDoc> members;

        public ClassMember(ProgramElementDoc programElementDoc) {
            members = new HashSet<ProgramElementDoc>();
            members.add(programElementDoc);
        }

        public void addMember(ProgramElementDoc programElementDoc) {
            members.add(programElementDoc);
        }

        public boolean isEqual(MethodDoc member) {
            for (Iterator<ProgramElementDoc> iter = members.iterator(); iter.hasNext(); ) {
                MethodDoc member2 = (MethodDoc) iter.next();
                if (Util.executableMembersEqual(member, member2)) {
                    members.add(member);
                    return true;
                }
            }
            return false;
        }
    }

    private class ClassMembers {

        private final Pattern pattern = Pattern.compile("[sg]et\\p{Upper}.*");
        private ClassDoc mappingClass;
        private List<ProgramElementDoc> members = new ArrayList<ProgramElementDoc>();
        private String level;

        private ClassMembers(ClassDoc mappingClass, String level) {
            this.mappingClass = mappingClass;
            this.level = level;
            if (classMap.containsKey(mappingClass) &&
                    level.startsWith(classMap.get(mappingClass).level)) {


                purgeMemberLevelMap(getClassMembers(mappingClass, false),
                        classMap.get(mappingClass).level);
                classMap.remove(mappingClass);
                visibleClasses.remove(mappingClass);
            }
            if (!classMap.containsKey(mappingClass)) {
                classMap.put(mappingClass, this);
                visibleClasses.add(mappingClass);
            }
        }

        public List<ProgramElementDoc> getMembers() {
            return members;
        }

        private void build() {
            if (kind == CONSTRUCTORS) {
                addMembers(mappingClass);
            } else {
                mapClass();
            }
        }

        private void mapClass() {
            addMembers(mappingClass);
            ClassDoc[] interfaces = mappingClass.interfaces();
            for (int i = 0; i < interfaces.length; i++) {
                String locallevel = level + 1;
                ClassMembers cm = new ClassMembers(interfaces[i], locallevel);
                cm.mapClass();
            }
            if (mappingClass.isClass()) {
                ClassDoc superclass = mappingClass.superclass();
                if (!(superclass == null || mappingClass.equals(superclass))) {
                    ClassMembers cm = new ClassMembers(superclass,
                            level + "c");
                    cm.mapClass();
                }
            }
        }

        private void addMembers(ClassDoc fromClass) {
            List<ProgramElementDoc> cdmembers = getClassMembers(fromClass, true);
            List<ProgramElementDoc> incllist = new ArrayList<ProgramElementDoc>();
            for (int i = 0; i < cdmembers.size(); i++) {
                ProgramElementDoc pgmelem = cdmembers.get(i);
                if (!found(members, pgmelem) &&
                        memberIsVisible(pgmelem) &&
                        !isOverridden(pgmelem, level) &&
                        !isTreatedAsPrivate(pgmelem)) {
                    incllist.add(pgmelem);
                }
            }
            if (incllist.size() > 0) {
                noVisibleMembers = false;
            }
            members.addAll(incllist);
            fillMemberLevelMap(getClassMembers(fromClass, false), level);
        }

        private boolean isTreatedAsPrivate(ProgramElementDoc pgmelem) {
            if (!configuration.javafx) {
                return false;
            }
            Tag[] aspTags = pgmelem.tags("@treatAsPrivate");
            boolean result = (aspTags != null) && (aspTags.length > 0);
            return result;
        }

        private boolean memberIsVisible(ProgramElementDoc pgmdoc) {
            if (pgmdoc.containingClass().equals(classdoc)) {


                return true;
            } else if (pgmdoc.isPrivate()) {


                return false;
            } else if (pgmdoc.isPackagePrivate()) {


                return pgmdoc.containingClass().containingPackage().equals(
                        classdoc.containingPackage());
            } else {

                return true;
            }
        }

        private List<ProgramElementDoc> getClassMembers(ClassDoc cd, boolean filter) {
            if (cd.isEnum() && kind == CONSTRUCTORS) {


                return Arrays.asList(new ProgramElementDoc[]{});
            }
            ProgramElementDoc[] members = null;
            switch (kind) {
                case ANNOTATION_TYPE_FIELDS:
                    members = cd.fields(filter);
                    break;
                case ANNOTATION_TYPE_MEMBER_OPTIONAL:
                    members = cd.isAnnotationType() ?
                            filter((AnnotationTypeDoc) cd, false) :
                            new AnnotationTypeElementDoc[]{};
                    break;
                case ANNOTATION_TYPE_MEMBER_REQUIRED:
                    members = cd.isAnnotationType() ?
                            filter((AnnotationTypeDoc) cd, true) :
                            new AnnotationTypeElementDoc[]{};
                    break;
                case INNERCLASSES:
                    members = cd.innerClasses(filter);
                    break;
                case ENUM_CONSTANTS:
                    members = cd.enumConstants();
                    break;
                case FIELDS:
                    members = cd.fields(filter);
                    break;
                case CONSTRUCTORS:
                    members = cd.constructors();
                    break;
                case METHODS:
                    members = cd.methods(filter);
                    checkOnPropertiesTags((MethodDoc[]) members);
                    break;
                case PROPERTIES:
                    members = properties(cd, filter);
                    break;
                default:
                    members = new ProgramElementDoc[0];
            }

            if (configuration.nodeprecated) {
                return Util.excludeDeprecatedMembersAsList(members);
            }
            return Arrays.asList(members);
        }

        private AnnotationTypeElementDoc[] filter(AnnotationTypeDoc doc,
                                                  boolean required) {
            AnnotationTypeElementDoc[] members = doc.elements();
            List<AnnotationTypeElementDoc> targetMembers = new ArrayList<AnnotationTypeElementDoc>();
            for (int i = 0; i < members.length; i++) {
                if ((required && members[i].defaultValue() == null) ||
                        ((!required) && members[i].defaultValue() != null)) {
                    targetMembers.add(members[i]);
                }
            }
            return targetMembers.toArray(new AnnotationTypeElementDoc[]{});
        }

        private boolean found(List<ProgramElementDoc> list, ProgramElementDoc elem) {
            for (int i = 0; i < list.size(); i++) {
                ProgramElementDoc pgmelem = list.get(i);
                if (Util.matches(pgmelem, elem)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isOverridden(ProgramElementDoc pgmdoc, String level) {
            Map<?, String> memberLevelMap = memberNameMap.get(getMemberKey(pgmdoc));
            if (memberLevelMap == null)
                return false;
            String mappedlevel = null;
            Iterator<String> iterator = memberLevelMap.values().iterator();
            while (iterator.hasNext()) {
                mappedlevel = iterator.next();
                if (mappedlevel.equals(STARTLEVEL) ||
                        (level.startsWith(mappedlevel) &&
                                !level.equals(mappedlevel))) {
                    return true;
                }
            }
            return false;
        }

        private ProgramElementDoc[] properties(final ClassDoc cd, final boolean filter) {
            final MethodDoc[] allMethods = cd.methods(filter);
            final FieldDoc[] allFields = cd.fields(false);
            if (propertiesCache.containsKey(cd)) {
                return propertiesCache.get(cd);
            }
            final List<MethodDoc> result = new ArrayList<MethodDoc>();
            for (final MethodDoc propertyMethod : allMethods) {
                if (!isPropertyMethod(propertyMethod)) {
                    continue;
                }
                final MethodDoc getter = getterForField(allMethods, propertyMethod);
                final MethodDoc setter = setterForField(allMethods, propertyMethod);
                final FieldDoc field = fieldForProperty(allFields, propertyMethod);
                addToPropertiesMap(setter, getter, propertyMethod, field);
                getterSetterMap.put(propertyMethod, new GetterSetter(getter, setter));
                result.add(propertyMethod);
            }
            final ProgramElementDoc[] resultAray =
                    result.toArray(new ProgramElementDoc[result.size()]);
            propertiesCache.put(cd, resultAray);
            return resultAray;
        }

        private void addToPropertiesMap(MethodDoc setter,
                                        MethodDoc getter,
                                        MethodDoc propertyMethod,
                                        FieldDoc field) {
            if ((field == null)
                    || (field.getRawCommentText() == null)
                    || field.getRawCommentText().length() == 0) {
                addToPropertiesMap(setter, propertyMethod);
                addToPropertiesMap(getter, propertyMethod);
                addToPropertiesMap(propertyMethod, propertyMethod);
            } else {
                addToPropertiesMap(getter, field);
                addToPropertiesMap(setter, field);
                addToPropertiesMap(propertyMethod, field);
            }
        }

        private void addToPropertiesMap(ProgramElementDoc propertyMethod,
                                        ProgramElementDoc commentSource) {
            if (null == propertyMethod || null == commentSource) {
                return;
            }
            final String methodRawCommentText = propertyMethod.getRawCommentText();

            if ((null == methodRawCommentText || 0 == methodRawCommentText.length())
                    || propertyMethod.equals(commentSource)) {
                classPropertiesMap.put(propertyMethod, commentSource);
            }
        }

        private MethodDoc getterForField(MethodDoc[] methods,
                                         MethodDoc propertyMethod) {
            final String propertyMethodName = propertyMethod.name();
            final String fieldName =
                    propertyMethodName.substring(0,
                            propertyMethodName.lastIndexOf("Property"));
            final String fieldNameUppercased =
                    "" + Character.toUpperCase(fieldName.charAt(0))
                            + fieldName.substring(1);
            final String getterNamePattern;
            final String fieldTypeName = propertyMethod.returnType().toString();
            if ("boolean".equals(fieldTypeName)
                    || fieldTypeName.endsWith("BooleanProperty")) {
                getterNamePattern = "(is|get)" + fieldNameUppercased;
            } else {
                getterNamePattern = "get" + fieldNameUppercased;
            }
            for (MethodDoc methodDoc : methods) {
                if (Pattern.matches(getterNamePattern, methodDoc.name())) {
                    if (0 == methodDoc.parameters().length
                            && (methodDoc.isPublic() || methodDoc.isProtected())) {
                        return methodDoc;
                    }
                }
            }
            return null;
        }

        private MethodDoc setterForField(MethodDoc[] methods,
                                         MethodDoc propertyMethod) {
            final String propertyMethodName = propertyMethod.name();
            final String fieldName =
                    propertyMethodName.substring(0,
                            propertyMethodName.lastIndexOf("Property"));
            final String fieldNameUppercased =
                    "" + Character.toUpperCase(fieldName.charAt(0))
                            + fieldName.substring(1);
            final String setter = "set" + fieldNameUppercased;
            for (MethodDoc methodDoc : methods) {
                if (setter.equals(methodDoc.name())) {
                    if (1 == methodDoc.parameters().length
                            && "void".equals(methodDoc.returnType().simpleTypeName())
                            && (methodDoc.isPublic() || methodDoc.isProtected())) {
                        return methodDoc;
                    }
                }
            }
            return null;
        }

        private FieldDoc fieldForProperty(FieldDoc[] fields, MethodDoc property) {
            for (FieldDoc field : fields) {
                final String fieldName = field.name();
                final String propertyName = fieldName + "Property";
                if (propertyName.equals(property.name())) {
                    return field;
                }
            }
            return null;
        }

        private boolean isPropertyMethod(MethodDoc method) {
            if (!method.name().endsWith("Property")) {
                return false;
            }
            if (!memberIsVisible(method)) {
                return false;
            }
            if (pattern.matcher(method.name()).matches()) {
                return false;
            }
            return 0 == method.parameters().length
                    && !"void".equals(method.returnType().simpleTypeName());
        }

        private void checkOnPropertiesTags(MethodDoc[] members) {
            for (MethodDoc methodDoc : members) {
                if (methodDoc.isIncluded()) {
                    for (Tag tag : methodDoc.tags()) {
                        String tagName = tag.name();
                        if (tagName.equals("@propertySetter")
                                || tagName.equals("@propertyGetter")
                                || tagName.equals("@propertyDescription")) {
                            if (!isPropertyGetterOrSetter(members, methodDoc)) {
                                configuration.message.warning(tag.position(),
                                        "doclet.javafx_tag_misuse");
                            }
                            break;
                        }
                    }
                }
            }
        }

        private boolean isPropertyGetterOrSetter(MethodDoc[] members,
                                                 MethodDoc methodDoc) {
            boolean found = false;
            String propertyName = Util.propertyNameFromMethodName(methodDoc.name());
            if (!propertyName.isEmpty()) {
                String propertyMethodName = propertyName + "Property";
                for (MethodDoc member : members) {
                    if (member.name().equals(propertyMethodName)) {
                        found = true;
                        break;
                    }
                }
            }
            return found;
        }
    }

    private class GetterSetter {
        private final ProgramElementDoc getter;
        private final ProgramElementDoc setter;

        public GetterSetter(ProgramElementDoc getter, ProgramElementDoc setter) {
            this.getter = getter;
            this.setter = setter;
        }

        public ProgramElementDoc getGetter() {
            return getter;
        }

        public ProgramElementDoc getSetter() {
            return setter;
        }
    }
}
