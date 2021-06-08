package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.CompletionFailure;
import com.github.api.sun.tools.javac.code.Symbol.MethodSymbol;
import com.github.api.sun.tools.javac.code.Symbol.TypeSymbol;
import com.github.api.sun.tools.javac.code.Symbol.VarSymbol;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.tree.TreeInfo;
import com.github.api.sun.tools.javac.tree.TreeMaker;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.Map;

import static com.github.api.sun.tools.javac.code.TypeTag.ARRAY;
import static com.github.api.sun.tools.javac.code.TypeTag.CLASS;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.*;

public class Annotate {
    protected static final Context.Key<Annotate> annotateKey =
            new Context.Key<Annotate>();
    final Attr attr;
    final TreeMaker make;
    final Log log;
    final Symtab syms;
    final Names names;
    final Resolve rs;
    final Types types;
    final ConstFold cfolder;
    final Check chk;
    ListBuffer<Worker> q = new ListBuffer<Worker>();
    ListBuffer<Worker> typesQ = new ListBuffer<Worker>();
    ListBuffer<Worker> repeatedQ = new ListBuffer<Worker>();
    ListBuffer<Worker> afterRepeatedQ = new ListBuffer<Worker>();
    ListBuffer<Worker> validateQ = new ListBuffer<Worker>();
    private int enterCount = 0;
    protected Annotate(Context context) {
        context.put(annotateKey, this);
        attr = Attr.instance(context);
        make = TreeMaker.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        names = Names.instance(context);
        rs = Resolve.instance(context);
        types = Types.instance(context);
        cfolder = ConstFold.instance(context);
        chk = Check.instance(context);
    }

    public static Annotate instance(Context context) {
        Annotate instance = context.get(annotateKey);
        if (instance == null)
            instance = new Annotate(context);
        return instance;
    }

    public void earlier(Worker a) {
        q.prepend(a);
    }

    public void normal(Worker a) {
        q.append(a);
    }

    public void typeAnnotation(Worker a) {
        typesQ.append(a);
    }

    public void repeated(Worker a) {
        repeatedQ.append(a);
    }

    public void afterRepeated(Worker a) {
        afterRepeatedQ.append(a);
    }

    public void validate(Worker a) {
        validateQ.append(a);
    }

    public void enterStart() {
        enterCount++;
    }

    public void enterDone() {
        enterCount--;
        flush();
    }

    public void enterDoneWithoutFlush() {
        enterCount--;
    }

    public void flush() {
        if (enterCount != 0) return;
        enterCount++;
        try {
            while (q.nonEmpty()) {
                q.next().run();
            }
            while (typesQ.nonEmpty()) {
                typesQ.next().run();
            }
            while (repeatedQ.nonEmpty()) {
                repeatedQ.next().run();
            }
            while (afterRepeatedQ.nonEmpty()) {
                afterRepeatedQ.next().run();
            }
            while (validateQ.nonEmpty()) {
                validateQ.next().run();
            }
        } finally {
            enterCount--;
        }
    }

    Attribute.Compound enterAnnotation(JCAnnotation a,
                                       Type expected,
                                       Env<AttrContext> env) {
        return enterAnnotation(a, expected, env, false);
    }

    Attribute.TypeCompound enterTypeAnnotation(JCAnnotation a,
                                               Type expected,
                                               Env<AttrContext> env) {
        return (Attribute.TypeCompound) enterAnnotation(a, expected, env, true);
    }

    Attribute.Compound enterAnnotation(JCAnnotation a,
                                       Type expected,
                                       Env<AttrContext> env,
                                       boolean typeAnnotation) {


        Type at = (a.annotationType.type != null ? a.annotationType.type
                : attr.attribType(a.annotationType, env));
        a.type = chk.checkType(a.annotationType.pos(), at, expected);
        if (a.type.isErroneous()) {

            attr.postAttr(a);
            if (typeAnnotation) {
                return new Attribute.TypeCompound(a.type, List.nil(),
                        new TypeAnnotationPosition());
            } else {
                return new Attribute.Compound(a.type, List.nil());
            }
        }
        if ((a.type.tsym.flags() & Flags.ANNOTATION) == 0) {
            log.error(a.annotationType.pos(),
                    "not.annotation.type", a.type.toString());

            attr.postAttr(a);
            if (typeAnnotation) {
                return new Attribute.TypeCompound(a.type, List.nil(), null);
            } else {
                return new Attribute.Compound(a.type, List.nil());
            }
        }
        List<JCExpression> args = a.args;
        if (args.length() == 1 && !args.head.hasTag(ASSIGN)) {

            args.head = make.at(args.head.pos).
                    Assign(make.Ident(names.value), args.head);
        }
        ListBuffer<Pair<MethodSymbol, Attribute>> buf =
                new ListBuffer<>();
        for (List<JCExpression> tl = args; tl.nonEmpty(); tl = tl.tail) {
            JCExpression t = tl.head;
            if (!t.hasTag(ASSIGN)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                continue;
            }
            JCAssign assign = (JCAssign) t;
            if (!assign.lhs.hasTag(IDENT)) {
                log.error(t.pos(), "annotation.value.must.be.name.value");
                continue;
            }
            JCIdent left = (JCIdent) assign.lhs;
            Symbol method = rs.resolveQualifiedMethod(assign.rhs.pos(),
                    env,
                    a.type,
                    left.name,
                    List.nil(),
                    null);
            left.sym = method;
            left.type = method.type;
            if (method.owner != a.type.tsym)
                log.error(left.pos(), "no.annotation.member", left.name, a.type);
            Type result = method.type.getReturnType();
            Attribute value = enterAttributeValue(result, assign.rhs, env);
            if (!method.type.isErroneous())
                buf.append(new Pair<>((MethodSymbol) method, value));
            t.type = result;
        }
        if (typeAnnotation) {
            if (a.attribute == null || !(a.attribute instanceof Attribute.TypeCompound)) {

                Attribute.TypeCompound tc = new Attribute.TypeCompound(a.type, buf.toList(), new TypeAnnotationPosition());
                a.attribute = tc;
                return tc;
            } else {

                return a.attribute;
            }
        } else {
            Attribute.Compound ac = new Attribute.Compound(a.type, buf.toList());
            a.attribute = ac;
            return ac;
        }
    }

    Attribute enterAttributeValue(Type expected,
                                  JCExpression tree,
                                  Env<AttrContext> env) {


        try {
            expected.tsym.complete();
        } catch (CompletionFailure e) {
            log.error(tree.pos(), "cant.resolve", Kinds.kindName(e.sym), e.sym);
            expected = syms.errType;
        }
        if (expected.hasTag(ARRAY)) {
            if (!tree.hasTag(NEWARRAY)) {
                tree = make.at(tree.pos).
                        NewArray(null, List.nil(), List.of(tree));
            }
            JCNewArray na = (JCNewArray) tree;
            if (na.elemtype != null) {
                log.error(na.elemtype.pos(), "new.not.allowed.in.annotation");
            }
            ListBuffer<Attribute> buf = new ListBuffer<Attribute>();
            for (List<JCExpression> l = na.elems; l.nonEmpty(); l = l.tail) {
                buf.append(enterAttributeValue(types.elemtype(expected),
                        l.head,
                        env));
            }
            na.type = expected;
            return new Attribute.
                    Array(expected, buf.toArray(new Attribute[buf.length()]));
        }
        if (tree.hasTag(NEWARRAY)) {
            if (!expected.isErroneous())
                log.error(tree.pos(), "annotation.value.not.allowable.type");
            JCNewArray na = (JCNewArray) tree;
            if (na.elemtype != null) {
                log.error(na.elemtype.pos(), "new.not.allowed.in.annotation");
            }
            for (List<JCExpression> l = na.elems; l.nonEmpty(); l = l.tail) {
                enterAttributeValue(syms.errType,
                        l.head,
                        env);
            }
            return new Attribute.Error(syms.errType);
        }
        if ((expected.tsym.flags() & Flags.ANNOTATION) != 0) {
            if (tree.hasTag(ANNOTATION)) {
                return enterAnnotation((JCAnnotation) tree, expected, env);
            } else {
                log.error(tree.pos(), "annotation.value.must.be.annotation");
                expected = syms.errType;
            }
        }
        if (tree.hasTag(ANNOTATION)) {
            if (!expected.isErroneous())
                log.error(tree.pos(), "annotation.not.valid.for.type", expected);
            enterAnnotation((JCAnnotation) tree, syms.errType, env);
            return new Attribute.Error(((JCAnnotation) tree).annotationType.type);
        }
        if (expected.isPrimitive() || types.isSameType(expected, syms.stringType)) {
            Type result = attr.attribExpr(tree, env, expected);
            if (result.isErroneous())
                return new Attribute.Error(result.getOriginalType());
            if (result.constValue() == null) {
                log.error(tree.pos(), "attribute.value.must.be.constant");
                return new Attribute.Error(expected);
            }
            result = cfolder.coerce(result, expected);
            return new Attribute.Constant(expected, result.constValue());
        }
        if (expected.tsym == syms.classType.tsym) {
            Type result = attr.attribExpr(tree, env, expected);
            if (result.isErroneous()) {

                if (TreeInfo.name(tree) == names._class &&
                        ((JCFieldAccess) tree).selected.type.isErroneous()) {
                    Name n = (((JCFieldAccess) tree).selected).type.tsym.flatName();
                    return new Attribute.UnresolvedClass(expected,
                            types.createErrorType(n,
                                    syms.unknownSymbol, syms.classType));
                } else {
                    return new Attribute.Error(result.getOriginalType());
                }
            }


            if (TreeInfo.name(tree) != names._class) {
                log.error(tree.pos(), "annotation.value.must.be.class.literal");
                return new Attribute.Error(syms.errType);
            }
            return new Attribute.Class(types,
                    (((JCFieldAccess) tree).selected).type);
        }
        if (expected.hasTag(CLASS) &&
                (expected.tsym.flags() & Flags.ENUM) != 0) {
            Type result = attr.attribExpr(tree, env, expected);
            Symbol sym = TreeInfo.symbol(tree);
            if (sym == null ||
                    TreeInfo.nonstaticSelect(tree) ||
                    sym.kind != Kinds.VAR ||
                    (sym.flags() & Flags.ENUM) == 0) {
                log.error(tree.pos(), "enum.annotation.must.be.enum.constant");
                return new Attribute.Error(result.getOriginalType());
            }
            VarSymbol enumerator = (VarSymbol) sym;
            return new Attribute.Enum(expected, enumerator);
        }

        if (!expected.isErroneous())
            log.error(tree.pos(), "annotation.value.not.allowable.type");
        return new Attribute.Error(attr.attribExpr(tree, env, expected));
    }

    private <T extends Attribute.Compound> T processRepeatedAnnotations(List<T> annotations,
                                                                        AnnotateRepeatedContext<T> ctx,
                                                                        Symbol on) {
        T firstOccurrence = annotations.head;
        List<Attribute> repeated = List.nil();
        Type origAnnoType = null;
        Type arrayOfOrigAnnoType = null;
        Type targetContainerType = null;
        MethodSymbol containerValueSymbol = null;
        Assert.check(!annotations.isEmpty() &&
                !annotations.tail.isEmpty());
        int count = 0;
        for (List<T> al = annotations;
             !al.isEmpty();
             al = al.tail) {
            count++;

            Assert.check(count > 1 || !al.tail.isEmpty());
            T currentAnno = al.head;
            origAnnoType = currentAnno.type;
            if (arrayOfOrigAnnoType == null) {
                arrayOfOrigAnnoType = types.makeArrayType(origAnnoType);
            }

            boolean reportError = count > 1;
            Type currentContainerType = getContainingType(currentAnno, ctx.pos.get(currentAnno), reportError);
            if (currentContainerType == null) {
                continue;
            }


            Assert.check(targetContainerType == null || currentContainerType == targetContainerType);
            targetContainerType = currentContainerType;
            containerValueSymbol = validateContainer(targetContainerType, origAnnoType, ctx.pos.get(currentAnno));
            if (containerValueSymbol == null) {

                continue;
            }
            repeated = repeated.prepend(currentAnno);
        }
        if (!repeated.isEmpty()) {
            repeated = repeated.reverse();
            TreeMaker m = make.at(ctx.pos.get(firstOccurrence));
            Pair<MethodSymbol, Attribute> p =
                    new Pair<MethodSymbol, Attribute>(containerValueSymbol,
                            new Attribute.Array(arrayOfOrigAnnoType, repeated));
            if (ctx.isTypeCompound) {


                Attribute.TypeCompound at = new Attribute.TypeCompound(targetContainerType, List.of(p),
                        ((Attribute.TypeCompound) annotations.head).position);

                at.setSynthesized(true);
                @SuppressWarnings("unchecked")
                T x = (T) at;
                return x;
            } else {
                Attribute.Compound c = new Attribute.Compound(targetContainerType, List.of(p));
                JCAnnotation annoTree = m.Annotation(c);
                if (!chk.annotationApplicable(annoTree, on))
                    log.error(annoTree.pos(), "invalid.repeatable.annotation.incompatible.target", targetContainerType, origAnnoType);
                if (!chk.validateAnnotationDeferErrors(annoTree))
                    log.error(annoTree.pos(), "duplicate.annotation.invalid.repeated", origAnnoType);
                c = enterAnnotation(annoTree, targetContainerType, ctx.env);
                c.setSynthesized(true);
                @SuppressWarnings("unchecked")
                T x = (T) c;
                return x;
            }
        } else {
            return null;
        }
    }

    private Type getContainingType(Attribute.Compound currentAnno,
                                   DiagnosticPosition pos,
                                   boolean reportError) {
        Type origAnnoType = currentAnno.type;
        TypeSymbol origAnnoDecl = origAnnoType.tsym;


        Attribute.Compound ca = origAnnoDecl.attribute(syms.repeatableType.tsym);
        if (ca == null) {
            if (reportError)
                log.error(pos, "duplicate.annotation.missing.container", origAnnoType, syms.repeatableType);
            return null;
        }
        return filterSame(extractContainingType(ca, pos, origAnnoDecl),
                origAnnoType);
    }

    private Type filterSame(Type t, Type s) {
        if (t == null || s == null) {
            return t;
        }
        return types.isSameType(t, s) ? null : t;
    }

    private Type extractContainingType(Attribute.Compound ca,
                                       DiagnosticPosition pos,
                                       TypeSymbol annoDecl) {


        if (ca.values.isEmpty()) {
            log.error(pos, "invalid.repeatable.annotation", annoDecl);
            return null;
        }
        Pair<MethodSymbol, Attribute> p = ca.values.head;
        Name name = p.fst.name;
        if (name != names.value) {
            log.error(pos, "invalid.repeatable.annotation", annoDecl);
            return null;
        }
        if (!(p.snd instanceof Attribute.Class)) {
            log.error(pos, "invalid.repeatable.annotation", annoDecl);
            return null;
        }
        return ((Attribute.Class) p.snd).getValue();
    }

    private MethodSymbol validateContainer(Type targetContainerType,
                                           Type originalAnnoType,
                                           DiagnosticPosition pos) {
        MethodSymbol containerValueSymbol = null;
        boolean fatalError = false;

        Scope scope = targetContainerType.tsym.members();
        int nr_value_elems = 0;
        boolean error = false;
        for (Symbol elm : scope.getElementsByName(names.value)) {
            nr_value_elems++;
            if (nr_value_elems == 1 &&
                    elm.kind == Kinds.MTH) {
                containerValueSymbol = (MethodSymbol) elm;
            } else {
                error = true;
            }
        }
        if (error) {
            log.error(pos,
                    "invalid.repeatable.annotation.multiple.values",
                    targetContainerType,
                    nr_value_elems);
            return null;
        } else if (nr_value_elems == 0) {
            log.error(pos,
                    "invalid.repeatable.annotation.no.value",
                    targetContainerType);
            return null;
        }


        if (containerValueSymbol.kind != Kinds.MTH) {
            log.error(pos,
                    "invalid.repeatable.annotation.invalid.value",
                    targetContainerType);
            fatalError = true;
        }


        Type valueRetType = containerValueSymbol.type.getReturnType();
        Type expectedType = types.makeArrayType(originalAnnoType);
        if (!(types.isArray(valueRetType) &&
                types.isSameType(expectedType, valueRetType))) {
            log.error(pos,
                    "invalid.repeatable.annotation.value.return",
                    targetContainerType,
                    valueRetType,
                    expectedType);
            fatalError = true;
        }
        if (error) {
            fatalError = true;
        }


        return fatalError ? null : containerValueSymbol;
    }

    public interface Worker {
        void run();

        String toString();
    }

    public class AnnotateRepeatedContext<T extends Attribute.Compound> {
        public final Env<AttrContext> env;
        public final Map<TypeSymbol, ListBuffer<T>> annotated;
        public final Map<T, DiagnosticPosition> pos;
        public final Log log;
        public final boolean isTypeCompound;

        public AnnotateRepeatedContext(Env<AttrContext> env,
                                       Map<TypeSymbol, ListBuffer<T>> annotated,
                                       Map<T, DiagnosticPosition> pos,
                                       Log log,
                                       boolean isTypeCompound) {
            Assert.checkNonNull(env);
            Assert.checkNonNull(annotated);
            Assert.checkNonNull(pos);
            Assert.checkNonNull(log);
            this.env = env;
            this.annotated = annotated;
            this.pos = pos;
            this.log = log;
            this.isTypeCompound = isTypeCompound;
        }

        public T processRepeatedAnnotations(List<T> repeatingAnnotations, Symbol sym) {
            return Annotate.this.processRepeatedAnnotations(repeatingAnnotations, this, sym);
        }

        public void annotateRepeated(Worker a) {
            Annotate.this.repeated(a);
        }
    }
}
