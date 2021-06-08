package com.github.api.sun.tools.javac.code;

import com.github.api.sun.tools.javac.code.Type.*;
import com.github.api.sun.tools.javac.comp.Annotate;
import com.github.api.sun.tools.javac.comp.Attr;
import com.github.api.sun.tools.javac.comp.AttrContext;
import com.github.api.sun.tools.javac.comp.Env;
import com.github.api.sun.tools.javac.jvm.Code;
import com.github.api.sun.tools.javac.jvm.Pool;
import com.github.api.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.github.api.sun.tools.javac.util.Name;
import com.github.api.sun.tools.javac.util.*;

import javax.lang.model.element.*;
import javax.tools.JavaFileObject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.Set;
import java.util.concurrent.Callable;

import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.Kinds.*;
import static com.github.api.sun.tools.javac.code.TypeTag.*;

public abstract class Symbol extends AnnoConstruct implements Element {

    public int kind;

    public long flags_field;
    public Name name;
    public Type type;
    public Symbol owner;
    public Completer completer;
    public Type erasure_field;
    protected SymbolMetadata metadata;


    public Symbol(int kind, long flags, Name name, Type type, Symbol owner) {
        this.kind = kind;
        this.flags_field = flags;
        this.type = type;
        this.owner = owner;
        this.completer = null;
        this.erasure_field = null;
        this.name = name;
    }

    public long flags() {
        return flags_field;
    }

    public List<Attribute.Compound> getRawAttributes() {
        return (metadata == null)
                ? List.nil()
                : metadata.getDeclarationAttributes();
    }

    public List<Attribute.TypeCompound> getRawTypeAttributes() {
        return (metadata == null)
                ? List.nil()
                : metadata.getTypeAttributes();
    }

    public Attribute.Compound attribute(Symbol anno) {
        for (Attribute.Compound a : getRawAttributes()) {
            if (a.type.tsym == anno) return a;
        }
        return null;
    }

    public boolean annotationsPendingCompletion() {
        return metadata != null && metadata.pendingCompletion();
    }

    public void appendAttributes(List<Attribute.Compound> l) {
        if (l.nonEmpty()) {
            initedMetadata().append(l);
        }
    }

    public void appendClassInitTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedMetadata().appendClassInitTypeAttributes(l);
        }
    }

    public void appendInitTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedMetadata().appendInitTypeAttributes(l);
        }
    }

    public void appendTypeAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.TypeCompound> ctx) {
        initedMetadata().appendTypeAttributesWithCompletion(ctx);
    }

    public void appendUniqueTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.nonEmpty()) {
            initedMetadata().appendUniqueTypes(l);
        }
    }

    public List<Attribute.TypeCompound> getClassInitTypeAttributes() {
        return (metadata == null)
                ? List.nil()
                : metadata.getClassInitTypeAttributes();
    }

    public List<Attribute.TypeCompound> getInitTypeAttributes() {
        return (metadata == null)
                ? List.nil()
                : metadata.getInitTypeAttributes();
    }

    public List<Attribute.Compound> getDeclarationAttributes() {
        return (metadata == null)
                ? List.nil()
                : metadata.getDeclarationAttributes();
    }

    public void setDeclarationAttributes(List<Attribute.Compound> a) {
        if (metadata != null || a.nonEmpty()) {
            initedMetadata().setDeclarationAttributes(a);
        }
    }

    public boolean hasAnnotations() {
        return (metadata != null && !metadata.isEmpty());
    }

    public boolean hasTypeAnnotations() {
        return (metadata != null && !metadata.isTypesEmpty());
    }

    public void prependAttributes(List<Attribute.Compound> l) {
        if (l.nonEmpty()) {
            initedMetadata().prepend(l);
        }
    }

    public void resetAnnotations() {
        initedMetadata().reset();
    }

    public void setAttributes(Symbol other) {
        if (metadata != null || other.metadata != null) {
            initedMetadata().setAttributes(other.metadata);
        }
    }

    public void setDeclarationAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.Compound> ctx) {
        initedMetadata().setDeclarationAttributesWithCompletion(ctx);
    }

    public void setTypeAttributes(List<Attribute.TypeCompound> a) {
        if (metadata != null || a.nonEmpty()) {
            if (metadata == null)
                metadata = new SymbolMetadata(this);
            metadata.setTypeAttributes(a);
        }
    }

    private SymbolMetadata initedMetadata() {
        if (metadata == null)
            metadata = new SymbolMetadata(this);
        return metadata;
    }

    public SymbolMetadata getMetadata() {
        return metadata;
    }

    public Symbol clone(Symbol newOwner) {
        throw new AssertionError();
    }

    public <R, P> R accept(Visitor<R, P> v, P p) {
        return v.visitSymbol(this, p);
    }

    public String toString() {
        return name.toString();
    }

    public Symbol location() {
        if (owner.name == null || (owner.name.isEmpty() &&
                (owner.flags() & BLOCK) == 0 && owner.kind != PCK && owner.kind != TYP)) {
            return null;
        }
        return owner;
    }

    public Symbol location(Type site, Types types) {
        if (owner.name == null || owner.name.isEmpty()) {
            return location();
        }
        if (owner.type.hasTag(CLASS)) {
            Type ownertype = types.asOuterSuper(site, owner);
            if (ownertype != null) return ownertype.tsym;
        }
        return owner;
    }

    public Symbol baseSymbol() {
        return this;
    }

    public Type erasure(Types types) {
        if (erasure_field == null)
            erasure_field = types.erasure(type);
        return erasure_field;
    }

    public Type externalType(Types types) {
        Type t = erasure(types);
        if (name == name.table.names.init && owner.hasOuterInstance()) {
            Type outerThisType = types.erasure(owner.type.getEnclosingType());
            return new MethodType(t.getParameterTypes().prepend(outerThisType),
                    t.getReturnType(),
                    t.getThrownTypes(),
                    t.tsym);
        } else {
            return t;
        }
    }

    public boolean isDeprecated() {
        return (flags_field & DEPRECATED) != 0;
    }

    public boolean isStatic() {
        return
                (flags() & STATIC) != 0 ||
                        (owner.flags() & INTERFACE) != 0 && kind != MTH &&
                                name != name.table.names._this;
    }

    public boolean isInterface() {
        return (flags() & INTERFACE) != 0;
    }

    public boolean isPrivate() {
        return (flags_field & Flags.AccessFlags) == PRIVATE;
    }

    public boolean isEnum() {
        return (flags() & ENUM) != 0;
    }

    public boolean isLocal() {
        return
                (owner.kind & (VAR | MTH)) != 0 ||
                        (owner.kind == TYP && owner.isLocal());
    }

    public boolean isAnonymous() {
        return name.isEmpty();
    }

    public boolean isConstructor() {
        return name == name.table.names.init;
    }

    public Name getQualifiedName() {
        return name;
    }

    public Name flatName() {
        return getQualifiedName();
    }

    public Scope members() {
        return null;
    }

    public boolean isInner() {
        return type.getEnclosingType().hasTag(CLASS);
    }

    public boolean hasOuterInstance() {
        return
                type.getEnclosingType().hasTag(CLASS) && (flags() & (INTERFACE | NOOUTERTHIS)) == 0;
    }

    public ClassSymbol enclClass() {
        Symbol c = this;
        while (c != null &&
                ((c.kind & TYP) == 0 || !c.type.hasTag(CLASS))) {
            c = c.owner;
        }
        return (ClassSymbol) c;
    }

    public ClassSymbol outermostClass() {
        Symbol sym = this;
        Symbol prev = null;
        while (sym.kind != PCK) {
            prev = sym;
            sym = sym.owner;
        }
        return (ClassSymbol) prev;
    }

    public PackageSymbol packge() {
        Symbol sym = this;
        while (sym.kind != PCK) {
            sym = sym.owner;
        }
        return (PackageSymbol) sym;
    }

    public boolean isSubClass(Symbol base, Types types) {
        throw new AssertionError("isSubClass " + this);
    }

    public boolean isMemberOf(TypeSymbol clazz, Types types) {
        return
                owner == clazz ||
                        clazz.isSubClass(owner, types) &&
                                isInheritedIn(clazz, types) &&
                                !hiddenIn((ClassSymbol) clazz, types);
    }

    public boolean isEnclosedBy(ClassSymbol clazz) {
        for (Symbol sym = this; sym.kind != PCK; sym = sym.owner)
            if (sym == clazz) return true;
        return false;
    }

    private boolean hiddenIn(ClassSymbol clazz, Types types) {
        Symbol sym = hiddenInInternal(clazz, types);
        return sym != null && sym != this;
    }

    private Symbol hiddenInInternal(ClassSymbol c, Types types) {
        Scope.Entry e = c.members().lookup(name);
        while (e.scope != null) {
            if (e.sym.kind == kind &&
                    (kind != MTH ||
                            (e.sym.flags() & STATIC) != 0 &&
                                    types.isSubSignature(e.sym.type, type))) {
                return e.sym;
            }
            e = e.next();
        }
        List<Symbol> hiddenSyms = List.nil();
        for (Type st : types.interfaces(c.type).prepend(types.supertype(c.type))) {
            if (st != null && (st.hasTag(CLASS))) {
                Symbol sym = hiddenInInternal((ClassSymbol) st.tsym, types);
                if (sym != null) {
                    hiddenSyms = hiddenSyms.prepend(hiddenInInternal((ClassSymbol) st.tsym, types));
                }
            }
        }
        return hiddenSyms.contains(this) ?
                this :
                (hiddenSyms.isEmpty() ? null : hiddenSyms.head);
    }

    public boolean isInheritedIn(Symbol clazz, Types types) {
        switch ((int) (flags_field & Flags.AccessFlags)) {
            default:
            case PUBLIC:
                return true;
            case PRIVATE:
                return this.owner == clazz;
            case PROTECTED:

                return (clazz.flags() & INTERFACE) == 0;
            case 0:
                PackageSymbol thisPackage = this.packge();
                for (Symbol sup = clazz;
                     sup != null && sup != this.owner;
                     sup = types.supertype(sup.type).tsym) {
                    while (sup.type.hasTag(TYPEVAR))
                        sup = sup.type.getUpperBound().tsym;
                    if (sup.type.isErroneous())
                        return true;
                    if ((sup.flags() & COMPOUND) != 0)
                        continue;
                    if (sup.packge() != thisPackage)
                        return false;
                }
                return (clazz.flags() & INTERFACE) == 0;
        }
    }

    public Symbol asMemberOf(Type site, Types types) {
        throw new AssertionError();
    }

    public boolean overrides(Symbol _other, TypeSymbol origin, Types types, boolean checkResult) {
        return false;
    }

    public void complete() throws CompletionFailure {
        if (completer != null) {
            Completer c = completer;
            completer = null;
            c.complete(this);
        }
    }

    public boolean exists() {
        return true;
    }

    public Type asType() {
        return type;
    }

    public Symbol getEnclosingElement() {
        return owner;
    }

    public ElementKind getKind() {
        return ElementKind.OTHER;
    }

    public Set<Modifier> getModifiers() {
        return Flags.asModifierSet(flags());
    }

    public Name getSimpleName() {
        return name;
    }

    @Override
    public List<Attribute.Compound> getAnnotationMirrors() {
        return getRawAttributes();
    }

    public java.util.List<Symbol> getEnclosedElements() {
        return List.nil();
    }

    public List<TypeVariableSymbol> getTypeParameters() {
        ListBuffer<TypeVariableSymbol> l = new ListBuffer<>();
        for (Type t : type.getTypeArguments()) {
            Assert.check(t.tsym.getKind() == ElementKind.TYPE_PARAMETER);
            l.append((TypeVariableSymbol) t.tsym);
        }
        return l.toList();
    }

    public interface Completer {
        void complete(Symbol sym) throws CompletionFailure;
    }

    public interface Visitor<R, P> {
        R visitClassSymbol(ClassSymbol s, P arg);

        R visitMethodSymbol(MethodSymbol s, P arg);

        R visitPackageSymbol(PackageSymbol s, P arg);

        R visitOperatorSymbol(OperatorSymbol s, P arg);

        R visitVarSymbol(VarSymbol s, P arg);

        R visitTypeSymbol(TypeSymbol s, P arg);

        R visitSymbol(Symbol s, P arg);
    }

    public static class DelegatedSymbol<T extends Symbol> extends Symbol {
        protected T other;

        public DelegatedSymbol(T other) {
            super(other.kind, other.flags_field, other.name, other.type, other.owner);
            this.other = other;
        }

        public String toString() {
            return other.toString();
        }

        public Symbol location() {
            return other.location();
        }

        public Symbol location(Type site, Types types) {
            return other.location(site, types);
        }

        public Symbol baseSymbol() {
            return other;
        }

        public Type erasure(Types types) {
            return other.erasure(types);
        }

        public Type externalType(Types types) {
            return other.externalType(types);
        }

        public boolean isLocal() {
            return other.isLocal();
        }

        public boolean isConstructor() {
            return other.isConstructor();
        }

        public Name getQualifiedName() {
            return other.getQualifiedName();
        }

        public Name flatName() {
            return other.flatName();
        }

        public Scope members() {
            return other.members();
        }

        public boolean isInner() {
            return other.isInner();
        }

        public boolean hasOuterInstance() {
            return other.hasOuterInstance();
        }

        public ClassSymbol enclClass() {
            return other.enclClass();
        }

        public ClassSymbol outermostClass() {
            return other.outermostClass();
        }

        public PackageSymbol packge() {
            return other.packge();
        }

        public boolean isSubClass(Symbol base, Types types) {
            return other.isSubClass(base, types);
        }

        public boolean isMemberOf(TypeSymbol clazz, Types types) {
            return other.isMemberOf(clazz, types);
        }

        public boolean isEnclosedBy(ClassSymbol clazz) {
            return other.isEnclosedBy(clazz);
        }

        public boolean isInheritedIn(Symbol clazz, Types types) {
            return other.isInheritedIn(clazz, types);
        }

        public Symbol asMemberOf(Type site, Types types) {
            return other.asMemberOf(site, types);
        }

        public void complete() throws CompletionFailure {
            other.complete();
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return other.accept(v, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitSymbol(other, p);
        }

        public T getUnderlyingSymbol() {
            return other;
        }
    }

    public static abstract class TypeSymbol extends Symbol {
        public TypeSymbol(int kind, long flags, Name name, Type type, Symbol owner) {
            super(kind, flags, name, type, owner);
        }

        static public Name formFullName(Name name, Symbol owner) {
            if (owner == null) return name;
            if (((owner.kind != ERR)) &&
                    ((owner.kind & (VAR | MTH)) != 0
                            || (owner.kind == TYP && owner.type.hasTag(TYPEVAR))
                    )) return name;
            Name prefix = owner.getQualifiedName();
            if (prefix == null || prefix == prefix.table.names.empty)
                return name;
            else return prefix.append('.', name);
        }

        static public Name formFlatName(Name name, Symbol owner) {
            if (owner == null ||
                    (owner.kind & (VAR | MTH)) != 0
                    || (owner.kind == TYP && owner.type.hasTag(TYPEVAR))
            ) return name;
            char sep = owner.kind == TYP ? '$' : '.';
            Name prefix = owner.flatName();
            if (prefix == null || prefix == prefix.table.names.empty)
                return name;
            else return prefix.append(sep, name);
        }

        public final boolean precedes(TypeSymbol that, Types types) {
            if (this == that)
                return false;
            if (type.hasTag(that.type.getTag())) {
                if (type.hasTag(CLASS)) {
                    return
                            types.rank(that.type) < types.rank(this.type) ||
                                    types.rank(that.type) == types.rank(this.type) &&
                                            that.getQualifiedName().compareTo(this.getQualifiedName()) < 0;
                } else if (type.hasTag(TYPEVAR)) {
                    return types.isSubtype(this.type, that.type);
                }
            }
            return type.hasTag(TYPEVAR);
        }

        @Override
        public java.util.List<Symbol> getEnclosedElements() {
            List<Symbol> list = List.nil();
            if (kind == TYP && type.hasTag(TYPEVAR)) {
                return list;
            }
            for (Scope.Entry e = members().elems; e != null; e = e.sibling) {
                if (e.sym != null && (e.sym.flags() & SYNTHETIC) == 0 && e.sym.owner == this)
                    list = list.prepend(e.sym);
            }
            return list;
        }

        @Override
        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitTypeSymbol(this, p);
        }
    }

    public static class TypeVariableSymbol
            extends TypeSymbol implements TypeParameterElement {
        public TypeVariableSymbol(long flags, Name name, Type type, Symbol owner) {
            super(TYP, flags, name, type, owner);
        }

        public ElementKind getKind() {
            return ElementKind.TYPE_PARAMETER;
        }

        @Override
        public Symbol getGenericElement() {
            return owner;
        }

        public List<Type> getBounds() {
            TypeVar t = (TypeVar) type;
            Type bound = t.getUpperBound();
            if (!bound.isCompound())
                return List.of(bound);
            ClassType ct = (ClassType) bound;
            if (!ct.tsym.erasure_field.isInterface()) {
                return ct.interfaces_field.prepend(ct.supertype_field);
            } else {


                return ct.interfaces_field;
            }
        }

        @Override
        public List<Attribute.Compound> getAnnotationMirrors() {
            return onlyTypeVariableAnnotations(owner.getRawTypeAttributes());
        }

        private List<Attribute.Compound> onlyTypeVariableAnnotations(
                List<Attribute.TypeCompound> candidates) {

            List<Attribute.Compound> res = List.nil();
            for (Attribute.TypeCompound a : candidates) {
                if (a.position.type == TargetType.CLASS_TYPE_PARAMETER ||
                        a.position.type == TargetType.METHOD_TYPE_PARAMETER)
                    res = res.prepend(a);
            }
            return res = res.reverse();
        }

        @Override
        public <A extends Annotation> Attribute.Compound getAttribute(Class<A> annoType) {
            String name = annoType.getName();


            List<Attribute.TypeCompound> candidates = owner.getRawTypeAttributes();
            for (Attribute.TypeCompound anno : candidates)
                if (anno.position.type == TargetType.CLASS_TYPE_PARAMETER ||
                        anno.position.type == TargetType.METHOD_TYPE_PARAMETER)
                    if (name.contentEquals(anno.type.tsym.flatName()))
                        return anno;
            return null;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitTypeParameter(this, p);
        }
    }

    public static class PackageSymbol extends TypeSymbol
            implements PackageElement {
        public Scope members_field;
        public Name fullname;
        public ClassSymbol package_info;

        public PackageSymbol(Name name, Type type, Symbol owner) {
            super(PCK, 0, name, type, owner);
            this.members_field = null;
            this.fullname = formFullName(name, owner);
        }

        public PackageSymbol(Name name, Symbol owner) {
            this(name, null, owner);
            this.type = new PackageType(this);
        }

        public String toString() {
            return fullname.toString();
        }

        public Name getQualifiedName() {
            return fullname;
        }

        public boolean isUnnamed() {
            return name.isEmpty() && owner != null;
        }

        public Scope members() {
            if (completer != null) complete();
            return members_field;
        }

        public long flags() {
            if (completer != null) complete();
            return flags_field;
        }

        @Override
        public List<Attribute.Compound> getRawAttributes() {
            if (completer != null) complete();
            if (package_info != null && package_info.completer != null) {
                package_info.complete();
                mergeAttributes();
            }
            return super.getRawAttributes();
        }

        private void mergeAttributes() {
            if (metadata == null &&
                    package_info.metadata != null) {
                metadata = new SymbolMetadata(this);
                metadata.setAttributes(package_info.metadata);
            }
        }

        public boolean exists() {
            return (flags_field & EXISTS) != 0;
        }

        public ElementKind getKind() {
            return ElementKind.PACKAGE;
        }

        public Symbol getEnclosingElement() {
            return null;
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitPackage(this, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitPackageSymbol(this, p);
        }
    }

    public static class ClassSymbol extends TypeSymbol implements TypeElement {

        public Scope members_field;

        public Name fullname;

        public Name flatname;

        public JavaFileObject sourcefile;

        public JavaFileObject classfile;

        public List<ClassSymbol> trans_local;

        public Pool pool;

        public ClassSymbol(long flags, Name name, Type type, Symbol owner) {
            super(TYP, flags, name, type, owner);
            this.members_field = null;
            this.fullname = formFullName(name, owner);
            this.flatname = formFlatName(name, owner);
            this.sourcefile = null;
            this.classfile = null;
            this.pool = null;
        }

        public ClassSymbol(long flags, Name name, Symbol owner) {
            this(
                    flags,
                    name,
                    new ClassType(Type.noType, null, null),
                    owner);
            this.type.tsym = this;
        }

        public String toString() {
            return className();
        }

        public long flags() {
            if (completer != null) complete();
            return flags_field;
        }

        public Scope members() {
            if (completer != null) complete();
            return members_field;
        }

        @Override
        public List<Attribute.Compound> getRawAttributes() {
            if (completer != null) complete();
            return super.getRawAttributes();
        }

        @Override
        public List<Attribute.TypeCompound> getRawTypeAttributes() {
            if (completer != null) complete();
            return super.getRawTypeAttributes();
        }

        public Type erasure(Types types) {
            if (erasure_field == null)
                erasure_field = new ClassType(types.erasure(type.getEnclosingType()),
                        List.nil(), this);
            return erasure_field;
        }

        public String className() {
            if (name.isEmpty())
                return
                        Log.getLocalizedString("anonymous.class", flatname);
            else
                return fullname.toString();
        }

        public Name getQualifiedName() {
            return fullname;
        }

        public Name flatName() {
            return flatname;
        }

        public boolean isSubClass(Symbol base, Types types) {
            if (this == base) {
                return true;
            } else if ((base.flags() & INTERFACE) != 0) {
                for (Type t = type; t.hasTag(CLASS); t = types.supertype(t))
                    for (List<Type> is = types.interfaces(t);
                         is.nonEmpty();
                         is = is.tail)
                        if (is.head.tsym.isSubClass(base, types)) return true;
            } else {
                for (Type t = type; t.hasTag(CLASS); t = types.supertype(t))
                    if (t.tsym == base) return true;
            }
            return false;
        }

        public void complete() throws CompletionFailure {
            try {
                super.complete();
            } catch (CompletionFailure ex) {

                flags_field |= (PUBLIC | STATIC);
                this.type = new ErrorType(this, Type.noType);
                throw ex;
            }
        }

        public List<Type> getInterfaces() {
            complete();
            if (type instanceof ClassType) {
                ClassType t = (ClassType) type;
                if (t.interfaces_field == null)
                    t.interfaces_field = List.nil();
                if (t.all_interfaces_field != null)
                    return Type.getModelTypes(t.all_interfaces_field);
                return t.interfaces_field;
            } else {
                return List.nil();
            }
        }

        public Type getSuperclass() {
            complete();
            if (type instanceof ClassType) {
                ClassType t = (ClassType) type;
                if (t.supertype_field == null)
                    t.supertype_field = Type.noType;

                return t.isInterface()
                        ? Type.noType
                        : t.supertype_field.getModelType();
            } else {
                return Type.noType;
            }
        }

        private ClassSymbol getSuperClassToSearchForAnnotations() {
            Type sup = getSuperclass();
            if (!sup.hasTag(CLASS) || sup.isErroneous())
                return null;
            return (ClassSymbol) sup.tsym;
        }

        @Override
        protected <A extends Annotation> A[] getInheritedAnnotations(Class<A> annoType) {
            ClassSymbol sup = getSuperClassToSearchForAnnotations();
            return sup == null ? super.getInheritedAnnotations(annoType)
                    : sup.getAnnotationsByType(annoType);
        }

        public ElementKind getKind() {
            long flags = flags();
            if ((flags & ANNOTATION) != 0)
                return ElementKind.ANNOTATION_TYPE;
            else if ((flags & INTERFACE) != 0)
                return ElementKind.INTERFACE;
            else if ((flags & ENUM) != 0)
                return ElementKind.ENUM;
            else
                return ElementKind.CLASS;
        }

        @Override
        public Set<Modifier> getModifiers() {
            long flags = flags();
            return Flags.asModifierSet(flags & ~DEFAULT);
        }

        public NestingKind getNestingKind() {
            complete();
            if (owner.kind == PCK)
                return NestingKind.TOP_LEVEL;
            else if (name.isEmpty())
                return NestingKind.ANONYMOUS;
            else if (owner.kind == MTH)
                return NestingKind.LOCAL;
            else
                return NestingKind.MEMBER;
        }

        @Override
        protected <A extends Annotation> Attribute.Compound getAttribute(final Class<A> annoType) {
            Attribute.Compound attrib = super.getAttribute(annoType);
            boolean inherited = annoType.isAnnotationPresent(Inherited.class);
            if (attrib != null || !inherited)
                return attrib;

            ClassSymbol superType = getSuperClassToSearchForAnnotations();
            return superType == null ? null
                    : superType.getAttribute(annoType);
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitType(this, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitClassSymbol(this, p);
        }
    }

    public static class VarSymbol extends Symbol implements VariableElement {

        public int pos = Position.NOPOS;

        public int adr = -1;
        private Object data;

        public VarSymbol(long flags, Name name, Type type, Symbol owner) {
            super(VAR, flags, name, type, owner);
        }

        public VarSymbol clone(Symbol newOwner) {
            VarSymbol v = new VarSymbol(flags_field, name, type, newOwner) {
                @Override
                public Symbol baseSymbol() {
                    return VarSymbol.this;
                }
            };
            v.pos = pos;
            v.adr = adr;
            v.data = data;
            return v;
        }

        public String toString() {
            return name.toString();
        }

        public Symbol asMemberOf(Type site, Types types) {
            return new VarSymbol(flags_field, name, types.memberType(site, this), owner);
        }

        public ElementKind getKind() {
            long flags = flags();
            if ((flags & PARAMETER) != 0) {
                if (isExceptionParameter())
                    return ElementKind.EXCEPTION_PARAMETER;
                else
                    return ElementKind.PARAMETER;
            } else if ((flags & ENUM) != 0) {
                return ElementKind.ENUM_CONSTANT;
            } else if (owner.kind == TYP || owner.kind == ERR) {
                return ElementKind.FIELD;
            } else if (isResourceVariable()) {
                return ElementKind.RESOURCE_VARIABLE;
            } else {
                return ElementKind.LOCAL_VARIABLE;
            }
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitVariable(this, p);
        }

        public Object getConstantValue() {
            return Constants.decode(getConstValue(), type);
        }

        public void setLazyConstValue(final Env<AttrContext> env,
                                      final Attr attr,
                                      final JCVariableDecl variable) {
            setData(new Callable<Object>() {
                public Object call() {
                    return attr.attribLazyConstantValue(env, variable, type);
                }
            });
        }

        public boolean isExceptionParameter() {
            return data == ElementKind.EXCEPTION_PARAMETER;
        }

        public boolean isResourceVariable() {
            return data == ElementKind.RESOURCE_VARIABLE;
        }

        public Object getConstValue() {

            if (data == ElementKind.EXCEPTION_PARAMETER ||
                    data == ElementKind.RESOURCE_VARIABLE) {
                return null;
            } else if (data instanceof Callable<?>) {


                Callable<?> eval = (Callable<?>) data;
                data = null;
                try {
                    data = eval.call();
                } catch (Exception ex) {
                    throw new AssertionError(ex);
                }
            }
            return data;
        }

        public void setData(Object data) {
            Assert.check(!(data instanceof Env<?>), this);
            this.data = data;
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitVarSymbol(this, p);
        }
    }

    public static class MethodSymbol extends Symbol implements ExecutableElement {

        public static final Filter<Symbol> implementation_filter = new Filter<Symbol>() {
            public boolean accepts(Symbol s) {
                return s.kind == Kinds.MTH &&
                        (s.flags() & SYNTHETIC) == 0;
            }
        };
        public Code code = null;
        public List<VarSymbol> extraParams = List.nil();
        public List<VarSymbol> capturedLocals = List.nil();
        public List<VarSymbol> params = null;
        public List<Name> savedParameterNames;
        public Attribute defaultValue = null;

        public MethodSymbol(long flags, Name name, Type type, Symbol owner) {
            super(MTH, flags, name, type, owner);
            if (owner.type.hasTag(TYPEVAR)) Assert.error(owner + "." + name);
        }

        public MethodSymbol clone(Symbol newOwner) {
            MethodSymbol m = new MethodSymbol(flags_field, name, type, newOwner) {
                @Override
                public Symbol baseSymbol() {
                    return MethodSymbol.this;
                }
            };
            m.code = code;
            return m;
        }

        @Override
        public Set<Modifier> getModifiers() {
            long flags = flags();
            return Flags.asModifierSet((flags & DEFAULT) != 0 ? flags & ~ABSTRACT : flags);
        }

        public String toString() {
            if ((flags() & BLOCK) != 0) {
                return owner.name.toString();
            } else {
                String s = (name == name.table.names.init)
                        ? owner.name.toString()
                        : name.toString();
                if (type != null) {
                    if (type.hasTag(FORALL))
                        s = "<" + type.getTypeArguments() + ">" + s;
                    s += "(" + type.argtypes((flags() & VARARGS) != 0) + ")";
                }
                return s;
            }
        }

        public boolean isDynamic() {
            return false;
        }

        public Symbol implemented(TypeSymbol c, Types types) {
            Symbol impl = null;
            for (List<Type> is = types.interfaces(c.type);
                 impl == null && is.nonEmpty();
                 is = is.tail) {
                TypeSymbol i = is.head.tsym;
                impl = implementedIn(i, types);
                if (impl == null)
                    impl = implemented(i, types);
            }
            return impl;
        }

        public Symbol implementedIn(TypeSymbol c, Types types) {
            Symbol impl = null;
            for (Scope.Entry e = c.members().lookup(name);
                 impl == null && e.scope != null;
                 e = e.next()) {
                if (this.overrides(e.sym, (TypeSymbol) owner, types, true) &&


                        types.isSameType(type.getReturnType(),
                                types.memberType(owner.type, e.sym).getReturnType())) {
                    impl = e.sym;
                }
            }
            return impl;
        }

        public boolean binaryOverrides(Symbol _other, TypeSymbol origin, Types types) {
            if (isConstructor() || _other.kind != MTH) return false;
            if (this == _other) return true;
            MethodSymbol other = (MethodSymbol) _other;

            if (other.isOverridableIn((TypeSymbol) owner) &&
                    types.asSuper(owner.type, other.owner) != null &&
                    types.isSameType(erasure(types), other.erasure(types)))
                return true;

            return
                    (flags() & ABSTRACT) == 0 &&
                            other.isOverridableIn(origin) &&
                            this.isMemberOf(origin, types) &&
                            types.isSameType(erasure(types), other.erasure(types));
        }

        public MethodSymbol binaryImplementation(ClassSymbol origin, Types types) {
            for (TypeSymbol c = origin; c != null; c = types.supertype(c.type).tsym) {
                for (Scope.Entry e = c.members().lookup(name);
                     e.scope != null;
                     e = e.next()) {
                    if (e.sym.kind == MTH &&
                            ((MethodSymbol) e.sym).binaryOverrides(this, origin, types))
                        return (MethodSymbol) e.sym;
                }
            }
            return null;
        }

        public boolean overrides(Symbol _other, TypeSymbol origin, Types types, boolean checkResult) {
            if (isConstructor() || _other.kind != MTH) return false;
            if (this == _other) return true;
            MethodSymbol other = (MethodSymbol) _other;

            if (other.isOverridableIn((TypeSymbol) owner) &&
                    types.asSuper(owner.type, other.owner) != null) {
                Type mt = types.memberType(owner.type, this);
                Type ot = types.memberType(owner.type, other);
                if (types.isSubSignature(mt, ot)) {
                    if (!checkResult)
                        return true;
                    if (types.returnTypeSubstitutable(mt, ot))
                        return true;
                }
            }

            if ((flags() & ABSTRACT) != 0 ||
                    ((other.flags() & ABSTRACT) == 0 && (other.flags() & DEFAULT) == 0) ||
                    !other.isOverridableIn(origin) ||
                    !this.isMemberOf(origin, types))
                return false;

            Type mt = types.memberType(origin.type, this);
            Type ot = types.memberType(origin.type, other);
            return
                    types.isSubSignature(mt, ot) &&
                            (!checkResult || types.resultSubtype(mt, ot, types.noWarnings));
        }

        private boolean isOverridableIn(TypeSymbol origin) {

            switch ((int) (flags_field & Flags.AccessFlags)) {
                case Flags.PRIVATE:
                    return false;
                case Flags.PUBLIC:
                    return !this.owner.isInterface() ||
                            (flags_field & STATIC) == 0;
                case Flags.PROTECTED:
                    return (origin.flags() & INTERFACE) == 0;
                case 0:


                    return
                            this.packge() == origin.packge() &&
                                    (origin.flags() & INTERFACE) == 0;
                default:
                    return false;
            }
        }

        @Override
        public boolean isInheritedIn(Symbol clazz, Types types) {
            switch ((int) (flags_field & Flags.AccessFlags)) {
                case PUBLIC:
                    return !this.owner.isInterface() ||
                            clazz == owner ||
                            (flags_field & STATIC) == 0;
                default:
                    return super.isInheritedIn(clazz, types);
            }
        }

        public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult) {
            return implementation(origin, types, checkResult, implementation_filter);
        }

        public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult, Filter<Symbol> implFilter) {
            MethodSymbol res = types.implementation(this, origin, checkResult, implFilter);
            if (res != null)
                return res;


            if (types.isDerivedRaw(origin.type) && !origin.isInterface())
                return implementation(types.supertype(origin.type).tsym, types, checkResult);
            else
                return null;
        }

        public List<VarSymbol> params() {
            owner.complete();
            if (params == null) {


                List<Name> paramNames = savedParameterNames;
                savedParameterNames = null;

                if (paramNames == null || paramNames.size() != type.getParameterTypes().size()) {
                    paramNames = List.nil();
                }
                ListBuffer<VarSymbol> buf = new ListBuffer<VarSymbol>();
                List<Name> remaining = paramNames;


                int i = 0;
                for (Type t : type.getParameterTypes()) {
                    Name paramName;
                    if (remaining.isEmpty()) {

                        paramName = createArgName(i, paramNames);
                    } else {
                        paramName = remaining.head;
                        remaining = remaining.tail;
                        if (paramName.isEmpty()) {

                            paramName = createArgName(i, paramNames);
                        }
                    }
                    buf.append(new VarSymbol(PARAMETER, paramName, t, this));
                    i++;
                }
                params = buf.toList();
            }
            return params;
        }


        private Name createArgName(int index, List<Name> exclude) {
            String prefix = "arg";
            while (true) {
                Name argName = name.table.fromString(prefix + index);
                if (!exclude.contains(argName))
                    return argName;
                prefix += "$";
            }
        }

        public Symbol asMemberOf(Type site, Types types) {
            return new MethodSymbol(flags_field, name, types.memberType(site, this), owner);
        }

        public ElementKind getKind() {
            if (name == name.table.names.init)
                return ElementKind.CONSTRUCTOR;
            else if (name == name.table.names.clinit)
                return ElementKind.STATIC_INIT;
            else if ((flags() & BLOCK) != 0)
                return isStatic() ? ElementKind.STATIC_INIT : ElementKind.INSTANCE_INIT;
            else
                return ElementKind.METHOD;
        }

        public boolean isStaticOrInstanceInit() {
            return getKind() == ElementKind.STATIC_INIT ||
                    getKind() == ElementKind.INSTANCE_INIT;
        }

        public Attribute getDefaultValue() {
            return defaultValue;
        }

        public List<VarSymbol> getParameters() {
            return params();
        }

        public boolean isVarArgs() {
            return (flags() & VARARGS) != 0;
        }

        public boolean isDefault() {
            return (flags() & DEFAULT) != 0;
        }

        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitMethodSymbol(this, p);
        }

        public Type getReceiverType() {
            return asType().getReceiverType();
        }

        public Type getReturnType() {
            return asType().getReturnType();
        }

        public List<Type> getThrownTypes() {
            return asType().getThrownTypes();
        }
    }

    public static class DynamicMethodSymbol extends MethodSymbol {
        public Object[] staticArgs;
        public Symbol bsm;
        public int bsmKind;

        public DynamicMethodSymbol(Name name, Symbol owner, int bsmKind, MethodSymbol bsm, Type type, Object[] staticArgs) {
            super(0, name, type, owner);
            this.bsm = bsm;
            this.bsmKind = bsmKind;
            this.staticArgs = staticArgs;
        }

        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    public static class OperatorSymbol extends MethodSymbol {
        public int opcode;

        public OperatorSymbol(Name name, Type type, int opcode, Symbol owner) {
            super(PUBLIC | STATIC, name, type, owner);
            this.opcode = opcode;
        }

        public <R, P> R accept(Visitor<R, P> v, P p) {
            return v.visitOperatorSymbol(this, p);
        }
    }

    public static class CompletionFailure extends RuntimeException {
        private static final long serialVersionUID = 0;
        public Symbol sym;

        public JCDiagnostic diag;

        @Deprecated
        public String errmsg;

        public CompletionFailure(Symbol sym, String errmsg) {
            this.sym = sym;
            this.errmsg = errmsg;
        }

        public CompletionFailure(Symbol sym, JCDiagnostic diag) {
            this.sym = sym;
            this.diag = diag;
        }

        public JCDiagnostic getDiagnostic() {
            return diag;
        }

        @Override
        public String getMessage() {
            if (diag != null)
                return diag.getMessage(null);
            else
                return errmsg;
        }

        public Object getDetailValue() {
            return (diag != null ? diag : errmsg);
        }

        @Override
        public CompletionFailure initCause(Throwable cause) {
            super.initCause(cause);
            return this;
        }
    }
}
