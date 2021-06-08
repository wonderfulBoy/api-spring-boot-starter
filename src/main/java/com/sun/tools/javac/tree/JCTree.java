package com.sun.tools.javac.tree;

import com.sun.source.tree.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.ImportScope;
import com.sun.tools.javac.code.Scope.StarImportScope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

import static com.sun.tools.javac.tree.JCTree.Tag.*;

public abstract class JCTree implements Tree, Cloneable, DiagnosticPosition {
    public int pos;
    public Type type;

    public abstract Tag getTag();

    public boolean hasTag(Tag tag) {
        return tag == getTag();
    }

    @Override
    public String toString() {
        StringWriter s = new StringWriter();
        try {
            new Pretty(s, false).printExpr(this);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return s.toString();
    }

    public JCTree setPos(int pos) {
        this.pos = pos;
        return this;
    }

    public JCTree setType(Type type) {
        this.type = type;
        return this;
    }

    public abstract void accept(Visitor v);

    public abstract <R, D> R accept(TreeVisitor<R, D> v, D d);

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public DiagnosticPosition pos() {
        return this;
    }

    public JCTree getTree() {
        return this;
    }

    public int getStartPosition() {
        return TreeInfo.getStartPos(this);
    }

    public int getPreferredPosition() {
        return pos;
    }

    public int getEndPosition(EndPosTable endPosTable) {
        return TreeInfo.getEndPos(this, endPosTable);
    }

    public enum Tag {
        NO_TAG,
        TOPLEVEL,
        IMPORT,
        CLASSDEF,
        METHODDEF,
        VARDEF,
        SKIP,
        BLOCK,
        DOLOOP,
        WHILELOOP,
        FORLOOP,
        FOREACHLOOP,
        LABELLED,
        SWITCH,
        CASE,
        SYNCHRONIZED,
        TRY,
        CATCH,
        CONDEXPR,
        IF,
        EXEC,
        BREAK,
        CONTINUE,
        RETURN,
        THROW,
        ASSERT,
        APPLY,
        NEWCLASS,
        NEWARRAY,
        LAMBDA,
        PARENS,
        ASSIGN,
        TYPECAST,
        TYPETEST,
        INDEXED,
        SELECT,
        REFERENCE,
        IDENT,
        LITERAL,
        TYPEIDENT,
        TYPEARRAY,
        TYPEAPPLY,
        TYPEUNION,
        TYPEINTERSECTION,
        TYPEPARAMETER,
        WILDCARD,
        TYPEBOUNDKIND,
        ANNOTATION,
        TYPE_ANNOTATION,
        MODIFIERS,
        ANNOTATED_TYPE,
        ERRONEOUS,
        POS,
        NEG,
        NOT,
        COMPL,
        PREINC,
        PREDEC,
        POSTINC,
        POSTDEC,
        NULLCHK,
        OR,
        AND,
        BITOR,
        BITXOR,
        BITAND,
        EQ,
        NE,
        LT,
        GT,
        LE,
        GE,
        SL,
        SR,
        USR,
        PLUS,
        MINUS,
        MUL,
        DIV,
        MOD,
        BITOR_ASG(BITOR),
        BITXOR_ASG(BITXOR),
        BITAND_ASG(BITAND),
        SL_ASG(SL),
        SR_ASG(SR),
        USR_ASG(USR),
        PLUS_ASG(PLUS),
        MINUS_ASG(MINUS),
        MUL_ASG(MUL),
        DIV_ASG(DIV),
        MOD_ASG(MOD),
        LETEXPR;
        private static final int numberOfOperators = MOD.ordinal() - POS.ordinal() + 1;
        private final Tag noAssignTag;

        Tag(Tag noAssignTag) {
            this.noAssignTag = noAssignTag;
        }

        Tag() {
            this(null);
        }

        public static int getNumberOfOperators() {
            return numberOfOperators;
        }

        public Tag noAssignOp() {
            if (noAssignTag != null)
                return noAssignTag;
            throw new AssertionError("noAssignOp() method is not available for non assignment tags");
        }

        public boolean isPostUnaryOp() {
            return (this == POSTINC || this == POSTDEC);
        }

        public boolean isIncOrDecUnaryOp() {
            return (this == PREINC || this == PREDEC || this == POSTINC || this == POSTDEC);
        }

        public boolean isAssignop() {
            return noAssignTag != null;
        }

        public int operatorIndex() {
            return (this.ordinal() - POS.ordinal());
        }
    }

    public interface Factory {
        JCCompilationUnit TopLevel(List<JCAnnotation> packageAnnotations,
                                   JCExpression pid,
                                   List<JCTree> defs);

        JCImport Import(JCTree qualid, boolean staticImport);

        JCClassDecl ClassDef(JCModifiers mods,
                             Name name,
                             List<JCTypeParameter> typarams,
                             JCExpression extending,
                             List<JCExpression> implementing,
                             List<JCTree> defs);

        JCMethodDecl MethodDef(JCModifiers mods,
                               Name name,
                               JCExpression restype,
                               List<JCTypeParameter> typarams,
                               JCVariableDecl recvparam,
                               List<JCVariableDecl> params,
                               List<JCExpression> thrown,
                               JCBlock body,
                               JCExpression defaultValue);

        JCVariableDecl VarDef(JCModifiers mods,
                              Name name,
                              JCExpression vartype,
                              JCExpression init);

        JCSkip Skip();

        JCBlock Block(long flags, List<JCStatement> stats);

        JCDoWhileLoop DoLoop(JCStatement body, JCExpression cond);

        JCWhileLoop WhileLoop(JCExpression cond, JCStatement body);

        JCForLoop ForLoop(List<JCStatement> init,
                          JCExpression cond,
                          List<JCExpressionStatement> step,
                          JCStatement body);

        JCEnhancedForLoop ForeachLoop(JCVariableDecl var, JCExpression expr, JCStatement body);

        JCLabeledStatement Labelled(Name label, JCStatement body);

        JCSwitch Switch(JCExpression selector, List<JCCase> cases);

        JCCase Case(JCExpression pat, List<JCStatement> stats);

        JCSynchronized Synchronized(JCExpression lock, JCBlock body);

        JCTry Try(JCBlock body, List<JCCatch> catchers, JCBlock finalizer);

        JCTry Try(List<JCTree> resources,
                  JCBlock body,
                  List<JCCatch> catchers,
                  JCBlock finalizer);

        JCCatch Catch(JCVariableDecl param, JCBlock body);

        JCConditional Conditional(JCExpression cond,
                                  JCExpression thenpart,
                                  JCExpression elsepart);

        JCIf If(JCExpression cond, JCStatement thenpart, JCStatement elsepart);

        JCExpressionStatement Exec(JCExpression expr);

        JCBreak Break(Name label);

        JCContinue Continue(Name label);

        JCReturn Return(JCExpression expr);

        JCThrow Throw(JCExpression expr);

        JCAssert Assert(JCExpression cond, JCExpression detail);

        JCMethodInvocation Apply(List<JCExpression> typeargs,
                                 JCExpression fn,
                                 List<JCExpression> args);

        JCNewClass NewClass(JCExpression encl,
                            List<JCExpression> typeargs,
                            JCExpression clazz,
                            List<JCExpression> args,
                            JCClassDecl def);

        JCNewArray NewArray(JCExpression elemtype,
                            List<JCExpression> dims,
                            List<JCExpression> elems);

        JCParens Parens(JCExpression expr);

        JCAssign Assign(JCExpression lhs, JCExpression rhs);

        JCAssignOp Assignop(Tag opcode, JCTree lhs, JCTree rhs);

        JCUnary Unary(Tag opcode, JCExpression arg);

        JCBinary Binary(Tag opcode, JCExpression lhs, JCExpression rhs);

        JCTypeCast TypeCast(JCTree expr, JCExpression type);

        JCInstanceOf TypeTest(JCExpression expr, JCTree clazz);

        JCArrayAccess Indexed(JCExpression indexed, JCExpression index);

        JCFieldAccess Select(JCExpression selected, Name selector);

        JCIdent Ident(Name idname);

        JCLiteral Literal(TypeTag tag, Object value);

        JCPrimitiveTypeTree TypeIdent(TypeTag typetag);

        JCArrayTypeTree TypeArray(JCExpression elemtype);

        JCTypeApply TypeApply(JCExpression clazz, List<JCExpression> arguments);

        JCTypeParameter TypeParameter(Name name, List<JCExpression> bounds);

        JCWildcard Wildcard(TypeBoundKind kind, JCTree type);

        TypeBoundKind TypeBoundKind(BoundKind kind);

        JCAnnotation Annotation(JCTree annotationType, List<JCExpression> args);

        JCModifiers Modifiers(long flags, List<JCAnnotation> annotations);

        JCErroneous Erroneous(List<? extends JCTree> errs);

        LetExpr LetExpr(List<JCVariableDecl> defs, JCTree expr);
    }

    public static class JCCompilationUnit extends JCTree implements CompilationUnitTree {
        public List<JCAnnotation> packageAnnotations;
        public JCExpression pid;
        public List<JCTree> defs;
        public JavaFileObject sourcefile;
        public PackageSymbol packge;
        public ImportScope namedImportScope;
        public StarImportScope starImportScope;
        public Position.LineMap lineMap = null;
        public DocCommentTable docComments = null;
        public EndPosTable endPositions = null;

        protected JCCompilationUnit(List<JCAnnotation> packageAnnotations,
                                    JCExpression pid,
                                    List<JCTree> defs,
                                    JavaFileObject sourcefile,
                                    PackageSymbol packge,
                                    ImportScope namedImportScope,
                                    StarImportScope starImportScope) {
            this.packageAnnotations = packageAnnotations;
            this.pid = pid;
            this.defs = defs;
            this.sourcefile = sourcefile;
            this.packge = packge;
            this.namedImportScope = namedImportScope;
            this.starImportScope = starImportScope;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTopLevel(this);
        }

        public Kind getKind() {
            return Kind.COMPILATION_UNIT;
        }

        public List<JCAnnotation> getPackageAnnotations() {
            return packageAnnotations;
        }

        public List<JCImport> getImports() {
            ListBuffer<JCImport> imports = new ListBuffer<JCImport>();
            for (JCTree tree : defs) {
                if (tree.hasTag(IMPORT))
                    imports.append((JCImport) tree);
                else if (!tree.hasTag(SKIP))
                    break;
            }
            return imports.toList();
        }

        public JCExpression getPackageName() {
            return pid;
        }

        public JavaFileObject getSourceFile() {
            return sourcefile;
        }

        public Position.LineMap getLineMap() {
            return lineMap;
        }

        public List<JCTree> getTypeDecls() {
            List<JCTree> typeDefs;
            for (typeDefs = defs; !typeDefs.isEmpty(); typeDefs = typeDefs.tail)
                if (!typeDefs.head.hasTag(IMPORT))
                    break;
            return typeDefs;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitCompilationUnit(this, d);
        }

        @Override
        public Tag getTag() {
            return TOPLEVEL;
        }
    }

    public static class JCImport extends JCTree implements ImportTree {
        public boolean staticImport;
        public JCTree qualid;

        protected JCImport(JCTree qualid, boolean importStatic) {
            this.qualid = qualid;
            this.staticImport = importStatic;
        }

        @Override
        public void accept(Visitor v) {
            v.visitImport(this);
        }

        public boolean isStatic() {
            return staticImport;
        }

        public JCTree getQualifiedIdentifier() {
            return qualid;
        }

        public Kind getKind() {
            return Kind.IMPORT;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitImport(this, d);
        }

        @Override
        public Tag getTag() {
            return IMPORT;
        }
    }

    public static abstract class JCStatement extends JCTree implements StatementTree {
        @Override
        public JCStatement setType(Type type) {
            super.setType(type);
            return this;
        }

        @Override
        public JCStatement setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    public static abstract class JCExpression extends JCTree implements ExpressionTree {
        @Override
        public JCExpression setType(Type type) {
            super.setType(type);
            return this;
        }

        @Override
        public JCExpression setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    public static abstract class JCPolyExpression extends JCExpression {
        public PolyKind polyKind;

        public enum PolyKind {
            STANDALONE,
            POLY
        }
    }

    public static abstract class JCFunctionalExpression extends JCPolyExpression {
        public List<Type> targets;

        public JCFunctionalExpression() {
            polyKind = PolyKind.POLY;
        }

        public Type getDescriptorType(Types types) {
            return targets.nonEmpty() ? types.findDescriptorType(targets.head) : types.createErrorType(null);
        }
    }

    public static class JCClassDecl extends JCStatement implements ClassTree {
        public JCModifiers mods;
        public Name name;
        public List<JCTypeParameter> typarams;
        public JCExpression extending;
        public List<JCExpression> implementing;
        public List<JCTree> defs;
        public ClassSymbol sym;

        protected JCClassDecl(JCModifiers mods,
                              Name name,
                              List<JCTypeParameter> typarams,
                              JCExpression extending,
                              List<JCExpression> implementing,
                              List<JCTree> defs,
                              ClassSymbol sym) {
            this.mods = mods;
            this.name = name;
            this.typarams = typarams;
            this.extending = extending;
            this.implementing = implementing;
            this.defs = defs;
            this.sym = sym;
        }

        @Override
        public void accept(Visitor v) {
            v.visitClassDef(this);
        }

        public Kind getKind() {
            if ((mods.flags & Flags.ANNOTATION) != 0)
                return Kind.ANNOTATION_TYPE;
            else if ((mods.flags & Flags.INTERFACE) != 0)
                return Kind.INTERFACE;
            else if ((mods.flags & Flags.ENUM) != 0)
                return Kind.ENUM;
            else
                return Kind.CLASS;
        }

        public JCModifiers getModifiers() {
            return mods;
        }

        public Name getSimpleName() {
            return name;
        }

        public List<JCTypeParameter> getTypeParameters() {
            return typarams;
        }

        public JCExpression getExtendsClause() {
            return extending;
        }

        public List<JCExpression> getImplementsClause() {
            return implementing;
        }

        public List<JCTree> getMembers() {
            return defs;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitClass(this, d);
        }

        @Override
        public Tag getTag() {
            return CLASSDEF;
        }
    }

    public static class JCMethodDecl extends JCTree implements MethodTree {
        public JCModifiers mods;
        public Name name;
        public JCExpression restype;
        public List<JCTypeParameter> typarams;
        public JCVariableDecl recvparam;
        public List<JCVariableDecl> params;
        public List<JCExpression> thrown;
        public JCBlock body;
        public JCExpression defaultValue;
        public MethodSymbol sym;

        protected JCMethodDecl(JCModifiers mods,
                               Name name,
                               JCExpression restype,
                               List<JCTypeParameter> typarams,
                               JCVariableDecl recvparam,
                               List<JCVariableDecl> params,
                               List<JCExpression> thrown,
                               JCBlock body,
                               JCExpression defaultValue,
                               MethodSymbol sym) {
            this.mods = mods;
            this.name = name;
            this.restype = restype;
            this.typarams = typarams;
            this.params = params;
            this.recvparam = recvparam;
            this.thrown = thrown;
            this.body = body;
            this.defaultValue = defaultValue;
            this.sym = sym;
        }

        @Override
        public void accept(Visitor v) {
            v.visitMethodDef(this);
        }

        public Kind getKind() {
            return Kind.METHOD;
        }

        public JCModifiers getModifiers() {
            return mods;
        }

        public Name getName() {
            return name;
        }

        public JCTree getReturnType() {
            return restype;
        }

        public List<JCTypeParameter> getTypeParameters() {
            return typarams;
        }

        public List<JCVariableDecl> getParameters() {
            return params;
        }

        public JCVariableDecl getReceiverParameter() {
            return recvparam;
        }

        public List<JCExpression> getThrows() {
            return thrown;
        }

        public JCBlock getBody() {
            return body;
        }

        public JCTree getDefaultValue() {
            return defaultValue;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitMethod(this, d);
        }

        @Override
        public Tag getTag() {
            return METHODDEF;
        }
    }

    public static class JCVariableDecl extends JCStatement implements VariableTree {
        public JCModifiers mods;
        public Name name;
        public JCExpression nameexpr;
        public JCExpression vartype;
        public JCExpression init;
        public VarSymbol sym;

        protected JCVariableDecl(JCModifiers mods,
                                 Name name,
                                 JCExpression vartype,
                                 JCExpression init,
                                 VarSymbol sym) {
            this.mods = mods;
            this.name = name;
            this.vartype = vartype;
            this.init = init;
            this.sym = sym;
        }

        protected JCVariableDecl(JCModifiers mods,
                                 JCExpression nameexpr,
                                 JCExpression vartype) {
            this(mods, null, vartype, null, null);
            this.nameexpr = nameexpr;
            if (nameexpr.hasTag(Tag.IDENT)) {
                this.name = ((JCIdent) nameexpr).name;
            } else {
                this.name = ((JCFieldAccess) nameexpr).name;
            }
        }

        @Override
        public void accept(Visitor v) {
            v.visitVarDef(this);
        }

        public Kind getKind() {
            return Kind.VARIABLE;
        }

        public JCModifiers getModifiers() {
            return mods;
        }

        public Name getName() {
            return name;
        }

        public JCExpression getNameExpression() {
            return nameexpr;
        }

        public JCTree getType() {
            return vartype;
        }

        public JCExpression getInitializer() {
            return init;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitVariable(this, d);
        }

        @Override
        public Tag getTag() {
            return VARDEF;
        }
    }

    public static class JCSkip extends JCStatement implements EmptyStatementTree {
        protected JCSkip() {
        }

        @Override
        public void accept(Visitor v) {
            v.visitSkip(this);
        }

        public Kind getKind() {
            return Kind.EMPTY_STATEMENT;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitEmptyStatement(this, d);
        }

        @Override
        public Tag getTag() {
            return SKIP;
        }
    }

    public static class JCBlock extends JCStatement implements BlockTree {
        public long flags;
        public List<JCStatement> stats;
        public int endpos = Position.NOPOS;

        protected JCBlock(long flags, List<JCStatement> stats) {
            this.stats = stats;
            this.flags = flags;
        }

        @Override
        public void accept(Visitor v) {
            v.visitBlock(this);
        }

        public Kind getKind() {
            return Kind.BLOCK;
        }

        public List<JCStatement> getStatements() {
            return stats;
        }

        public boolean isStatic() {
            return (flags & Flags.STATIC) != 0;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitBlock(this, d);
        }

        @Override
        public Tag getTag() {
            return BLOCK;
        }
    }

    public static class JCDoWhileLoop extends JCStatement implements DoWhileLoopTree {
        public JCStatement body;
        public JCExpression cond;

        protected JCDoWhileLoop(JCStatement body, JCExpression cond) {
            this.body = body;
            this.cond = cond;
        }

        @Override
        public void accept(Visitor v) {
            v.visitDoLoop(this);
        }

        public Kind getKind() {
            return Kind.DO_WHILE_LOOP;
        }

        public JCExpression getCondition() {
            return cond;
        }

        public JCStatement getStatement() {
            return body;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitDoWhileLoop(this, d);
        }

        @Override
        public Tag getTag() {
            return DOLOOP;
        }
    }

    public static class JCWhileLoop extends JCStatement implements WhileLoopTree {
        public JCExpression cond;
        public JCStatement body;

        protected JCWhileLoop(JCExpression cond, JCStatement body) {
            this.cond = cond;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitWhileLoop(this);
        }

        public Kind getKind() {
            return Kind.WHILE_LOOP;
        }

        public JCExpression getCondition() {
            return cond;
        }

        public JCStatement getStatement() {
            return body;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitWhileLoop(this, d);
        }

        @Override
        public Tag getTag() {
            return WHILELOOP;
        }
    }

    public static class JCForLoop extends JCStatement implements ForLoopTree {
        public List<JCStatement> init;
        public JCExpression cond;
        public List<JCExpressionStatement> step;
        public JCStatement body;

        protected JCForLoop(List<JCStatement> init,
                            JCExpression cond,
                            List<JCExpressionStatement> update,
                            JCStatement body) {
            this.init = init;
            this.cond = cond;
            this.step = update;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitForLoop(this);
        }

        public Kind getKind() {
            return Kind.FOR_LOOP;
        }

        public JCExpression getCondition() {
            return cond;
        }

        public JCStatement getStatement() {
            return body;
        }

        public List<JCStatement> getInitializer() {
            return init;
        }

        public List<JCExpressionStatement> getUpdate() {
            return step;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitForLoop(this, d);
        }

        @Override
        public Tag getTag() {
            return FORLOOP;
        }
    }

    public static class JCEnhancedForLoop extends JCStatement implements EnhancedForLoopTree {
        public JCVariableDecl var;
        public JCExpression expr;
        public JCStatement body;

        protected JCEnhancedForLoop(JCVariableDecl var, JCExpression expr, JCStatement body) {
            this.var = var;
            this.expr = expr;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitForeachLoop(this);
        }

        public Kind getKind() {
            return Kind.ENHANCED_FOR_LOOP;
        }

        public JCVariableDecl getVariable() {
            return var;
        }

        public JCExpression getExpression() {
            return expr;
        }

        public JCStatement getStatement() {
            return body;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitEnhancedForLoop(this, d);
        }

        @Override
        public Tag getTag() {
            return FOREACHLOOP;
        }
    }

    public static class JCLabeledStatement extends JCStatement implements LabeledStatementTree {
        public Name label;
        public JCStatement body;

        protected JCLabeledStatement(Name label, JCStatement body) {
            this.label = label;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitLabelled(this);
        }

        public Kind getKind() {
            return Kind.LABELED_STATEMENT;
        }

        public Name getLabel() {
            return label;
        }

        public JCStatement getStatement() {
            return body;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitLabeledStatement(this, d);
        }

        @Override
        public Tag getTag() {
            return LABELLED;
        }
    }

    public static class JCSwitch extends JCStatement implements SwitchTree {
        public JCExpression selector;
        public List<JCCase> cases;

        protected JCSwitch(JCExpression selector, List<JCCase> cases) {
            this.selector = selector;
            this.cases = cases;
        }

        @Override
        public void accept(Visitor v) {
            v.visitSwitch(this);
        }

        public Kind getKind() {
            return Kind.SWITCH;
        }

        public JCExpression getExpression() {
            return selector;
        }

        public List<JCCase> getCases() {
            return cases;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitSwitch(this, d);
        }

        @Override
        public Tag getTag() {
            return SWITCH;
        }
    }

    public static class JCCase extends JCStatement implements CaseTree {
        public JCExpression pat;
        public List<JCStatement> stats;

        protected JCCase(JCExpression pat, List<JCStatement> stats) {
            this.pat = pat;
            this.stats = stats;
        }

        @Override
        public void accept(Visitor v) {
            v.visitCase(this);
        }

        public Kind getKind() {
            return Kind.CASE;
        }

        public JCExpression getExpression() {
            return pat;
        }

        public List<JCStatement> getStatements() {
            return stats;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitCase(this, d);
        }

        @Override
        public Tag getTag() {
            return CASE;
        }
    }

    public static class JCSynchronized extends JCStatement implements SynchronizedTree {
        public JCExpression lock;
        public JCBlock body;

        protected JCSynchronized(JCExpression lock, JCBlock body) {
            this.lock = lock;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitSynchronized(this);
        }

        public Kind getKind() {
            return Kind.SYNCHRONIZED;
        }

        public JCExpression getExpression() {
            return lock;
        }

        public JCBlock getBlock() {
            return body;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitSynchronized(this, d);
        }

        @Override
        public Tag getTag() {
            return SYNCHRONIZED;
        }
    }

    public static class JCTry extends JCStatement implements TryTree {
        public JCBlock body;
        public List<JCCatch> catchers;
        public JCBlock finalizer;
        public List<JCTree> resources;
        public boolean finallyCanCompleteNormally;

        protected JCTry(List<JCTree> resources,
                        JCBlock body,
                        List<JCCatch> catchers,
                        JCBlock finalizer) {
            this.body = body;
            this.catchers = catchers;
            this.finalizer = finalizer;
            this.resources = resources;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTry(this);
        }

        public Kind getKind() {
            return Kind.TRY;
        }

        public JCBlock getBlock() {
            return body;
        }

        public List<JCCatch> getCatches() {
            return catchers;
        }

        public JCBlock getFinallyBlock() {
            return finalizer;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitTry(this, d);
        }

        @Override
        public List<JCTree> getResources() {
            return resources;
        }

        @Override
        public Tag getTag() {
            return TRY;
        }
    }

    public static class JCCatch extends JCTree implements CatchTree {
        public JCVariableDecl param;
        public JCBlock body;

        protected JCCatch(JCVariableDecl param, JCBlock body) {
            this.param = param;
            this.body = body;
        }

        @Override
        public void accept(Visitor v) {
            v.visitCatch(this);
        }

        public Kind getKind() {
            return Kind.CATCH;
        }

        public JCVariableDecl getParameter() {
            return param;
        }

        public JCBlock getBlock() {
            return body;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitCatch(this, d);
        }

        @Override
        public Tag getTag() {
            return CATCH;
        }
    }

    public static class JCConditional extends JCPolyExpression implements ConditionalExpressionTree {
        public JCExpression cond;
        public JCExpression truepart;
        public JCExpression falsepart;

        protected JCConditional(JCExpression cond,
                                JCExpression truepart,
                                JCExpression falsepart) {
            this.cond = cond;
            this.truepart = truepart;
            this.falsepart = falsepart;
        }

        @Override
        public void accept(Visitor v) {
            v.visitConditional(this);
        }

        public Kind getKind() {
            return Kind.CONDITIONAL_EXPRESSION;
        }

        public JCExpression getCondition() {
            return cond;
        }

        public JCExpression getTrueExpression() {
            return truepart;
        }

        public JCExpression getFalseExpression() {
            return falsepart;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitConditionalExpression(this, d);
        }

        @Override
        public Tag getTag() {
            return CONDEXPR;
        }
    }

    public static class JCIf extends JCStatement implements IfTree {
        public JCExpression cond;
        public JCStatement thenpart;
        public JCStatement elsepart;

        protected JCIf(JCExpression cond,
                       JCStatement thenpart,
                       JCStatement elsepart) {
            this.cond = cond;
            this.thenpart = thenpart;
            this.elsepart = elsepart;
        }

        @Override
        public void accept(Visitor v) {
            v.visitIf(this);
        }

        public Kind getKind() {
            return Kind.IF;
        }

        public JCExpression getCondition() {
            return cond;
        }

        public JCStatement getThenStatement() {
            return thenpart;
        }

        public JCStatement getElseStatement() {
            return elsepart;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitIf(this, d);
        }

        @Override
        public Tag getTag() {
            return IF;
        }
    }

    public static class JCExpressionStatement extends JCStatement implements ExpressionStatementTree {
        public JCExpression expr;

        protected JCExpressionStatement(JCExpression expr) {
            this.expr = expr;
        }

        @Override
        public void accept(Visitor v) {
            v.visitExec(this);
        }

        public Kind getKind() {
            return Kind.EXPRESSION_STATEMENT;
        }

        public JCExpression getExpression() {
            return expr;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitExpressionStatement(this, d);
        }

        @Override
        public Tag getTag() {
            return EXEC;
        }

        @Override
        public String toString() {
            StringWriter s = new StringWriter();
            try {
                new Pretty(s, false).printStat(this);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return s.toString();
        }
    }

    public static class JCBreak extends JCStatement implements BreakTree {
        public Name label;
        public JCTree target;

        protected JCBreak(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }

        @Override
        public void accept(Visitor v) {
            v.visitBreak(this);
        }

        public Kind getKind() {
            return Kind.BREAK;
        }

        public Name getLabel() {
            return label;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitBreak(this, d);
        }

        @Override
        public Tag getTag() {
            return BREAK;
        }
    }

    public static class JCContinue extends JCStatement implements ContinueTree {
        public Name label;
        public JCTree target;

        protected JCContinue(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }

        @Override
        public void accept(Visitor v) {
            v.visitContinue(this);
        }

        public Kind getKind() {
            return Kind.CONTINUE;
        }

        public Name getLabel() {
            return label;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitContinue(this, d);
        }

        @Override
        public Tag getTag() {
            return CONTINUE;
        }
    }

    public static class JCReturn extends JCStatement implements ReturnTree {
        public JCExpression expr;

        protected JCReturn(JCExpression expr) {
            this.expr = expr;
        }

        @Override
        public void accept(Visitor v) {
            v.visitReturn(this);
        }

        public Kind getKind() {
            return Kind.RETURN;
        }

        public JCExpression getExpression() {
            return expr;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitReturn(this, d);
        }

        @Override
        public Tag getTag() {
            return RETURN;
        }
    }

    public static class JCThrow extends JCStatement implements ThrowTree {
        public JCExpression expr;

        protected JCThrow(JCExpression expr) {
            this.expr = expr;
        }

        @Override
        public void accept(Visitor v) {
            v.visitThrow(this);
        }

        public Kind getKind() {
            return Kind.THROW;
        }

        public JCExpression getExpression() {
            return expr;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitThrow(this, d);
        }

        @Override
        public Tag getTag() {
            return THROW;
        }
    }

    public static class JCAssert extends JCStatement implements AssertTree {
        public JCExpression cond;
        public JCExpression detail;

        protected JCAssert(JCExpression cond, JCExpression detail) {
            this.cond = cond;
            this.detail = detail;
        }

        @Override
        public void accept(Visitor v) {
            v.visitAssert(this);
        }

        public Kind getKind() {
            return Kind.ASSERT;
        }

        public JCExpression getCondition() {
            return cond;
        }

        public JCExpression getDetail() {
            return detail;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitAssert(this, d);
        }

        @Override
        public Tag getTag() {
            return ASSERT;
        }
    }

    public static class JCMethodInvocation extends JCPolyExpression implements MethodInvocationTree {
        public List<JCExpression> typeargs;
        public JCExpression meth;
        public List<JCExpression> args;
        public Type varargsElement;

        protected JCMethodInvocation(List<JCExpression> typeargs,
                                     JCExpression meth,
                                     List<JCExpression> args) {
            this.typeargs = (typeargs == null) ? List.nil()
                    : typeargs;
            this.meth = meth;
            this.args = args;
        }

        @Override
        public void accept(Visitor v) {
            v.visitApply(this);
        }

        public Kind getKind() {
            return Kind.METHOD_INVOCATION;
        }

        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }

        public JCExpression getMethodSelect() {
            return meth;
        }

        public List<JCExpression> getArguments() {
            return args;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitMethodInvocation(this, d);
        }

        @Override
        public JCMethodInvocation setType(Type type) {
            super.setType(type);
            return this;
        }

        @Override
        public Tag getTag() {
            return (APPLY);
        }
    }

    public static class JCNewClass extends JCPolyExpression implements NewClassTree {
        public JCExpression encl;
        public List<JCExpression> typeargs;
        public JCExpression clazz;
        public List<JCExpression> args;
        public JCClassDecl def;
        public Symbol constructor;
        public Type varargsElement;
        public Type constructorType;

        protected JCNewClass(JCExpression encl,
                             List<JCExpression> typeargs,
                             JCExpression clazz,
                             List<JCExpression> args,
                             JCClassDecl def) {
            this.encl = encl;
            this.typeargs = (typeargs == null) ? List.nil()
                    : typeargs;
            this.clazz = clazz;
            this.args = args;
            this.def = def;
        }

        @Override
        public void accept(Visitor v) {
            v.visitNewClass(this);
        }

        public Kind getKind() {
            return Kind.NEW_CLASS;
        }

        public JCExpression getEnclosingExpression() {
            return encl;
        }

        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }

        public JCExpression getIdentifier() {
            return clazz;
        }

        public List<JCExpression> getArguments() {
            return args;
        }

        public JCClassDecl getClassBody() {
            return def;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitNewClass(this, d);
        }

        @Override
        public Tag getTag() {
            return NEWCLASS;
        }
    }

    public static class JCNewArray extends JCExpression implements NewArrayTree {
        public JCExpression elemtype;
        public List<JCExpression> dims;
        public List<JCAnnotation> annotations;
        public List<List<JCAnnotation>> dimAnnotations;
        public List<JCExpression> elems;

        protected JCNewArray(JCExpression elemtype,
                             List<JCExpression> dims,
                             List<JCExpression> elems) {
            this.elemtype = elemtype;
            this.dims = dims;
            this.annotations = List.nil();
            this.dimAnnotations = List.nil();
            this.elems = elems;
        }

        @Override
        public void accept(Visitor v) {
            v.visitNewArray(this);
        }

        public Kind getKind() {
            return Kind.NEW_ARRAY;
        }

        public JCExpression getType() {
            return elemtype;
        }

        public List<JCExpression> getDimensions() {
            return dims;
        }

        public List<JCExpression> getInitializers() {
            return elems;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitNewArray(this, d);
        }

        @Override
        public Tag getTag() {
            return NEWARRAY;
        }

        @Override
        public List<JCAnnotation> getAnnotations() {
            return annotations;
        }

        @Override
        public List<List<JCAnnotation>> getDimAnnotations() {
            return dimAnnotations;
        }
    }

    public static class JCLambda extends JCFunctionalExpression implements LambdaExpressionTree {
        public List<JCVariableDecl> params;
        public JCTree body;
        public boolean canCompleteNormally = true;
        public ParameterKind paramKind;
        public JCLambda(List<JCVariableDecl> params,
                        JCTree body) {
            this.params = params;
            this.body = body;
            if (params.isEmpty() ||
                    params.head.vartype != null) {
                paramKind = ParameterKind.EXPLICIT;
            } else {
                paramKind = ParameterKind.IMPLICIT;
            }
        }

        @Override
        public Tag getTag() {
            return LAMBDA;
        }

        @Override
        public void accept(Visitor v) {
            v.visitLambda(this);
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitLambdaExpression(this, d);
        }

        public Kind getKind() {
            return Kind.LAMBDA_EXPRESSION;
        }

        public JCTree getBody() {
            return body;
        }

        public java.util.List<? extends VariableTree> getParameters() {
            return params;
        }

        @Override
        public JCLambda setType(Type type) {
            super.setType(type);
            return this;
        }

        @Override
        public BodyKind getBodyKind() {
            return body.hasTag(BLOCK) ?
                    BodyKind.STATEMENT :
                    BodyKind.EXPRESSION;
        }

        public enum ParameterKind {
            IMPLICIT,
            EXPLICIT
        }
    }

    public static class JCParens extends JCExpression implements ParenthesizedTree {
        public JCExpression expr;

        protected JCParens(JCExpression expr) {
            this.expr = expr;
        }

        @Override
        public void accept(Visitor v) {
            v.visitParens(this);
        }

        public Kind getKind() {
            return Kind.PARENTHESIZED;
        }

        public JCExpression getExpression() {
            return expr;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitParenthesized(this, d);
        }

        @Override
        public Tag getTag() {
            return PARENS;
        }
    }

    public static class JCAssign extends JCExpression implements AssignmentTree {
        public JCExpression lhs;
        public JCExpression rhs;

        protected JCAssign(JCExpression lhs, JCExpression rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }

        @Override
        public void accept(Visitor v) {
            v.visitAssign(this);
        }

        public Kind getKind() {
            return Kind.ASSIGNMENT;
        }

        public JCExpression getVariable() {
            return lhs;
        }

        public JCExpression getExpression() {
            return rhs;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitAssignment(this, d);
        }

        @Override
        public Tag getTag() {
            return ASSIGN;
        }
    }

    public static class JCAssignOp extends JCExpression implements CompoundAssignmentTree {
        public JCExpression lhs;
        public JCExpression rhs;
        public Symbol operator;
        private Tag opcode;

        protected JCAssignOp(Tag opcode, JCTree lhs, JCTree rhs, Symbol operator) {
            this.opcode = opcode;
            this.lhs = (JCExpression) lhs;
            this.rhs = (JCExpression) rhs;
            this.operator = operator;
        }

        @Override
        public void accept(Visitor v) {
            v.visitAssignop(this);
        }

        public Kind getKind() {
            return TreeInfo.tagToKind(getTag());
        }

        public JCExpression getVariable() {
            return lhs;
        }

        public JCExpression getExpression() {
            return rhs;
        }

        public Symbol getOperator() {
            return operator;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitCompoundAssignment(this, d);
        }

        @Override
        public Tag getTag() {
            return opcode;
        }
    }

    public static class JCUnary extends JCExpression implements UnaryTree {
        public JCExpression arg;
        public Symbol operator;
        private Tag opcode;

        protected JCUnary(Tag opcode, JCExpression arg) {
            this.opcode = opcode;
            this.arg = arg;
        }

        @Override
        public void accept(Visitor v) {
            v.visitUnary(this);
        }

        public Kind getKind() {
            return TreeInfo.tagToKind(getTag());
        }

        public JCExpression getExpression() {
            return arg;
        }

        public Symbol getOperator() {
            return operator;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitUnary(this, d);
        }

        @Override
        public Tag getTag() {
            return opcode;
        }

        public void setTag(Tag tag) {
            opcode = tag;
        }
    }

    public static class JCBinary extends JCExpression implements BinaryTree {
        public JCExpression lhs;
        public JCExpression rhs;
        public Symbol operator;
        private Tag opcode;

        protected JCBinary(Tag opcode,
                           JCExpression lhs,
                           JCExpression rhs,
                           Symbol operator) {
            this.opcode = opcode;
            this.lhs = lhs;
            this.rhs = rhs;
            this.operator = operator;
        }

        @Override
        public void accept(Visitor v) {
            v.visitBinary(this);
        }

        public Kind getKind() {
            return TreeInfo.tagToKind(getTag());
        }

        public JCExpression getLeftOperand() {
            return lhs;
        }

        public JCExpression getRightOperand() {
            return rhs;
        }

        public Symbol getOperator() {
            return operator;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitBinary(this, d);
        }

        @Override
        public Tag getTag() {
            return opcode;
        }
    }

    public static class JCTypeCast extends JCExpression implements TypeCastTree {
        public JCTree clazz;
        public JCExpression expr;

        protected JCTypeCast(JCTree clazz, JCExpression expr) {
            this.clazz = clazz;
            this.expr = expr;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeCast(this);
        }

        public Kind getKind() {
            return Kind.TYPE_CAST;
        }

        public JCTree getType() {
            return clazz;
        }

        public JCExpression getExpression() {
            return expr;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitTypeCast(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPECAST;
        }
    }

    public static class JCInstanceOf extends JCExpression implements InstanceOfTree {
        public JCExpression expr;
        public JCTree clazz;

        protected JCInstanceOf(JCExpression expr, JCTree clazz) {
            this.expr = expr;
            this.clazz = clazz;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeTest(this);
        }

        public Kind getKind() {
            return Kind.INSTANCE_OF;
        }

        public JCTree getType() {
            return clazz;
        }

        public JCExpression getExpression() {
            return expr;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitInstanceOf(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPETEST;
        }
    }

    public static class JCArrayAccess extends JCExpression implements ArrayAccessTree {
        public JCExpression indexed;
        public JCExpression index;

        protected JCArrayAccess(JCExpression indexed, JCExpression index) {
            this.indexed = indexed;
            this.index = index;
        }

        @Override
        public void accept(Visitor v) {
            v.visitIndexed(this);
        }

        public Kind getKind() {
            return Kind.ARRAY_ACCESS;
        }

        public JCExpression getExpression() {
            return indexed;
        }

        public JCExpression getIndex() {
            return index;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitArrayAccess(this, d);
        }

        @Override
        public Tag getTag() {
            return INDEXED;
        }
    }

    public static class JCFieldAccess extends JCExpression implements MemberSelectTree {
        public JCExpression selected;
        public Name name;
        public Symbol sym;

        protected JCFieldAccess(JCExpression selected, Name name, Symbol sym) {
            this.selected = selected;
            this.name = name;
            this.sym = sym;
        }

        @Override
        public void accept(Visitor v) {
            v.visitSelect(this);
        }

        public Kind getKind() {
            return Kind.MEMBER_SELECT;
        }

        public JCExpression getExpression() {
            return selected;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitMemberSelect(this, d);
        }

        public Name getIdentifier() {
            return name;
        }

        @Override
        public Tag getTag() {
            return SELECT;
        }
    }

    public static class JCMemberReference extends JCFunctionalExpression implements MemberReferenceTree {
        public ReferenceMode mode;
        public ReferenceKind kind;
        public Name name;
        public JCExpression expr;
        public List<JCExpression> typeargs;
        public Symbol sym;
        public Type varargsElement;
        public PolyKind refPolyKind;
        public boolean ownerAccessible;
        public OverloadKind overloadKind;

        protected JCMemberReference(ReferenceMode mode, Name name, JCExpression expr, List<JCExpression> typeargs) {
            this.mode = mode;
            this.name = name;
            this.expr = expr;
            this.typeargs = typeargs;
        }

        @Override
        public void accept(Visitor v) {
            v.visitReference(this);
        }

        public Kind getKind() {
            return Kind.MEMBER_REFERENCE;
        }

        @Override
        public ReferenceMode getMode() {
            return mode;
        }

        @Override
        public JCExpression getQualifierExpression() {
            return expr;
        }

        @Override
        public Name getName() {
            return name;
        }

        @Override
        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitMemberReference(this, d);
        }

        @Override
        public Tag getTag() {
            return REFERENCE;
        }

        public boolean hasKind(ReferenceKind kind) {
            return this.kind == kind;
        }

        public enum OverloadKind {
            OVERLOADED,
            UNOVERLOADED
        }

        public enum ReferenceKind {
            SUPER(ReferenceMode.INVOKE, false),
            UNBOUND(ReferenceMode.INVOKE, true),
            STATIC(ReferenceMode.INVOKE, false),
            BOUND(ReferenceMode.INVOKE, false),
            IMPLICIT_INNER(ReferenceMode.NEW, false),
            TOPLEVEL(ReferenceMode.NEW, false),
            ARRAY_CTOR(ReferenceMode.NEW, false);
            final ReferenceMode mode;
            final boolean unbound;

            ReferenceKind(ReferenceMode mode, boolean unbound) {
                this.mode = mode;
                this.unbound = unbound;
            }

            public boolean isUnbound() {
                return unbound;
            }
        }
    }

    public static class JCIdent extends JCExpression implements IdentifierTree {
        public Name name;
        public Symbol sym;

        protected JCIdent(Name name, Symbol sym) {
            this.name = name;
            this.sym = sym;
        }

        @Override
        public void accept(Visitor v) {
            v.visitIdent(this);
        }

        public Kind getKind() {
            return Kind.IDENTIFIER;
        }

        public Name getName() {
            return name;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitIdentifier(this, d);
        }

        @Override
        public Tag getTag() {
            return IDENT;
        }
    }

    public static class JCLiteral extends JCExpression implements LiteralTree {
        public TypeTag typetag;
        public Object value;

        protected JCLiteral(TypeTag typetag, Object value) {
            this.typetag = typetag;
            this.value = value;
        }

        @Override
        public void accept(Visitor v) {
            v.visitLiteral(this);
        }

        public Kind getKind() {
            return typetag.getKindLiteral();
        }

        public Object getValue() {
            switch (typetag) {
                case BOOLEAN:
                    int bi = (Integer) value;
                    return (bi != 0);
                case CHAR:
                    int ci = (Integer) value;
                    char c = (char) ci;
                    if (c != ci)
                        throw new AssertionError("bad value for char literal");
                    return c;
                default:
                    return value;
            }
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitLiteral(this, d);
        }

        @Override
        public JCLiteral setType(Type type) {
            super.setType(type);
            return this;
        }

        @Override
        public Tag getTag() {
            return LITERAL;
        }
    }

    public static class JCPrimitiveTypeTree extends JCExpression implements PrimitiveTypeTree {
        public TypeTag typetag;

        protected JCPrimitiveTypeTree(TypeTag typetag) {
            this.typetag = typetag;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeIdent(this);
        }

        public Kind getKind() {
            return Kind.PRIMITIVE_TYPE;
        }

        public TypeKind getPrimitiveTypeKind() {
            return typetag.getPrimitiveTypeKind();
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitPrimitiveType(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPEIDENT;
        }
    }

    public static class JCArrayTypeTree extends JCExpression implements ArrayTypeTree {
        public JCExpression elemtype;

        protected JCArrayTypeTree(JCExpression elemtype) {
            this.elemtype = elemtype;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeArray(this);
        }

        public Kind getKind() {
            return Kind.ARRAY_TYPE;
        }

        public JCTree getType() {
            return elemtype;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitArrayType(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPEARRAY;
        }
    }

    public static class JCTypeApply extends JCExpression implements ParameterizedTypeTree {
        public JCExpression clazz;
        public List<JCExpression> arguments;

        protected JCTypeApply(JCExpression clazz, List<JCExpression> arguments) {
            this.clazz = clazz;
            this.arguments = arguments;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeApply(this);
        }

        public Kind getKind() {
            return Kind.PARAMETERIZED_TYPE;
        }

        public JCTree getType() {
            return clazz;
        }

        public List<JCExpression> getTypeArguments() {
            return arguments;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitParameterizedType(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPEAPPLY;
        }
    }

    public static class JCTypeUnion extends JCExpression implements UnionTypeTree {
        public List<JCExpression> alternatives;

        protected JCTypeUnion(List<JCExpression> components) {
            this.alternatives = components;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeUnion(this);
        }

        public Kind getKind() {
            return Kind.UNION_TYPE;
        }

        public List<JCExpression> getTypeAlternatives() {
            return alternatives;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitUnionType(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPEUNION;
        }
    }

    public static class JCTypeIntersection extends JCExpression implements IntersectionTypeTree {
        public List<JCExpression> bounds;

        protected JCTypeIntersection(List<JCExpression> bounds) {
            this.bounds = bounds;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeIntersection(this);
        }

        public Kind getKind() {
            return Kind.INTERSECTION_TYPE;
        }

        public List<JCExpression> getBounds() {
            return bounds;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitIntersectionType(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPEINTERSECTION;
        }
    }

    public static class JCTypeParameter extends JCTree implements TypeParameterTree {
        public Name name;
        public List<JCExpression> bounds;
        public List<JCAnnotation> annotations;

        protected JCTypeParameter(Name name, List<JCExpression> bounds, List<JCAnnotation> annotations) {
            this.name = name;
            this.bounds = bounds;
            this.annotations = annotations;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeParameter(this);
        }

        public Kind getKind() {
            return Kind.TYPE_PARAMETER;
        }

        public Name getName() {
            return name;
        }

        public List<JCExpression> getBounds() {
            return bounds;
        }

        public List<JCAnnotation> getAnnotations() {
            return annotations;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitTypeParameter(this, d);
        }

        @Override
        public Tag getTag() {
            return TYPEPARAMETER;
        }
    }

    public static class JCWildcard extends JCExpression implements WildcardTree {
        public TypeBoundKind kind;
        public JCTree inner;

        protected JCWildcard(TypeBoundKind kind, JCTree inner) {
            kind.getClass();
            this.kind = kind;
            this.inner = inner;
        }

        @Override
        public void accept(Visitor v) {
            v.visitWildcard(this);
        }

        public Kind getKind() {
            switch (kind.kind) {
                case UNBOUND:
                    return Kind.UNBOUNDED_WILDCARD;
                case EXTENDS:
                    return Kind.EXTENDS_WILDCARD;
                case SUPER:
                    return Kind.SUPER_WILDCARD;
                default:
                    throw new AssertionError("Unknown wildcard bound " + kind);
            }
        }

        public JCTree getBound() {
            return inner;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitWildcard(this, d);
        }

        @Override
        public Tag getTag() {
            return Tag.WILDCARD;
        }
    }

    public static class TypeBoundKind extends JCTree {
        public BoundKind kind;

        protected TypeBoundKind(BoundKind kind) {
            this.kind = kind;
        }

        @Override
        public void accept(Visitor v) {
            v.visitTypeBoundKind(this);
        }

        public Kind getKind() {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }

        @Override
        public Tag getTag() {
            return TYPEBOUNDKIND;
        }
    }

    public static class JCAnnotation extends JCExpression implements AnnotationTree {
        public JCTree annotationType;
        public List<JCExpression> args;
        public Attribute.Compound attribute;
        private Tag tag;

        protected JCAnnotation(Tag tag, JCTree annotationType, List<JCExpression> args) {
            this.tag = tag;
            this.annotationType = annotationType;
            this.args = args;
        }

        @Override
        public void accept(Visitor v) {
            v.visitAnnotation(this);
        }

        public Kind getKind() {
            return TreeInfo.tagToKind(getTag());
        }

        public JCTree getAnnotationType() {
            return annotationType;
        }

        public List<JCExpression> getArguments() {
            return args;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitAnnotation(this, d);
        }

        @Override
        public Tag getTag() {
            return tag;
        }
    }

    public static class JCModifiers extends JCTree implements ModifiersTree {
        public long flags;
        public List<JCAnnotation> annotations;

        protected JCModifiers(long flags, List<JCAnnotation> annotations) {
            this.flags = flags;
            this.annotations = annotations;
        }

        @Override
        public void accept(Visitor v) {
            v.visitModifiers(this);
        }

        public Kind getKind() {
            return Kind.MODIFIERS;
        }

        public Set<Modifier> getFlags() {
            return Flags.asModifierSet(flags);
        }

        public List<JCAnnotation> getAnnotations() {
            return annotations;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitModifiers(this, d);
        }

        @Override
        public Tag getTag() {
            return MODIFIERS;
        }
    }

    public static class JCAnnotatedType extends JCExpression implements AnnotatedTypeTree {
        public List<JCAnnotation> annotations;
        public JCExpression underlyingType;

        protected JCAnnotatedType(List<JCAnnotation> annotations, JCExpression underlyingType) {
            Assert.check(annotations != null && annotations.nonEmpty());
            this.annotations = annotations;
            this.underlyingType = underlyingType;
        }

        @Override
        public void accept(Visitor v) {
            v.visitAnnotatedType(this);
        }

        public Kind getKind() {
            return Kind.ANNOTATED_TYPE;
        }

        public List<JCAnnotation> getAnnotations() {
            return annotations;
        }

        public JCExpression getUnderlyingType() {
            return underlyingType;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitAnnotatedType(this, d);
        }

        @Override
        public Tag getTag() {
            return ANNOTATED_TYPE;
        }
    }

    public static class JCErroneous extends JCExpression
            implements ErroneousTree {
        public List<? extends JCTree> errs;

        protected JCErroneous(List<? extends JCTree> errs) {
            this.errs = errs;
        }

        @Override
        public void accept(Visitor v) {
            v.visitErroneous(this);
        }

        public Kind getKind() {
            return Kind.ERRONEOUS;
        }

        public List<? extends JCTree> getErrorTrees() {
            return errs;
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            return v.visitErroneous(this, d);
        }

        @Override
        public Tag getTag() {
            return ERRONEOUS;
        }
    }

    public static class LetExpr extends JCExpression {
        public List<JCVariableDecl> defs;
        public JCTree expr;

        protected LetExpr(List<JCVariableDecl> defs, JCTree expr) {
            this.defs = defs;
            this.expr = expr;
        }

        @Override
        public void accept(Visitor v) {
            v.visitLetExpr(this);
        }

        public Kind getKind() {
            throw new AssertionError("LetExpr is not part of a public API");
        }

        @Override
        public <R, D> R accept(TreeVisitor<R, D> v, D d) {
            throw new AssertionError("LetExpr is not part of a public API");
        }

        @Override
        public Tag getTag() {
            return LETEXPR;
        }
    }

    public static abstract class Visitor {
        public void visitTopLevel(JCCompilationUnit that) {
            visitTree(that);
        }

        public void visitImport(JCImport that) {
            visitTree(that);
        }

        public void visitClassDef(JCClassDecl that) {
            visitTree(that);
        }

        public void visitMethodDef(JCMethodDecl that) {
            visitTree(that);
        }

        public void visitVarDef(JCVariableDecl that) {
            visitTree(that);
        }

        public void visitSkip(JCSkip that) {
            visitTree(that);
        }

        public void visitBlock(JCBlock that) {
            visitTree(that);
        }

        public void visitDoLoop(JCDoWhileLoop that) {
            visitTree(that);
        }

        public void visitWhileLoop(JCWhileLoop that) {
            visitTree(that);
        }

        public void visitForLoop(JCForLoop that) {
            visitTree(that);
        }

        public void visitForeachLoop(JCEnhancedForLoop that) {
            visitTree(that);
        }

        public void visitLabelled(JCLabeledStatement that) {
            visitTree(that);
        }

        public void visitSwitch(JCSwitch that) {
            visitTree(that);
        }

        public void visitCase(JCCase that) {
            visitTree(that);
        }

        public void visitSynchronized(JCSynchronized that) {
            visitTree(that);
        }

        public void visitTry(JCTry that) {
            visitTree(that);
        }

        public void visitCatch(JCCatch that) {
            visitTree(that);
        }

        public void visitConditional(JCConditional that) {
            visitTree(that);
        }

        public void visitIf(JCIf that) {
            visitTree(that);
        }

        public void visitExec(JCExpressionStatement that) {
            visitTree(that);
        }

        public void visitBreak(JCBreak that) {
            visitTree(that);
        }

        public void visitContinue(JCContinue that) {
            visitTree(that);
        }

        public void visitReturn(JCReturn that) {
            visitTree(that);
        }

        public void visitThrow(JCThrow that) {
            visitTree(that);
        }

        public void visitAssert(JCAssert that) {
            visitTree(that);
        }

        public void visitApply(JCMethodInvocation that) {
            visitTree(that);
        }

        public void visitNewClass(JCNewClass that) {
            visitTree(that);
        }

        public void visitNewArray(JCNewArray that) {
            visitTree(that);
        }

        public void visitLambda(JCLambda that) {
            visitTree(that);
        }

        public void visitParens(JCParens that) {
            visitTree(that);
        }

        public void visitAssign(JCAssign that) {
            visitTree(that);
        }

        public void visitAssignop(JCAssignOp that) {
            visitTree(that);
        }

        public void visitUnary(JCUnary that) {
            visitTree(that);
        }

        public void visitBinary(JCBinary that) {
            visitTree(that);
        }

        public void visitTypeCast(JCTypeCast that) {
            visitTree(that);
        }

        public void visitTypeTest(JCInstanceOf that) {
            visitTree(that);
        }

        public void visitIndexed(JCArrayAccess that) {
            visitTree(that);
        }

        public void visitSelect(JCFieldAccess that) {
            visitTree(that);
        }

        public void visitReference(JCMemberReference that) {
            visitTree(that);
        }

        public void visitIdent(JCIdent that) {
            visitTree(that);
        }

        public void visitLiteral(JCLiteral that) {
            visitTree(that);
        }

        public void visitTypeIdent(JCPrimitiveTypeTree that) {
            visitTree(that);
        }

        public void visitTypeArray(JCArrayTypeTree that) {
            visitTree(that);
        }

        public void visitTypeApply(JCTypeApply that) {
            visitTree(that);
        }

        public void visitTypeUnion(JCTypeUnion that) {
            visitTree(that);
        }

        public void visitTypeIntersection(JCTypeIntersection that) {
            visitTree(that);
        }

        public void visitTypeParameter(JCTypeParameter that) {
            visitTree(that);
        }

        public void visitWildcard(JCWildcard that) {
            visitTree(that);
        }

        public void visitTypeBoundKind(TypeBoundKind that) {
            visitTree(that);
        }

        public void visitAnnotation(JCAnnotation that) {
            visitTree(that);
        }

        public void visitModifiers(JCModifiers that) {
            visitTree(that);
        }

        public void visitAnnotatedType(JCAnnotatedType that) {
            visitTree(that);
        }

        public void visitErroneous(JCErroneous that) {
            visitTree(that);
        }

        public void visitLetExpr(LetExpr that) {
            visitTree(that);
        }

        public void visitTree(JCTree that) {
            Assert.error();
        }
    }
}
