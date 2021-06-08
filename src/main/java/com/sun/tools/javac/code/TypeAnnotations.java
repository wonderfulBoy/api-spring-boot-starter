package com.sun.tools.javac.code;

import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type.Visitor;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.TypeAnnotationPosition.TypePathEntry;
import com.sun.tools.javac.code.TypeAnnotationPosition.TypePathEntryKind;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Annotate.Worker;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;

public class TypeAnnotations {
    protected static final Context.Key<TypeAnnotations> typeAnnosKey =
            new Context.Key<TypeAnnotations>();
    final Log log;
    final Names names;
    final Symtab syms;
    final Annotate annotate;
    final Attr attr;
    protected TypeAnnotations(Context context) {
        context.put(typeAnnosKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        annotate = Annotate.instance(context);
        attr = Attr.instance(context);
        Options options = Options.instance(context);
    }

    public static TypeAnnotations instance(Context context) {
        TypeAnnotations instance = context.get(typeAnnosKey);
        if (instance == null)
            instance = new TypeAnnotations(context);
        return instance;
    }

    private static AnnotationType inferTargetMetaInfo(Attribute.Compound a, Symbol s) {
        return AnnotationType.DECLARATION;
    }

    public void organizeTypeAnnotationsSignatures(final Env<AttrContext> env, final JCClassDecl tree) {
        annotate.afterRepeated(new Worker() {
            @Override
            public void run() {
                JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);
                try {
                    new TypeAnnotationPositions(true).scan(tree);
                } finally {
                    log.useSource(oldSource);
                }
            }
        });
    }

    public void validateTypeAnnotationsSignatures(final Env<AttrContext> env, final JCClassDecl tree) {
        annotate.validate(new Worker() {
            @Override
            public void run() {
                JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);
                try {
                    attr.validateTypeAnnotations(tree, true);
                } finally {
                    log.useSource(oldSource);
                }
            }
        });
    }

    public void organizeTypeAnnotationsBodies(JCClassDecl tree) {
        new TypeAnnotationPositions(false).scan(tree);
    }

    public AnnotationType annotationType(Attribute.Compound a, Symbol s) {
        Attribute.Compound atTarget =
                a.type.tsym.attribute(syms.annotationTargetType.tsym);
        if (atTarget == null) {
            return inferTargetMetaInfo(a, s);
        }
        Attribute atValue = atTarget.member(names.value);
        if (!(atValue instanceof Attribute.Array)) {
            Assert.error("annotationType(): bad @Target argument " + atValue +
                    " (" + atValue.getClass() + ")");
            return AnnotationType.DECLARATION;
        }
        Attribute.Array arr = (Attribute.Array) atValue;
        boolean isDecl = false, isType = false;
        for (Attribute app : arr.values) {
            if (!(app instanceof Attribute.Enum)) {
                Assert.error("annotationType(): unrecognized Attribute kind " + app +
                        " (" + app.getClass() + ")");
                isDecl = true;
                continue;
            }
            Attribute.Enum e = (Attribute.Enum) app;
            if (e.value.name == names.TYPE) {
                if (s.kind == Kinds.TYP)
                    isDecl = true;
            } else if (e.value.name == names.FIELD) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind != Kinds.MTH)
                    isDecl = true;
            } else if (e.value.name == names.METHOD) {
                if (s.kind == Kinds.MTH &&
                        !s.isConstructor())
                    isDecl = true;
            } else if (e.value.name == names.PARAMETER) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind == Kinds.MTH &&
                        (s.flags() & Flags.PARAMETER) != 0)
                    isDecl = true;
            } else if (e.value.name == names.CONSTRUCTOR) {
                if (s.kind == Kinds.MTH &&
                        s.isConstructor())
                    isDecl = true;
            } else if (e.value.name == names.LOCAL_VARIABLE) {
                if (s.kind == Kinds.VAR &&
                        s.owner.kind == Kinds.MTH &&
                        (s.flags() & Flags.PARAMETER) == 0)
                    isDecl = true;
            } else if (e.value.name == names.ANNOTATION_TYPE) {
                if (s.kind == Kinds.TYP &&
                        (s.flags() & Flags.ANNOTATION) != 0)
                    isDecl = true;
            } else if (e.value.name == names.PACKAGE) {
                if (s.kind == Kinds.PCK)
                    isDecl = true;
            } else if (e.value.name == names.TYPE_USE) {
                if (s.kind == Kinds.TYP ||
                        s.kind == Kinds.VAR ||
                        (s.kind == Kinds.MTH && !s.isConstructor() &&
                                !s.type.getReturnType().hasTag(TypeTag.VOID)) ||
                        (s.kind == Kinds.MTH && s.isConstructor()))
                    isType = true;
            } else if (e.value.name == names.TYPE_PARAMETER) {


            } else {
                Assert.error("annotationType(): unrecognized Attribute name " + e.value.name +
                        " (" + e.value.name.getClass() + ")");
                isDecl = true;
            }
        }
        if (isDecl && isType) {
            return AnnotationType.BOTH;
        } else if (isType) {
            return AnnotationType.TYPE;
        } else {
            return AnnotationType.DECLARATION;
        }
    }

    public enum AnnotationType {DECLARATION, TYPE, BOTH}

    private class TypeAnnotationPositions extends TreeScanner {
        private final boolean sigOnly;
        private ListBuffer<JCTree> frames = new ListBuffer<>();
        private boolean isInClass = false;
        private JCLambda currentLambda = null;

        TypeAnnotationPositions(boolean sigOnly) {
            this.sigOnly = sigOnly;
        }

        protected void push(JCTree t) {
            frames = frames.prepend(t);
        }

        protected JCTree pop() {
            return frames.next();
        }

        private JCTree peek2() {
            return frames.toList().tail.head;
        }

        @Override
        public void scan(JCTree tree) {
            push(tree);
            super.scan(tree);
            pop();
        }

        private void separateAnnotationsKinds(JCTree typetree, Type type, Symbol sym,
                                              TypeAnnotationPosition pos) {
            List<Attribute.Compound> annotations = sym.getRawAttributes();
            ListBuffer<Attribute.Compound> declAnnos = new ListBuffer<Attribute.Compound>();
            ListBuffer<TypeCompound> typeAnnos = new ListBuffer<TypeCompound>();
            ListBuffer<TypeCompound> onlyTypeAnnos = new ListBuffer<TypeCompound>();
            for (Attribute.Compound a : annotations) {
                switch (annotationType(a, sym)) {
                    case DECLARATION:
                        declAnnos.append(a);
                        break;
                    case BOTH: {
                        declAnnos.append(a);
                        TypeCompound ta = toTypeCompound(a, pos);
                        typeAnnos.append(ta);
                        break;
                    }
                    case TYPE: {
                        TypeCompound ta = toTypeCompound(a, pos);
                        typeAnnos.append(ta);

                        onlyTypeAnnos.append(ta);
                        break;
                    }
                }
            }
            sym.resetAnnotations();
            sym.setDeclarationAttributes(declAnnos.toList());
            if (typeAnnos.isEmpty()) {
                return;
            }
            List<TypeCompound> typeAnnotations = typeAnnos.toList();
            if (type == null) {


                type = sym.getEnclosingElement().asType();


                type = typeWithAnnotations(typetree, type, typeAnnotations, typeAnnotations);


                sym.appendUniqueTypeAttributes(typeAnnotations);
                return;
            }

            type = typeWithAnnotations(typetree, type, typeAnnotations, onlyTypeAnnos.toList());
            if (sym.getKind() == ElementKind.METHOD) {
                sym.type.asMethodType().restype = type;
            } else if (sym.getKind() == ElementKind.PARAMETER) {
                sym.type = type;
                if (sym.getQualifiedName().equals(names._this)) {
                    sym.owner.type.asMethodType().recvtype = type;

                } else {
                    MethodType methType = sym.owner.type.asMethodType();
                    List<VarSymbol> params = ((MethodSymbol) sym.owner).params;
                    List<Type> oldArgs = methType.argtypes;
                    ListBuffer<Type> newArgs = new ListBuffer<Type>();
                    while (params.nonEmpty()) {
                        if (params.head == sym) {
                            newArgs.add(type);
                        } else {
                            newArgs.add(oldArgs.head);
                        }
                        oldArgs = oldArgs.tail;
                        params = params.tail;
                    }
                    methType.argtypes = newArgs.toList();
                }
            } else {
                sym.type = type;
            }
            sym.appendUniqueTypeAttributes(typeAnnotations);
            if (sym.getKind() == ElementKind.PARAMETER ||
                    sym.getKind() == ElementKind.LOCAL_VARIABLE ||
                    sym.getKind() == ElementKind.RESOURCE_VARIABLE ||
                    sym.getKind() == ElementKind.EXCEPTION_PARAMETER) {


                sym.owner.appendUniqueTypeAttributes(sym.getRawTypeAttributes());
            }
        }

        private Type typeWithAnnotations(final JCTree typetree, final Type type,
                                         final List<TypeCompound> annotations,
                                         final List<TypeCompound> onlyTypeAnnotations) {


            if (annotations.isEmpty()) {
                return type;
            }
            if (type.hasTag(TypeTag.ARRAY)) {
                ArrayType arType = (ArrayType) type.unannotatedType();
                ArrayType tomodify = new ArrayType(null, arType.tsym);
                Type toreturn;
                if (type.isAnnotated()) {
                    toreturn = tomodify.annotatedType(type.getAnnotationMirrors());
                } else {
                    toreturn = tomodify;
                }
                JCArrayTypeTree arTree = arrayTypeTree(typetree);
                ListBuffer<TypePathEntry> depth = new ListBuffer<>();
                depth = depth.append(TypePathEntry.ARRAY);
                while (arType.elemtype.hasTag(TypeTag.ARRAY)) {
                    if (arType.elemtype.isAnnotated()) {
                        Type aelemtype = arType.elemtype;
                        arType = (ArrayType) aelemtype.unannotatedType();
                        ArrayType prevToMod = tomodify;
                        tomodify = new ArrayType(null, arType.tsym);
                        prevToMod.elemtype = tomodify.annotatedType(arType.elemtype.getAnnotationMirrors());
                    } else {
                        arType = (ArrayType) arType.elemtype;
                        tomodify.elemtype = new ArrayType(null, arType.tsym);
                        tomodify = (ArrayType) tomodify.elemtype;
                    }
                    arTree = arrayTypeTree(arTree.elemtype);
                    depth = depth.append(TypePathEntry.ARRAY);
                }
                Type arelemType = typeWithAnnotations(arTree.elemtype, arType.elemtype, annotations, onlyTypeAnnotations);
                tomodify.elemtype = arelemType;
                {

                    TypeCompound a = annotations.get(0);
                    TypeAnnotationPosition p = a.position;
                    p.location = p.location.prependList(depth.toList());
                }
                typetree.type = toreturn;
                return toreturn;
            } else if (type.hasTag(TypeTag.TYPEVAR)) {

                return type;
            } else if (type.getKind() == TypeKind.UNION) {

                JCTypeUnion tutree = (JCTypeUnion) typetree;
                JCExpression fst = tutree.alternatives.get(0);
                Type res = typeWithAnnotations(fst, fst.type, annotations, onlyTypeAnnotations);
                fst.type = res;


                return type;
            } else {
                Type enclTy = type;
                Element enclEl = type.asElement();
                JCTree enclTr = typetree;
                while (enclEl != null &&
                        enclEl.getKind() != ElementKind.PACKAGE &&
                        enclTy != null &&
                        enclTy.getKind() != TypeKind.NONE &&
                        enclTy.getKind() != TypeKind.ERROR &&
                        (enclTr.getKind() == JCTree.Kind.MEMBER_SELECT ||
                                enclTr.getKind() == JCTree.Kind.PARAMETERIZED_TYPE ||
                                enclTr.getKind() == JCTree.Kind.ANNOTATED_TYPE)) {


                    if (enclTr.getKind() == JCTree.Kind.MEMBER_SELECT) {

                        enclTy = enclTy.getEnclosingType();
                        enclEl = enclEl.getEnclosingElement();
                        enclTr = ((JCFieldAccess) enclTr).getExpression();
                    } else if (enclTr.getKind() == JCTree.Kind.PARAMETERIZED_TYPE) {
                        enclTr = ((JCTypeApply) enclTr).getType();
                    } else {

                        enclTr = ((JCAnnotatedType) enclTr).getUnderlyingType();
                    }
                }

                if (enclTy != null &&
                        enclTy.hasTag(TypeTag.NONE)) {
                    switch (onlyTypeAnnotations.size()) {
                        case 0:


                            break;
                        case 1:
                            log.error(typetree.pos(), "cant.type.annotate.scoping.1",
                                    onlyTypeAnnotations);
                            break;
                        default:
                            log.error(typetree.pos(), "cant.type.annotate.scoping",
                                    onlyTypeAnnotations);
                    }
                    return type;
                }


                ListBuffer<TypePathEntry> depth = new ListBuffer<>();
                Type topTy = enclTy;
                while (enclEl != null &&
                        enclEl.getKind() != ElementKind.PACKAGE &&
                        topTy != null &&
                        topTy.getKind() != TypeKind.NONE &&
                        topTy.getKind() != TypeKind.ERROR) {
                    topTy = topTy.getEnclosingType();
                    enclEl = enclEl.getEnclosingElement();
                    if (topTy != null && topTy.getKind() != TypeKind.NONE) {

                        depth = depth.append(TypePathEntry.INNER_TYPE);
                    }
                }
                if (depth.nonEmpty()) {


                    TypeCompound a = annotations.get(0);
                    TypeAnnotationPosition p = a.position;
                    p.location = p.location.appendList(depth.toList());
                }
                Type ret = typeWithAnnotations(type, enclTy, annotations);
                typetree.type = ret;
                return ret;
            }
        }

        private JCArrayTypeTree arrayTypeTree(JCTree typetree) {
            if (typetree.getKind() == JCTree.Kind.ARRAY_TYPE) {
                return (JCArrayTypeTree) typetree;
            } else if (typetree.getKind() == JCTree.Kind.ANNOTATED_TYPE) {
                return (JCArrayTypeTree) ((JCAnnotatedType) typetree).underlyingType;
            } else {
                Assert.error("Could not determine array type from type tree: " + typetree);
                return null;
            }
        }

        private Type typeWithAnnotations(final Type type,
                                         final Type stopAt,
                                         final List<TypeCompound> annotations) {
            Visitor<Type, List<TypeCompound>> visitor =
                    new Visitor<Type, List<TypeCompound>>() {
                        @Override
                        public Type visitClassType(ClassType t, List<TypeCompound> s) {

                            if (t == stopAt ||
                                    t.getEnclosingType() == Type.noType) {
                                return t.annotatedType(s);
                            } else {
                                ClassType ret = new ClassType(t.getEnclosingType().accept(this, s),
                                        t.typarams_field, t.tsym);
                                ret.all_interfaces_field = t.all_interfaces_field;
                                ret.allparams_field = t.allparams_field;
                                ret.interfaces_field = t.interfaces_field;
                                ret.rank_field = t.rank_field;
                                ret.supertype_field = t.supertype_field;
                                return ret;
                            }
                        }

                        @Override
                        public Type visitAnnotatedType(AnnotatedType t, List<TypeCompound> s) {
                            return t.unannotatedType().accept(this, s).annotatedType(t.getAnnotationMirrors());
                        }

                        @Override
                        public Type visitWildcardType(WildcardType t, List<TypeCompound> s) {
                            return t.annotatedType(s);
                        }

                        @Override
                        public Type visitArrayType(ArrayType t, List<TypeCompound> s) {
                            ArrayType ret = new ArrayType(t.elemtype.accept(this, s), t.tsym);
                            return ret;
                        }

                        @Override
                        public Type visitMethodType(MethodType t, List<TypeCompound> s) {

                            return t;
                        }

                        @Override
                        public Type visitPackageType(PackageType t, List<TypeCompound> s) {

                            return t;
                        }

                        @Override
                        public Type visitTypeVar(TypeVar t, List<TypeCompound> s) {
                            return t.annotatedType(s);
                        }

                        @Override
                        public Type visitCapturedType(CapturedType t, List<TypeCompound> s) {
                            return t.annotatedType(s);
                        }

                        @Override
                        public Type visitForAll(ForAll t, List<TypeCompound> s) {

                            return t;
                        }

                        @Override
                        public Type visitUndetVar(UndetVar t, List<TypeCompound> s) {

                            return t;
                        }

                        @Override
                        public Type visitErrorType(ErrorType t, List<TypeCompound> s) {
                            return t.annotatedType(s);
                        }

                        @Override
                        public Type visitType(Type t, List<TypeCompound> s) {
                            return t.annotatedType(s);
                        }
                    };
            return type.accept(visitor, annotations);
        }

        private TypeCompound toTypeCompound(Attribute.Compound a, TypeAnnotationPosition p) {

            return new TypeCompound(a, p);
        }

        private void resolveFrame(JCTree tree, JCTree frame,
                                  List<JCTree> path, TypeAnnotationPosition p) {


            switch (frame.getKind()) {
                case TYPE_CAST:
                    JCTypeCast frameTC = (JCTypeCast) frame;
                    p.type = TargetType.CAST;
                    if (frameTC.clazz.hasTag(Tag.TYPEINTERSECTION)) {

                    } else {
                        p.type_index = 0;
                    }
                    p.pos = frame.pos;
                    return;
                case INSTANCE_OF:
                    p.type = TargetType.INSTANCEOF;
                    p.pos = frame.pos;
                    return;
                case NEW_CLASS:
                    JCNewClass frameNewClass = (JCNewClass) frame;
                    if (frameNewClass.def != null) {

                        JCClassDecl frameClassDecl = frameNewClass.def;
                        if (frameClassDecl.extending == tree) {
                            p.type = TargetType.CLASS_EXTENDS;
                            p.type_index = -1;
                        } else if (frameClassDecl.implementing.contains(tree)) {
                            p.type = TargetType.CLASS_EXTENDS;
                            p.type_index = frameClassDecl.implementing.indexOf(tree);
                        } else {

                            Assert.error("Could not determine position of tree " + tree +
                                    " within frame " + frame);
                        }
                    } else if (frameNewClass.typeargs.contains(tree)) {
                        p.type = TargetType.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT;
                        p.type_index = frameNewClass.typeargs.indexOf(tree);
                    } else {
                        p.type = TargetType.NEW;
                    }
                    p.pos = frame.pos;
                    return;
                case NEW_ARRAY:
                    p.type = TargetType.NEW;
                    p.pos = frame.pos;
                    return;
                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    p.pos = frame.pos;
                    if (((JCClassDecl) frame).extending == tree) {
                        p.type = TargetType.CLASS_EXTENDS;
                        p.type_index = -1;
                    } else if (((JCClassDecl) frame).implementing.contains(tree)) {
                        p.type = TargetType.CLASS_EXTENDS;
                        p.type_index = ((JCClassDecl) frame).implementing.indexOf(tree);
                    } else if (((JCClassDecl) frame).typarams.contains(tree)) {
                        p.type = TargetType.CLASS_TYPE_PARAMETER;
                        p.parameter_index = ((JCClassDecl) frame).typarams.indexOf(tree);
                    } else {
                        Assert.error("Could not determine position of tree " + tree +
                                " within frame " + frame);
                    }
                    return;
                case METHOD: {
                    JCMethodDecl frameMethod = (JCMethodDecl) frame;
                    p.pos = frame.pos;
                    if (frameMethod.thrown.contains(tree)) {
                        p.type = TargetType.THROWS;
                        p.type_index = frameMethod.thrown.indexOf(tree);
                    } else if (frameMethod.restype == tree) {
                        p.type = TargetType.METHOD_RETURN;
                    } else if (frameMethod.typarams.contains(tree)) {
                        p.type = TargetType.METHOD_TYPE_PARAMETER;
                        p.parameter_index = frameMethod.typarams.indexOf(tree);
                    } else {
                        Assert.error("Could not determine position of tree " + tree +
                                " within frame " + frame);
                    }
                    return;
                }
                case PARAMETERIZED_TYPE: {
                    List<JCTree> newPath = path.tail;
                    if (((JCTypeApply) frame).clazz == tree) {

                    } else if (((JCTypeApply) frame).arguments.contains(tree)) {
                        JCTypeApply taframe = (JCTypeApply) frame;
                        int arg = taframe.arguments.indexOf(tree);
                        p.location = p.location.prepend(new TypePathEntry(TypePathEntryKind.TYPE_ARGUMENT, arg));
                        Type typeToUse;
                        if (newPath.tail != null && newPath.tail.head.hasTag(Tag.NEWCLASS)) {


                            typeToUse = newPath.tail.head.type;
                        } else {
                            typeToUse = taframe.type;
                        }
                        locateNestedTypes(typeToUse, p);
                    } else {
                        Assert.error("Could not determine type argument position of tree " + tree +
                                " within frame " + frame);
                    }
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                case MEMBER_REFERENCE: {
                    JCMemberReference mrframe = (JCMemberReference) frame;
                    if (mrframe.expr == tree) {
                        switch (mrframe.mode) {
                            case INVOKE:
                                p.type = TargetType.METHOD_REFERENCE;
                                break;
                            case NEW:
                                p.type = TargetType.CONSTRUCTOR_REFERENCE;
                                break;
                            default:
                                Assert.error("Unknown method reference mode " + mrframe.mode +
                                        " for tree " + tree + " within frame " + frame);
                        }
                        p.pos = frame.pos;
                    } else if (mrframe.typeargs != null &&
                            mrframe.typeargs.contains(tree)) {
                        int arg = mrframe.typeargs.indexOf(tree);
                        p.type_index = arg;
                        switch (mrframe.mode) {
                            case INVOKE:
                                p.type = TargetType.METHOD_REFERENCE_TYPE_ARGUMENT;
                                break;
                            case NEW:
                                p.type = TargetType.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT;
                                break;
                            default:
                                Assert.error("Unknown method reference mode " + mrframe.mode +
                                        " for tree " + tree + " within frame " + frame);
                        }
                        p.pos = frame.pos;
                    } else {
                        Assert.error("Could not determine type argument position of tree " + tree +
                                " within frame " + frame);
                    }
                    return;
                }
                case ARRAY_TYPE: {
                    ListBuffer<TypePathEntry> index = new ListBuffer<>();
                    index = index.append(TypePathEntry.ARRAY);
                    List<JCTree> newPath = path.tail;
                    while (true) {
                        JCTree npHead = newPath.tail.head;
                        if (npHead.hasTag(Tag.TYPEARRAY)) {
                            newPath = newPath.tail;
                            index = index.append(TypePathEntry.ARRAY);
                        } else if (npHead.hasTag(Tag.ANNOTATED_TYPE)) {
                            newPath = newPath.tail;
                        } else {
                            break;
                        }
                    }
                    p.location = p.location.prependList(index.toList());
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                case TYPE_PARAMETER:
                    if (path.tail.tail.head.hasTag(Tag.CLASSDEF)) {
                        JCClassDecl clazz = (JCClassDecl) path.tail.tail.head;
                        p.type = TargetType.CLASS_TYPE_PARAMETER_BOUND;
                        p.parameter_index = clazz.typarams.indexOf(path.tail.head);
                        p.bound_index = ((JCTypeParameter) frame).bounds.indexOf(tree);
                        if (((JCTypeParameter) frame).bounds.get(0).type.isInterface()) {

                            p.bound_index += 1;
                        }
                    } else if (path.tail.tail.head.hasTag(Tag.METHODDEF)) {
                        JCMethodDecl method = (JCMethodDecl) path.tail.tail.head;
                        p.type = TargetType.METHOD_TYPE_PARAMETER_BOUND;
                        p.parameter_index = method.typarams.indexOf(path.tail.head);
                        p.bound_index = ((JCTypeParameter) frame).bounds.indexOf(tree);
                        if (((JCTypeParameter) frame).bounds.get(0).type.isInterface()) {

                            p.bound_index += 1;
                        }
                    } else {
                        Assert.error("Could not determine position of tree " + tree +
                                " within frame " + frame);
                    }
                    p.pos = frame.pos;
                    return;
                case VARIABLE:
                    VarSymbol v = ((JCVariableDecl) frame).sym;
                    p.pos = frame.pos;
                    switch (v.getKind()) {
                        case LOCAL_VARIABLE:
                            p.type = TargetType.LOCAL_VARIABLE;
                            break;
                        case FIELD:
                            p.type = TargetType.FIELD;
                            break;
                        case PARAMETER:
                            if (v.getQualifiedName().equals(names._this)) {

                                p.type = TargetType.METHOD_RECEIVER;
                            } else {
                                p.type = TargetType.METHOD_FORMAL_PARAMETER;
                                p.parameter_index = methodParamIndex(path, frame);
                            }
                            break;
                        case EXCEPTION_PARAMETER:
                            p.type = TargetType.EXCEPTION_PARAMETER;
                            break;
                        case RESOURCE_VARIABLE:
                            p.type = TargetType.RESOURCE_VARIABLE;
                            break;
                        default:
                            Assert.error("Found unexpected type annotation for variable: " + v + " with kind: " + v.getKind());
                    }
                    if (v.getKind() != ElementKind.FIELD) {
                        v.owner.appendUniqueTypeAttributes(v.getRawTypeAttributes());
                    }
                    return;
                case ANNOTATED_TYPE: {
                    if (frame == tree) {


                        JCAnnotatedType atypetree = (JCAnnotatedType) frame;
                        final Type utype = atypetree.underlyingType.type;
                        if (utype == null) {


                            return;
                        }
                        Symbol tsym = utype.tsym;
                        if (tsym.getKind().equals(ElementKind.TYPE_PARAMETER) ||
                                utype.getKind().equals(TypeKind.WILDCARD) ||
                                utype.getKind().equals(TypeKind.ARRAY)) {


                        } else {
                            locateNestedTypes(utype, p);
                        }
                    }
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                case UNION_TYPE: {
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                case INTERSECTION_TYPE: {
                    JCTypeIntersection isect = (JCTypeIntersection) frame;
                    p.type_index = isect.bounds.indexOf(tree);
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                case METHOD_INVOCATION: {
                    JCMethodInvocation invocation = (JCMethodInvocation) frame;
                    if (!invocation.typeargs.contains(tree)) {
                        Assert.error("{" + tree + "} is not an argument in the invocation: " + invocation);
                    }
                    MethodSymbol exsym = (MethodSymbol) TreeInfo.symbol(invocation.getMethodSelect());
                    if (exsym == null) {
                        Assert.error("could not determine symbol for {" + invocation + "}");
                    } else if (exsym.isConstructor()) {
                        p.type = TargetType.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT;
                    } else {
                        p.type = TargetType.METHOD_INVOCATION_TYPE_ARGUMENT;
                    }
                    p.pos = invocation.pos;
                    p.type_index = invocation.typeargs.indexOf(tree);
                    return;
                }
                case EXTENDS_WILDCARD:
                case SUPER_WILDCARD: {

                    p.location = p.location.prepend(TypePathEntry.WILDCARD);
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                case MEMBER_SELECT: {
                    List<JCTree> newPath = path.tail;
                    resolveFrame(newPath.head, newPath.tail.head, newPath, p);
                    return;
                }
                default:
                    Assert.error("Unresolved frame: " + frame + " of kind: " + frame.getKind() +
                            "\n    Looking for tree: " + tree);
                    return;
            }
        }

        private void locateNestedTypes(Type type, TypeAnnotationPosition p) {


            ListBuffer<TypePathEntry> depth = new ListBuffer<>();
            Type encl = type.getEnclosingType();
            while (encl != null &&
                    encl.getKind() != TypeKind.NONE &&
                    encl.getKind() != TypeKind.ERROR) {
                depth = depth.append(TypePathEntry.INNER_TYPE);
                encl = encl.getEnclosingType();
            }
            if (depth.nonEmpty()) {
                p.location = p.location.prependList(depth.toList());
            }
        }

        private int methodParamIndex(List<JCTree> path, JCTree param) {
            List<JCTree> curr = path;
            while (curr.head.getTag() != Tag.METHODDEF &&
                    curr.head.getTag() != Tag.LAMBDA) {
                curr = curr.tail;
            }
            if (curr.head.getTag() == Tag.METHODDEF) {
                JCMethodDecl method = (JCMethodDecl) curr.head;
                return method.params.indexOf(param);
            } else if (curr.head.getTag() == Tag.LAMBDA) {
                JCLambda lambda = (JCLambda) curr.head;
                return lambda.params.indexOf(param);
            } else {
                Assert.error("methodParamIndex expected to find method or lambda for param: " + param);
                return -1;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            if (isInClass)
                return;
            isInClass = true;
            if (sigOnly) {
                scan(tree.mods);
                scan(tree.typarams);
                scan(tree.extending);
                scan(tree.implementing);
            }
            scan(tree.defs);
        }

        @Override
        public void visitMethodDef(final JCMethodDecl tree) {
            if (tree.sym == null) {
                Assert.error("Visiting tree node before memberEnter");
            }
            if (sigOnly) {
                if (!tree.mods.annotations.isEmpty()) {


                    TypeAnnotationPosition pos = new TypeAnnotationPosition();
                    pos.type = TargetType.METHOD_RETURN;
                    if (tree.sym.isConstructor()) {
                        pos.pos = tree.pos;

                        separateAnnotationsKinds(tree, null, tree.sym, pos);
                    } else {
                        pos.pos = tree.restype.pos;
                        separateAnnotationsKinds(tree.restype, tree.sym.type.getReturnType(),
                                tree.sym, pos);
                    }
                }
                if (tree.recvparam != null && tree.recvparam.sym != null &&
                        !tree.recvparam.mods.annotations.isEmpty()) {


                    TypeAnnotationPosition pos = new TypeAnnotationPosition();
                    pos.type = TargetType.METHOD_RECEIVER;
                    pos.pos = tree.recvparam.vartype.pos;
                    separateAnnotationsKinds(tree.recvparam.vartype, tree.recvparam.sym.type,
                            tree.recvparam.sym, pos);
                }
                int i = 0;
                for (JCVariableDecl param : tree.params) {
                    if (!param.mods.annotations.isEmpty()) {


                        TypeAnnotationPosition pos = new TypeAnnotationPosition();
                        pos.type = TargetType.METHOD_FORMAL_PARAMETER;
                        pos.parameter_index = i;
                        pos.pos = param.vartype.pos;
                        separateAnnotationsKinds(param.vartype, param.sym.type, param.sym, pos);
                    }
                    ++i;
                }
            }
            push(tree);

            if (sigOnly) {
                scan(tree.mods);
                scan(tree.restype);
                scan(tree.typarams);
                scan(tree.recvparam);
                scan(tree.params);
                scan(tree.thrown);
            } else {
                scan(tree.defaultValue);
                scan(tree.body);
            }
            pop();
        }

        public void visitLambda(JCLambda tree) {
            JCLambda prevLambda = currentLambda;
            try {
                currentLambda = tree;
                int i = 0;
                for (JCVariableDecl param : tree.params) {
                    if (!param.mods.annotations.isEmpty()) {


                        TypeAnnotationPosition pos = new TypeAnnotationPosition();
                        pos.type = TargetType.METHOD_FORMAL_PARAMETER;
                        pos.parameter_index = i;
                        pos.pos = param.vartype.pos;
                        pos.onLambda = tree;
                        separateAnnotationsKinds(param.vartype, param.sym.type, param.sym, pos);
                    }
                    ++i;
                }
                push(tree);
                scan(tree.body);
                scan(tree.params);
                pop();
            } finally {
                currentLambda = prevLambda;
            }
        }

        @Override
        public void visitVarDef(final JCVariableDecl tree) {
            if (tree.mods.annotations.isEmpty()) {


            } else if (tree.sym == null) {
                Assert.error("Visiting tree node before memberEnter");
            } else if (tree.sym.getKind() == ElementKind.PARAMETER) {

            } else if (tree.sym.getKind() == ElementKind.FIELD) {
                if (sigOnly) {
                    TypeAnnotationPosition pos = new TypeAnnotationPosition();
                    pos.type = TargetType.FIELD;
                    pos.pos = tree.pos;
                    separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
                }
            } else if (tree.sym.getKind() == ElementKind.LOCAL_VARIABLE) {
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.LOCAL_VARIABLE;
                pos.pos = tree.pos;
                pos.onLambda = currentLambda;
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.EXCEPTION_PARAMETER) {
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.EXCEPTION_PARAMETER;
                pos.pos = tree.pos;
                pos.onLambda = currentLambda;
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.RESOURCE_VARIABLE) {
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.RESOURCE_VARIABLE;
                pos.pos = tree.pos;
                pos.onLambda = currentLambda;
                separateAnnotationsKinds(tree.vartype, tree.sym.type, tree.sym, pos);
            } else if (tree.sym.getKind() == ElementKind.ENUM_CONSTANT) {

            } else {

                Assert.error("Unhandled variable kind: " + tree + " of kind: " + tree.sym.getKind());
            }
            push(tree);

            scan(tree.mods);
            scan(tree.vartype);
            if (!sigOnly) {
                scan(tree.init);
            }
            pop();
        }

        @Override
        public void visitBlock(JCBlock tree) {


            if (!sigOnly) {
                scan(tree.stats);
            }
        }

        @Override
        public void visitAnnotatedType(JCAnnotatedType tree) {
            push(tree);
            findPosition(tree, tree, tree.annotations);
            pop();
            super.visitAnnotatedType(tree);
        }

        @Override
        public void visitTypeParameter(JCTypeParameter tree) {
            findPosition(tree, peek2(), tree.annotations);
            super.visitTypeParameter(tree);
        }

        private void copyNewClassAnnotationsToOwner(JCNewClass tree) {
            Symbol sym = tree.def.sym;
            TypeAnnotationPosition pos = new TypeAnnotationPosition();
            ListBuffer<TypeCompound> newattrs =
                    new ListBuffer<TypeCompound>();
            for (TypeCompound old : sym.getRawTypeAttributes()) {
                newattrs.append(new TypeCompound(old.type, old.values,
                        pos));
            }
            pos.type = TargetType.NEW;
            pos.pos = tree.pos;
            sym.owner.appendUniqueTypeAttributes(newattrs.toList());
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            if (tree.def != null &&
                    !tree.def.mods.annotations.isEmpty()) {
                JCClassDecl classdecl = tree.def;
                TypeAnnotationPosition pos = new TypeAnnotationPosition();
                pos.type = TargetType.CLASS_EXTENDS;
                pos.pos = tree.pos;
                if (classdecl.extending == tree.clazz) {
                    pos.type_index = -1;
                } else if (classdecl.implementing.contains(tree.clazz)) {
                    pos.type_index = classdecl.implementing.indexOf(tree.clazz);
                } else {

                    Assert.error("Could not determine position of tree " + tree);
                }
                Type before = classdecl.sym.type;
                separateAnnotationsKinds(classdecl, tree.clazz.type, classdecl.sym, pos);
                copyNewClassAnnotationsToOwner(tree);


                classdecl.sym.type = before;
            }
            scan(tree.encl);
            scan(tree.typeargs);
            scan(tree.clazz);
            scan(tree.args);


        }

        @Override
        public void visitNewArray(JCNewArray tree) {
            findPosition(tree, tree, tree.annotations);
            int dimAnnosCount = tree.dimAnnotations.size();
            ListBuffer<TypePathEntry> depth = new ListBuffer<>();

            for (int i = 0; i < dimAnnosCount; ++i) {
                TypeAnnotationPosition p = new TypeAnnotationPosition();
                p.pos = tree.pos;
                p.onLambda = currentLambda;
                p.type = TargetType.NEW;
                if (i != 0) {
                    depth = depth.append(TypePathEntry.ARRAY);
                    p.location = p.location.appendList(depth.toList());
                }
                setTypeAnnotationPos(tree.dimAnnotations.get(i), p);
            }


            JCExpression elemType = tree.elemtype;
            depth = depth.append(TypePathEntry.ARRAY);
            while (elemType != null) {
                if (elemType.hasTag(Tag.ANNOTATED_TYPE)) {
                    JCAnnotatedType at = (JCAnnotatedType) elemType;
                    TypeAnnotationPosition p = new TypeAnnotationPosition();
                    p.type = TargetType.NEW;
                    p.pos = tree.pos;
                    p.onLambda = currentLambda;
                    locateNestedTypes(elemType.type, p);
                    p.location = p.location.prependList(depth.toList());
                    setTypeAnnotationPos(at.annotations, p);
                    elemType = at.underlyingType;
                } else if (elemType.hasTag(Tag.TYPEARRAY)) {
                    depth = depth.append(TypePathEntry.ARRAY);
                    elemType = ((JCArrayTypeTree) elemType).elemtype;
                } else if (elemType.hasTag(Tag.SELECT)) {
                    elemType = ((JCFieldAccess) elemType).selected;
                } else {
                    break;
                }
            }
            scan(tree.elems);
        }

        private void findPosition(JCTree tree, JCTree frame, List<JCAnnotation> annotations) {
            if (!annotations.isEmpty()) {

                TypeAnnotationPosition p = new TypeAnnotationPosition();
                p.onLambda = currentLambda;
                resolveFrame(tree, frame, frames.toList(), p);
                setTypeAnnotationPos(annotations, p);
            }
        }

        private void setTypeAnnotationPos(List<JCAnnotation> annotations,
                                          TypeAnnotationPosition position) {
            for (JCAnnotation anno : annotations) {


                if (anno.attribute != null) {
                    ((TypeCompound) anno.attribute).position = position;
                }
            }
        }

        @Override
        public String toString() {
            return super.toString() + ": sigOnly: " + sigOnly;
        }
    }
}
