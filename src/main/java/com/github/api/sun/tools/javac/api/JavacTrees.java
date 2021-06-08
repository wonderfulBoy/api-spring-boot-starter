package com.github.api.sun.tools.javac.api;

import com.github.api.sun.source.doctree.DocCommentTree;
import com.github.api.sun.source.doctree.DocTree;
import com.github.api.sun.source.tree.CatchTree;
import com.github.api.sun.source.tree.CompilationUnitTree;
import com.github.api.sun.source.tree.Scope;
import com.github.api.sun.source.tree.Tree;
import com.github.api.sun.source.util.*;
import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.*;
import com.github.api.sun.tools.javac.code.Type.ArrayType;
import com.github.api.sun.tools.javac.code.Type.ClassType;
import com.github.api.sun.tools.javac.code.Type.ErrorType;
import com.github.api.sun.tools.javac.code.Type.UnionClassType;
import com.github.api.sun.tools.javac.code.Types.TypeRelation;
import com.github.api.sun.tools.javac.comp.*;
import com.github.api.sun.tools.javac.model.JavacElements;
import com.github.api.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.github.api.sun.tools.javac.tree.*;
import com.github.api.sun.tools.javac.tree.DCTree.*;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.util.Name;
import com.github.api.sun.tools.javac.util.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.github.api.sun.tools.javac.code.TypeTag.ARRAY;
import static com.github.api.sun.tools.javac.code.TypeTag.CLASS;

public class JavacTrees extends DocTrees {
    private Resolve resolve;
    private Enter enter;
    private Log log;
    private MemberEnter memberEnter;
    private Attr attr;
    private TreeMaker treeMaker;
    private JavacElements elements;
    private JavacTaskImpl javacTaskImpl;
    private Names names;
    private Types types;
    TypeRelation fuzzyMatcher = new TypeRelation() {
        @Override
        public Boolean visitType(Type t, Type s) {
            if (t == s)
                return true;
            if (s.isPartial())
                return visit(s, t);
            switch (t.getTag()) {
                case BYTE:
                case CHAR:
                case SHORT:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BOOLEAN:
                case VOID:
                case BOT:
                case NONE:
                    return t.hasTag(s.getTag());
                default:
                    throw new AssertionError("fuzzyMatcher " + t.getTag());
            }
        }

        @Override
        public Boolean visitArrayType(ArrayType t, Type s) {
            if (t == s)
                return true;
            if (s.isPartial())
                return visit(s, t);
            return s.hasTag(ARRAY)
                    && visit(t.elemtype, types.elemtype(s));
        }

        @Override
        public Boolean visitClassType(ClassType t, Type s) {
            if (t == s)
                return true;
            if (s.isPartial())
                return visit(s, t);
            return t.tsym == s.tsym;
        }

        @Override
        public Boolean visitErrorType(ErrorType t, Type s) {
            return s.hasTag(CLASS)
                    && t.tsym.name == ((ClassType) s).tsym.name;
        }
    };

    protected JavacTrees(Context context) {
        context.put(JavacTrees.class, this);
        init(context);
    }

    public static JavacTrees instance(JavaCompiler.CompilationTask task) {
        if (!(task instanceof BasicJavacTask))
            throw new IllegalArgumentException();
        return instance(((BasicJavacTask) task).getContext());
    }

    public static JavacTrees instance(ProcessingEnvironment env) {
        if (!(env instanceof JavacProcessingEnvironment))
            throw new IllegalArgumentException();
        return instance(((JavacProcessingEnvironment) env).getContext());
    }

    public static JavacTrees instance(Context context) {
        JavacTrees instance = context.get(JavacTrees.class);
        if (instance == null)
            instance = new JavacTrees(context);
        return instance;
    }

    public void updateContext(Context context) {
        init(context);
    }

    private void init(Context context) {
        attr = Attr.instance(context);
        enter = Enter.instance(context);
        elements = JavacElements.instance(context);
        log = Log.instance(context);
        resolve = Resolve.instance(context);
        treeMaker = TreeMaker.instance(context);
        memberEnter = MemberEnter.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        JavacTask t = context.get(JavacTask.class);
        if (t instanceof JavacTaskImpl)
            javacTaskImpl = (JavacTaskImpl) t;
    }

    public DocSourcePositions getSourcePositions() {
        return new DocSourcePositions() {
            public long getStartPosition(CompilationUnitTree file, Tree tree) {
                return TreeInfo.getStartPos((JCTree) tree);
            }

            public long getEndPosition(CompilationUnitTree file, Tree tree) {
                EndPosTable endPosTable = ((JCCompilationUnit) file).endPositions;
                return TreeInfo.getEndPos((JCTree) tree, endPosTable);
            }

            public long getStartPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree) {
                return ((DCTree) tree).getSourcePosition((DCDocComment) comment);
            }

            @SuppressWarnings("fallthrough")
            public long getEndPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree) {
                DCDocComment dcComment = (DCDocComment) comment;
                if (tree instanceof DCEndPosTree) {
                    int endPos = ((DCEndPosTree) tree).getEndPos(dcComment);
                    if (endPos != Position.NOPOS) {
                        return endPos;
                    }
                }
                int correction = 0;
                switch (tree.getKind()) {
                    case TEXT:
                        DCText text = (DCText) tree;
                        return dcComment.comment.getSourcePos(text.pos + text.text.length());
                    case ERRONEOUS:
                        DCErroneous err = (DCErroneous) tree;
                        return dcComment.comment.getSourcePos(err.pos + err.body.length());
                    case IDENTIFIER:
                        DCIdentifier ident = (DCIdentifier) tree;
                        return dcComment.comment.getSourcePos(ident.pos + (ident.name != names.error ? ident.name.length() : 0));
                    case PARAM:
                        DCParam param = (DCParam) tree;
                        if (param.isTypeParameter && param.getDescription().isEmpty()) {
                            correction = 1;
                        }
                    case AUTHOR:
                    case DEPRECATED:
                    case RETURN:
                    case SEE:
                    case SERIAL:
                    case SERIAL_DATA:
                    case SERIAL_FIELD:
                    case SINCE:
                    case THROWS:
                    case UNKNOWN_BLOCK_TAG:
                    case VERSION: {
                        DocTree last = getLastChild(tree);
                        if (last != null) {
                            return getEndPosition(file, comment, last) + correction;
                        }
                        DCBlockTag block = (DCBlockTag) tree;
                        return dcComment.comment.getSourcePos(block.pos + block.getTagName().length() + 1);
                    }
                    default:
                        DocTree last = getLastChild(tree);
                        if (last != null) {
                            return getEndPosition(file, comment, last);
                        }
                        break;
                }
                return Position.NOPOS;
            }
        };
    }

    private DocTree getLastChild(DocTree tree) {
        final DocTree[] last = new DocTree[]{null};
        tree.accept(new DocTreeScanner<Void, Void>() {
            @Override
            public Void scan(DocTree node, Void p) {
                if (node != null) last[0] = node;
                return null;
            }
        }, null);
        return last[0];
    }

    public JCClassDecl getTree(TypeElement element) {
        return (JCClassDecl) getTree((Element) element);
    }

    public JCMethodDecl getTree(ExecutableElement method) {
        return (JCMethodDecl) getTree((Element) method);
    }

    public JCTree getTree(Element element) {
        Symbol symbol = (Symbol) element;
        TypeSymbol enclosing = symbol.enclClass();
        Env<AttrContext> env = enter.getEnv(enclosing);
        if (env == null)
            return null;
        JCClassDecl classNode = env.enclClass;
        if (classNode != null) {
            if (TreeInfo.symbolFor(classNode) == element)
                return classNode;
            for (JCTree node : classNode.getMembers())
                if (TreeInfo.symbolFor(node) == element)
                    return node;
        }
        return null;
    }

    public JCTree getTree(Element e, AnnotationMirror a) {
        return getTree(e, a, null);
    }

    public JCTree getTree(Element e, AnnotationMirror a, AnnotationValue v) {
        Pair<JCTree, JCCompilationUnit> treeTopLevel = elements.getTreeAndTopLevel(e, a, v);
        if (treeTopLevel == null)
            return null;
        return treeTopLevel.fst;
    }

    public TreePath getPath(CompilationUnitTree unit, Tree node) {
        return TreePath.getPath(unit, node);
    }

    public TreePath getPath(Element e) {
        return getPath(e, null, null);
    }

    public TreePath getPath(Element e, AnnotationMirror a) {
        return getPath(e, a, null);
    }

    public TreePath getPath(Element e, AnnotationMirror a, AnnotationValue v) {
        final Pair<JCTree, JCCompilationUnit> treeTopLevel = elements.getTreeAndTopLevel(e, a, v);
        if (treeTopLevel == null)
            return null;
        return TreePath.getPath(treeTopLevel.snd, treeTopLevel.fst);
    }

    public Symbol getElement(TreePath path) {
        JCTree tree = (JCTree) path.getLeaf();
        Symbol sym = TreeInfo.symbolFor(tree);
        if (sym == null) {
            if (TreeInfo.isDeclaration(tree)) {
                for (TreePath p = path; p != null; p = p.getParentPath()) {
                    JCTree t = (JCTree) p.getLeaf();
                    if (t.hasTag(Tag.CLASSDEF)) {
                        JCClassDecl ct = (JCClassDecl) t;
                        if (ct.sym != null) {
                            if ((ct.sym.flags_field & Flags.UNATTRIBUTED) != 0) {
                                attr.attribClass(ct.pos(), ct.sym);
                                sym = TreeInfo.symbolFor(tree);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return sym;
    }

    @Override
    public Element getElement(DocTreePath path) {
        DocTree forTree = path.getLeaf();
        if (forTree instanceof DCReference)
            return attributeDocReference(path.getTreePath(), ((DCReference) forTree));
        if (forTree instanceof DCIdentifier) {
            if (path.getParentPath().getLeaf() instanceof DCParam) {
                return attributeParamIdentifier(path.getTreePath(), (DCParam) path.getParentPath().getLeaf());
            }
        }
        return null;
    }

    private Symbol attributeDocReference(TreePath path, DCReference ref) {
        Env<AttrContext> env = getAttrContext(path);
        Log.DeferredDiagnosticHandler deferredDiagnosticHandler =
                new Log.DeferredDiagnosticHandler(log);
        try {
            final TypeSymbol tsym;
            final Name memberName;
            if (ref.qualifierExpression == null) {
                tsym = env.enclClass.sym;
                memberName = ref.memberName;
            } else {
                Type t = attr.attribType(ref.qualifierExpression, env);
                if (t.isErroneous()) {
                    if (ref.memberName == null) {
                        PackageSymbol pck = elements.getPackageElement(ref.qualifierExpression.toString());
                        if (pck != null) {
                            return pck;
                        } else if (ref.qualifierExpression.hasTag(Tag.IDENT)) {
                            tsym = env.enclClass.sym;
                            memberName = ((JCIdent) ref.qualifierExpression).name;
                        } else
                            return null;
                    } else {
                        return null;
                    }
                } else {
                    tsym = t.tsym;
                    memberName = ref.memberName;
                }
            }
            if (memberName == null)
                return tsym;
            final List<Type> paramTypes;
            if (ref.paramTypes == null)
                paramTypes = null;
            else {
                ListBuffer<Type> lb = new ListBuffer<Type>();
                for (List<JCTree> l = ref.paramTypes; l.nonEmpty(); l = l.tail) {
                    JCTree tree = l.head;
                    Type t = attr.attribType(tree, env);
                    lb.add(t);
                }
                paramTypes = lb.toList();
            }
            ClassSymbol sym = (ClassSymbol) types.upperBound(tsym.type).tsym;
            Symbol msym = (memberName == sym.name)
                    ? findConstructor(sym, paramTypes)
                    : findMethod(sym, memberName, paramTypes);
            if (paramTypes != null) {
                return msym;
            }
            VarSymbol vsym = (ref.paramTypes != null) ? null : findField(sym, memberName);
            if (vsym != null &&
                    (msym == null ||
                            types.isSubtypeUnchecked(vsym.enclClass().asType(), msym.enclClass().asType()))) {
                return vsym;
            } else {
                return msym;
            }
        } catch (Abort e) {
            return null;
        } finally {
            log.popDiagnosticHandler(deferredDiagnosticHandler);
        }
    }

    private Symbol attributeParamIdentifier(TreePath path, DCParam ptag) {
        Symbol javadocSymbol = getElement(path);
        if (javadocSymbol == null)
            return null;
        ElementKind kind = javadocSymbol.getKind();
        List<? extends Symbol> params = List.nil();
        if (kind == ElementKind.METHOD || kind == ElementKind.CONSTRUCTOR) {
            MethodSymbol ee = (MethodSymbol) javadocSymbol;
            params = ptag.isTypeParameter()
                    ? ee.getTypeParameters()
                    : ee.getParameters();
        } else if (kind.isClass() || kind.isInterface()) {
            ClassSymbol te = (ClassSymbol) javadocSymbol;
            params = te.getTypeParameters();
        }
        for (Symbol param : params) {
            if (param.getSimpleName() == ptag.getName().getName()) {
                return param;
            }
        }
        return null;
    }

    private VarSymbol findField(ClassSymbol tsym, Name fieldName) {
        return searchField(tsym, fieldName, new HashSet<ClassSymbol>());
    }

    private VarSymbol searchField(ClassSymbol tsym, Name fieldName, Set<ClassSymbol> searched) {
        if (searched.contains(tsym)) {
            return null;
        }
        searched.add(tsym);
        for (com.github.api.sun.tools.javac.code.Scope.Entry e = tsym.members().lookup(fieldName);
             e.scope != null; e = e.next()) {
            if (e.sym.kind == Kinds.VAR) {
                return (VarSymbol) e.sym;
            }
        }
        ClassSymbol encl = tsym.owner.enclClass();
        if (encl != null) {
            VarSymbol vsym = searchField(encl, fieldName, searched);
            if (vsym != null) {
                return vsym;
            }
        }
        Type superclass = tsym.getSuperclass();
        if (superclass.tsym != null) {
            VarSymbol vsym = searchField((ClassSymbol) superclass.tsym, fieldName, searched);
            if (vsym != null) {
                return vsym;
            }
        }
        List<Type> intfs = tsym.getInterfaces();
        for (List<Type> l = intfs; l.nonEmpty(); l = l.tail) {
            Type intf = l.head;
            if (intf.isErroneous()) continue;
            VarSymbol vsym = searchField((ClassSymbol) intf.tsym, fieldName, searched);
            if (vsym != null) {
                return vsym;
            }
        }
        return null;
    }

    MethodSymbol findConstructor(ClassSymbol tsym, List<Type> paramTypes) {
        for (com.github.api.sun.tools.javac.code.Scope.Entry e = tsym.members().lookup(names.init);
             e.scope != null; e = e.next()) {
            if (e.sym.kind == Kinds.MTH) {
                if (hasParameterTypes((MethodSymbol) e.sym, paramTypes)) {
                    return (MethodSymbol) e.sym;
                }
            }
        }
        return null;
    }

    private MethodSymbol findMethod(ClassSymbol tsym, Name methodName, List<Type> paramTypes) {
        return searchMethod(tsym, methodName, paramTypes, new HashSet<ClassSymbol>());
    }

    private MethodSymbol searchMethod(ClassSymbol tsym, Name methodName,
                                      List<Type> paramTypes, Set<ClassSymbol> searched) {
        if (methodName == names.init)
            return null;
        if (searched.contains(tsym))
            return null;
        searched.add(tsym);
        com.github.api.sun.tools.javac.code.Scope.Entry e = tsym.members().lookup(methodName);
        if (paramTypes == null) {
            MethodSymbol lastFound = null;
            for (; e.scope != null; e = e.next()) {
                if (e.sym.kind == Kinds.MTH) {
                    if (e.sym.name == methodName) {
                        lastFound = (MethodSymbol) e.sym;
                    }
                }
            }
            if (lastFound != null) {
                return lastFound;
            }
        } else {
            for (; e.scope != null; e = e.next()) {
                if (e.sym != null &&
                        e.sym.kind == Kinds.MTH) {
                    if (hasParameterTypes((MethodSymbol) e.sym, paramTypes)) {
                        return (MethodSymbol) e.sym;
                    }
                }
            }
        }
        Type superclass = tsym.getSuperclass();
        if (superclass.tsym != null) {
            MethodSymbol msym = searchMethod((ClassSymbol) superclass.tsym, methodName, paramTypes, searched);
            if (msym != null) {
                return msym;
            }
        }
        List<Type> intfs = tsym.getInterfaces();
        for (List<Type> l = intfs; l.nonEmpty(); l = l.tail) {
            Type intf = l.head;
            if (intf.isErroneous()) continue;
            MethodSymbol msym = searchMethod((ClassSymbol) intf.tsym, methodName, paramTypes, searched);
            if (msym != null) {
                return msym;
            }
        }
        ClassSymbol encl = tsym.owner.enclClass();
        if (encl != null) {
            MethodSymbol msym = searchMethod(encl, methodName, paramTypes, searched);
            return msym;
        }
        return null;
    }

    private boolean hasParameterTypes(MethodSymbol method, List<Type> paramTypes) {
        if (paramTypes == null)
            return true;
        if (method.params().size() != paramTypes.size())
            return false;
        List<Type> methodParamTypes = types.erasureRecursive(method.asType()).getParameterTypes();
        return (Type.isErroneous(paramTypes))
                ? fuzzyMatch(paramTypes, methodParamTypes)
                : types.isSameTypes(paramTypes, methodParamTypes);
    }

    boolean fuzzyMatch(List<Type> paramTypes, List<Type> methodParamTypes) {
        List<Type> l1 = paramTypes;
        List<Type> l2 = methodParamTypes;
        while (l1.nonEmpty()) {
            if (!fuzzyMatch(l1.head, l2.head))
                return false;
            l1 = l1.tail;
            l2 = l2.tail;
        }
        return true;
    }

    boolean fuzzyMatch(Type paramType, Type methodParamType) {
        Boolean b = fuzzyMatcher.visit(paramType, methodParamType);
        return (b == Boolean.TRUE);
    }

    public TypeMirror getTypeMirror(TreePath path) {
        Tree t = path.getLeaf();
        return ((JCTree) t).type;
    }

    public JavacScope getScope(TreePath path) {
        return new JavacScope(getAttrContext(path));
    }

    public String getDocComment(TreePath path) {
        CompilationUnitTree t = path.getCompilationUnit();
        Tree leaf = path.getLeaf();
        if (t instanceof JCCompilationUnit && leaf instanceof JCTree) {
            JCCompilationUnit cu = (JCCompilationUnit) t;
            if (cu.docComments != null) {
                return cu.docComments.getCommentText((JCTree) leaf);
            }
        }
        return null;
    }

    public DocCommentTree getDocCommentTree(TreePath path) {
        CompilationUnitTree t = path.getCompilationUnit();
        Tree leaf = path.getLeaf();
        if (t instanceof JCCompilationUnit && leaf instanceof JCTree) {
            JCCompilationUnit cu = (JCCompilationUnit) t;
            if (cu.docComments != null) {
                return cu.docComments.getCommentTree((JCTree) leaf);
            }
        }
        return null;
    }

    public boolean isAccessible(Scope scope, TypeElement type) {
        if (scope instanceof JavacScope && type instanceof ClassSymbol) {
            Env<AttrContext> env = ((JavacScope) scope).env;
            return resolve.isAccessible(env, (ClassSymbol) type, true);
        } else
            return false;
    }

    public boolean isAccessible(Scope scope, Element member, DeclaredType type) {
        if (scope instanceof JavacScope
                && member instanceof Symbol
                && type instanceof Type) {
            Env<AttrContext> env = ((JavacScope) scope).env;
            return resolve.isAccessible(env, (Type) type, (Symbol) member, true);
        } else
            return false;
    }

    private Env<AttrContext> getAttrContext(TreePath path) {
        if (!(path.getLeaf() instanceof JCTree))
            throw new IllegalArgumentException();
        if (javacTaskImpl != null) {
            try {
                javacTaskImpl.enter(null);
            } catch (IOException e) {
                throw new Error("unexpected error while entering symbols: " + e);
            }
        }
        JCCompilationUnit unit = (JCCompilationUnit) path.getCompilationUnit();
        Copier copier = createCopier(treeMaker.forToplevel(unit));
        Env<AttrContext> env = null;
        JCMethodDecl method = null;
        JCVariableDecl field = null;
        List<Tree> l = List.nil();
        TreePath p = path;
        while (p != null) {
            l = l.prepend(p.getLeaf());
            p = p.getParentPath();
        }
        for (; l.nonEmpty(); l = l.tail) {
            Tree tree = l.head;
            switch (tree.getKind()) {
                case COMPILATION_UNIT:
                    env = enter.getTopLevelEnv((JCCompilationUnit) tree);
                    break;
                case ANNOTATION_TYPE:
                case CLASS:
                case ENUM:
                case INTERFACE:
                    env = enter.getClassEnv(((JCClassDecl) tree).sym);
                    break;
                case METHOD:
                    method = (JCMethodDecl) tree;
                    env = memberEnter.getMethodEnv(method, env);
                    break;
                case VARIABLE:
                    field = (JCVariableDecl) tree;
                    break;
                case BLOCK: {
                    if (method != null) {
                        try {
                            Assert.check(method.body == tree);
                            method.body = copier.copy((JCBlock) tree, (JCTree) path.getLeaf());
                            env = attribStatToTree(method.body, env, copier.leafCopy);
                        } finally {
                            method.body = (JCBlock) tree;
                        }
                    } else {
                        JCBlock body = copier.copy((JCBlock) tree, (JCTree) path.getLeaf());
                        env = attribStatToTree(body, env, copier.leafCopy);
                    }
                    return env;
                }
                default:
                    if (field != null && field.getInitializer() == tree) {
                        env = memberEnter.getInitEnv(field, env);
                        JCExpression expr = copier.copy((JCExpression) tree, (JCTree) path.getLeaf());
                        env = attribExprToTree(expr, env, copier.leafCopy);
                        return env;
                    }
            }
        }
        return (field != null) ? memberEnter.getInitEnv(field, env) : env;
    }

    private Env<AttrContext> attribStatToTree(JCTree stat, Env<AttrContext> env, JCTree tree) {
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            return attr.attribStatToTree(stat, env, tree);
        } finally {
            log.useSource(prev);
        }
    }

    private Env<AttrContext> attribExprToTree(JCExpression expr, Env<AttrContext> env, JCTree tree) {
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            return attr.attribExprToTree(expr, env, tree);
        } finally {
            log.useSource(prev);
        }
    }

    protected Copier createCopier(TreeMaker maker) {
        return new Copier(maker);
    }

    public TypeMirror getOriginalType(javax.lang.model.type.ErrorType errorType) {
        if (errorType instanceof ErrorType) {
            return ((ErrorType) errorType).getOriginalType();
        }
        return Type.noType;
    }

    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                             Tree t,
                             CompilationUnitTree root) {
        printMessage(kind, msg, ((JCTree) t).pos(), root);
    }

    public void printMessage(Diagnostic.Kind kind, CharSequence msg,
                             DocTree t,
                             DocCommentTree c,
                             CompilationUnitTree root) {
        printMessage(kind, msg, ((DCTree) t).pos((DCDocComment) c), root);
    }

    private void printMessage(Diagnostic.Kind kind, CharSequence msg,
                              JCDiagnostic.DiagnosticPosition pos,
                              CompilationUnitTree root) {
        JavaFileObject oldSource = null;
        JavaFileObject newSource = null;
        newSource = root.getSourceFile();
        if (newSource == null) {
            pos = null;
        } else {
            oldSource = log.useSource(newSource);
        }
        try {
            switch (kind) {
                case ERROR:
                    boolean prev = log.multipleErrors;
                    try {
                        log.error(pos, "proc.messager", msg.toString());
                    } finally {
                        log.multipleErrors = prev;
                    }
                    break;
                case WARNING:
                    log.warning(pos, "proc.messager", msg.toString());
                    break;
                case MANDATORY_WARNING:
                    log.mandatoryWarning(pos, "proc.messager", msg.toString());
                    break;
                default:
                    log.note(pos, "proc.messager", msg.toString());
            }
        } finally {
            if (oldSource != null)
                log.useSource(oldSource);
        }
    }

    @Override
    public TypeMirror getLub(CatchTree tree) {
        JCCatch ct = (JCCatch) tree;
        JCVariableDecl v = ct.param;
        if (v.type != null && v.type.getKind() == TypeKind.UNION) {
            UnionClassType ut = (UnionClassType) v.type;
            return ut.getLub();
        } else {
            return v.type;
        }
    }

    protected static class Copier extends TreeCopier<JCTree> {
        JCTree leafCopy = null;

        protected Copier(TreeMaker M) {
            super(M);
        }

        @Override
        public <T extends JCTree> T copy(T t, JCTree leaf) {
            T t2 = super.copy(t, leaf);
            if (t == leaf)
                leafCopy = t2;
            return t2;
        }
    }
}
