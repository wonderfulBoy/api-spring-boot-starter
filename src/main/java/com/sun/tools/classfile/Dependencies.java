package com.sun.tools.classfile;

import com.sun.tools.classfile.Dependency.Filter;
import com.sun.tools.classfile.Dependency.Finder;
import com.sun.tools.classfile.Dependency.Location;
import com.sun.tools.classfile.Type.*;

import java.util.*;
import java.util.regex.Pattern;

import static com.sun.tools.classfile.ConstantPool.*;

public class Dependencies {

    private Filter filter;
    private Finder finder;

    public static Finder getDefaultFinder() {
        return new APIDependencyFinder(AccessFlags.ACC_PRIVATE);
    }

    public static Finder getAPIFinder(int access) {
        return new APIDependencyFinder(access);
    }

    public static Finder getClassDependencyFinder() {
        return new ClassDependencyFinder();
    }

    public static Filter getDefaultFilter() {
        return DefaultFilter.instance();
    }

    public static Filter getRegexFilter(Pattern pattern) {
        return new TargetRegexFilter(pattern);
    }

    public static Filter getPackageFilter(Set<String> packageNames, boolean matchSubpackages) {
        return new TargetPackageFilter(packageNames, matchSubpackages);
    }

    public Finder getFinder() {
        if (finder == null)
            finder = getDefaultFinder();
        return finder;
    }

    public void setFinder(Finder f) {
        f.getClass();
        finder = f;
    }

    public Filter getFilter() {
        if (filter == null)
            filter = getDefaultFilter();
        return filter;
    }

    public void setFilter(Filter f) {
        f.getClass();
        filter = f;
    }

    public Set<Dependency> findAllDependencies(
            ClassFileReader classFinder, Set<String> rootClassNames,
            boolean transitiveClosure)
            throws ClassFileNotFoundException {
        final Set<Dependency> results = new HashSet<Dependency>();
        Recorder r = new Recorder() {
            public void addDependency(Dependency d) {
                results.add(d);
            }
        };
        findAllDependencies(classFinder, rootClassNames, transitiveClosure, r);
        return results;
    }

    public void findAllDependencies(
            ClassFileReader classFinder, Set<String> rootClassNames,
            boolean transitiveClosure, Recorder recorder)
            throws ClassFileNotFoundException {
        Set<String> doneClasses = new HashSet<String>();
        getFinder();
        getFilter();


        Deque<String> deque = new LinkedList<String>(rootClassNames);
        String className;
        while ((className = deque.poll()) != null) {
            assert (!doneClasses.contains(className));
            doneClasses.add(className);
            ClassFile cf = classFinder.getClassFile(className);


            for (Dependency d : finder.findDependencies(cf)) {
                recorder.addDependency(d);
                if (transitiveClosure && filter.accepts(d)) {
                    String cn = d.getTarget().getClassName();
                    if (!doneClasses.contains(cn))
                        deque.add(cn);
                }
            }
        }
    }

    public interface ClassFileReader {

        ClassFile getClassFile(String className)
                throws ClassFileNotFoundException;
    }

    public interface Recorder {

        void addDependency(Dependency d);
    }

    public static class ClassFileNotFoundException extends Exception {
        private static final long serialVersionUID = 3632265927794475048L;
        public final String className;

        public ClassFileNotFoundException(String className) {
            super(className);
            this.className = className;
        }

        public ClassFileNotFoundException(String className, Throwable cause) {
            this(className);
            initCause(cause);
        }
    }

    public static class ClassFileError extends Error {
        private static final long serialVersionUID = 4111110813961313203L;

        public ClassFileError(Throwable cause) {
            initCause(cause);
        }
    }

    static class SimpleLocation implements Location {
        private String name;
        private String className;

        public SimpleLocation(String name) {
            this.name = name;
            this.className = name.replace('/', '.');
        }

        public String getName() {
            return name;
        }

        public String getClassName() {
            return className;
        }

        public String getPackageName() {
            int i = name.lastIndexOf('/');
            return (i > 0) ? name.substring(0, i).replace('/', '.') : "";
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof SimpleLocation))
                return false;
            return (name.equals(((SimpleLocation) other).name));
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    static class SimpleDependency implements Dependency {
        private Location origin;
        private Location target;

        public SimpleDependency(Location origin, Location target) {
            this.origin = origin;
            this.target = target;
        }

        public Location getOrigin() {
            return origin;
        }

        public Location getTarget() {
            return target;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other)
                return true;
            if (!(other instanceof SimpleDependency))
                return false;
            SimpleDependency o = (SimpleDependency) other;
            return (origin.equals(o.origin) && target.equals(o.target));
        }

        @Override
        public int hashCode() {
            return origin.hashCode() * 31 + target.hashCode();
        }

        @Override
        public String toString() {
            return origin + ":" + target;
        }
    }

    static class DefaultFilter implements Filter {
        private static DefaultFilter instance;

        static DefaultFilter instance() {
            if (instance == null)
                instance = new DefaultFilter();
            return instance;
        }

        public boolean accepts(Dependency dependency) {
            return true;
        }
    }

    static class TargetRegexFilter implements Filter {
        private final Pattern pattern;

        TargetRegexFilter(Pattern pattern) {
            this.pattern = pattern;
        }

        public boolean accepts(Dependency dependency) {
            return pattern.matcher(dependency.getTarget().getClassName()).matches();
        }
    }

    static class TargetPackageFilter implements Filter {
        private final Set<String> packageNames;
        private final boolean matchSubpackages;

        TargetPackageFilter(Set<String> packageNames, boolean matchSubpackages) {
            for (String pn : packageNames) {
                if (pn.length() == 0)
                    throw new IllegalArgumentException();
            }
            this.packageNames = packageNames;
            this.matchSubpackages = matchSubpackages;
        }

        public boolean accepts(Dependency dependency) {
            String pn = dependency.getTarget().getPackageName();
            if (packageNames.contains(pn))
                return true;
            if (matchSubpackages) {
                for (String n : packageNames) {
                    if (pn.startsWith(n + "."))
                        return true;
                }
            }
            return false;
        }
    }

    static class ClassDependencyFinder extends BasicDependencyFinder {
        public Iterable<? extends Dependency> findDependencies(ClassFile classfile) {
            Visitor v = new Visitor(classfile);
            for (CPInfo cpInfo : classfile.constant_pool.entries()) {
                v.scan(cpInfo);
            }
            try {
                v.addClass(classfile.super_class);
                v.addClasses(classfile.interfaces);
                v.scan(classfile.attributes);
                for (Field f : classfile.fields) {
                    v.scan(f.descriptor, f.attributes);
                }
                for (Method m : classfile.methods) {
                    v.scan(m.descriptor, m.attributes);
                    Exceptions_attribute e =
                            (Exceptions_attribute) m.attributes.get(Attribute.Exceptions);
                    if (e != null) {
                        v.addClasses(e.exception_index_table);
                    }
                }
            } catch (ConstantPoolException e) {
                throw new ClassFileError(e);
            }
            return v.deps;
        }
    }

    static class APIDependencyFinder extends BasicDependencyFinder {
        private int showAccess;

        APIDependencyFinder(int access) {
            switch (access) {
                case AccessFlags.ACC_PUBLIC:
                case AccessFlags.ACC_PROTECTED:
                case AccessFlags.ACC_PRIVATE:
                case 0:
                    showAccess = access;
                    break;
                default:
                    throw new IllegalArgumentException("invalid access 0x"
                            + Integer.toHexString(access));
            }
        }

        public Iterable<? extends Dependency> findDependencies(ClassFile classfile) {
            try {
                Visitor v = new Visitor(classfile);
                v.addClass(classfile.super_class);
                v.addClasses(classfile.interfaces);

                for (Field f : classfile.fields) {
                    if (checkAccess(f.access_flags))
                        v.scan(f.descriptor, f.attributes);
                }
                for (Method m : classfile.methods) {
                    if (checkAccess(m.access_flags)) {
                        v.scan(m.descriptor, m.attributes);
                        Exceptions_attribute e =
                                (Exceptions_attribute) m.attributes.get(Attribute.Exceptions);
                        if (e != null)
                            v.addClasses(e.exception_index_table);
                    }
                }
                return v.deps;
            } catch (ConstantPoolException e) {
                throw new ClassFileError(e);
            }
        }

        boolean checkAccess(AccessFlags flags) {

            boolean isPublic = flags.is(AccessFlags.ACC_PUBLIC);
            boolean isProtected = flags.is(AccessFlags.ACC_PROTECTED);
            boolean isPrivate = flags.is(AccessFlags.ACC_PRIVATE);
            boolean isPackage = !(isPublic || isProtected || isPrivate);
            if ((showAccess == AccessFlags.ACC_PUBLIC) && (isProtected || isPrivate || isPackage))
                return false;
            else if ((showAccess == AccessFlags.ACC_PROTECTED) && (isPrivate || isPackage))
                return false;
            else return (showAccess != 0) || (!isPrivate);
        }
    }

    static abstract class BasicDependencyFinder implements Finder {
        private Map<String, Location> locations = new HashMap<String, Location>();

        Location getLocation(String className) {
            Location l = locations.get(className);
            if (l == null)
                locations.put(className, l = new SimpleLocation(className));
            return l;
        }

        class Visitor implements ConstantPool.Visitor<Void, Void>, Type.Visitor<Void, Void> {
            Set<Dependency> deps;
            private ConstantPool constant_pool;
            private Location origin;

            Visitor(ClassFile classFile) {
                try {
                    constant_pool = classFile.constant_pool;
                    origin = getLocation(classFile.getName());
                    deps = new HashSet<Dependency>();
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }
            }

            void scan(Descriptor d, Attributes attrs) {
                try {
                    scan(new Signature(d.index).getType(constant_pool));
                    scan(attrs);
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }
            }

            void scan(CPInfo cpInfo) {
                cpInfo.accept(this, null);
            }

            void scan(Type t) {
                t.accept(this, null);
            }

            void scan(Attributes attrs) {
                try {
                    Signature_attribute sa = (Signature_attribute) attrs.get(Attribute.Signature);
                    if (sa != null)
                        scan(sa.getParsedSignature().getType(constant_pool));
                    scan((RuntimeVisibleAnnotations_attribute)
                            attrs.get(Attribute.RuntimeVisibleAnnotations));
                    scan((RuntimeVisibleParameterAnnotations_attribute)
                            attrs.get(Attribute.RuntimeVisibleParameterAnnotations));
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }
            }

            private void scan(RuntimeAnnotations_attribute attr) throws ConstantPoolException {
                if (attr == null) {
                    return;
                }
                for (int i = 0; i < attr.annotations.length; i++) {
                    int index = attr.annotations[i].type_index;
                    scan(new Signature(index).getType(constant_pool));
                }
            }

            private void scan(RuntimeParameterAnnotations_attribute attr) throws ConstantPoolException {
                if (attr == null) {
                    return;
                }
                for (int param = 0; param < attr.parameter_annotations.length; param++) {
                    for (int i = 0; i < attr.parameter_annotations[param].length; i++) {
                        int index = attr.parameter_annotations[param][i].type_index;
                        scan(new Signature(index).getType(constant_pool));
                    }
                }
            }

            void addClass(int index) throws ConstantPoolException {
                if (index != 0) {
                    String name = constant_pool.getClassInfo(index).getBaseName();
                    if (name != null)
                        addDependency(name);
                }
            }

            void addClasses(int[] indices) throws ConstantPoolException {
                for (int i : indices)
                    addClass(i);
            }

            private void addDependency(String name) {
                deps.add(new SimpleDependency(origin, getLocation(name)));
            }

            public Void visitClass(CONSTANT_Class_info info, Void p) {
                try {
                    if (info.getName().startsWith("["))
                        new Signature(info.name_index).getType(constant_pool).accept(this, null);
                    else
                        addDependency(info.getBaseName());
                    return null;
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }
            }

            public Void visitDouble(CONSTANT_Double_info info, Void p) {
                return null;
            }

            public Void visitFieldref(CONSTANT_Fieldref_info info, Void p) {
                return visitRef(info, p);
            }

            public Void visitFloat(CONSTANT_Float_info info, Void p) {
                return null;
            }

            public Void visitInteger(CONSTANT_Integer_info info, Void p) {
                return null;
            }

            public Void visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, Void p) {
                return visitRef(info, p);
            }

            public Void visitInvokeDynamic(CONSTANT_InvokeDynamic_info info, Void p) {
                return null;
            }

            public Void visitLong(CONSTANT_Long_info info, Void p) {
                return null;
            }

            public Void visitMethodHandle(CONSTANT_MethodHandle_info info, Void p) {
                return null;
            }

            public Void visitMethodType(CONSTANT_MethodType_info info, Void p) {
                return null;
            }

            public Void visitMethodref(CONSTANT_Methodref_info info, Void p) {
                return visitRef(info, p);
            }

            public Void visitNameAndType(CONSTANT_NameAndType_info info, Void p) {
                try {
                    new Signature(info.type_index).getType(constant_pool).accept(this, null);
                    return null;
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }
            }

            public Void visitString(CONSTANT_String_info info, Void p) {
                return null;
            }

            public Void visitUtf8(CONSTANT_Utf8_info info, Void p) {
                return null;
            }

            private Void visitRef(CPRefInfo info, Void p) {
                try {
                    visitClass(info.getClassInfo(), p);
                    return null;
                } catch (ConstantPoolException e) {
                    throw new ClassFileError(e);
                }
            }

            private void findDependencies(Type t) {
                if (t != null)
                    t.accept(this, null);
            }

            private void findDependencies(List<? extends Type> ts) {
                if (ts != null) {
                    for (Type t : ts)
                        t.accept(this, null);
                }
            }

            public Void visitSimpleType(SimpleType type, Void p) {
                return null;
            }

            public Void visitArrayType(ArrayType type, Void p) {
                findDependencies(type.elemType);
                return null;
            }

            public Void visitMethodType(MethodType type, Void p) {
                findDependencies(type.paramTypes);
                findDependencies(type.returnType);
                findDependencies(type.throwsTypes);
                findDependencies(type.typeParamTypes);
                return null;
            }

            public Void visitClassSigType(ClassSigType type, Void p) {
                findDependencies(type.superclassType);
                findDependencies(type.superinterfaceTypes);
                return null;
            }

            public Void visitClassType(ClassType type, Void p) {
                findDependencies(type.outerType);
                addDependency(type.getBinaryName());
                findDependencies(type.typeArgs);
                return null;
            }

            public Void visitTypeParamType(TypeParamType type, Void p) {
                findDependencies(type.classBound);
                findDependencies(type.interfaceBounds);
                return null;
            }

            public Void visitWildcardType(WildcardType type, Void p) {
                findDependencies(type.boundType);
                return null;
            }
        }
    }
}
