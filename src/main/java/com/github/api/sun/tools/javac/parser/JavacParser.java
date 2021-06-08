package com.github.api.sun.tools.javac.parser;

import com.github.api.sun.source.tree.MemberReferenceTree.ReferenceMode;
import com.github.api.sun.tools.javac.code.BoundKind;
import com.github.api.sun.tools.javac.code.Flags;
import com.github.api.sun.tools.javac.code.Source;
import com.github.api.sun.tools.javac.code.TypeTag;
import com.github.api.sun.tools.javac.parser.Tokens.Comment;
import com.github.api.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.github.api.sun.tools.javac.parser.Tokens.Token;
import com.github.api.sun.tools.javac.parser.Tokens.TokenKind;
import com.github.api.sun.tools.javac.tree.*;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.ASSERT;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.CASE;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.CATCH;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.EQ;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.GT;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.IMPORT;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.LT;
import static com.github.api.sun.tools.javac.parser.Tokens.TokenKind.*;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.*;

public class JavacParser implements Parser {

    static final int EXPR = 0x1;
    static final int TYPE = 0x2;
    static final int NOPARAMS = 0x4;
    static final int TYPEARG = 0x8;
    static final int DIAMOND = 0x10;
    private static final int infixPrecedenceLevels = 10;
    private final AbstractEndPosTable endPosTable;
    private final DocCommentTable docComments;
    protected Lexer S;
    protected TreeMaker F;
    protected Token token;
    boolean allowGenerics;
    boolean allowDiamond;
    boolean allowMulticatch;
    boolean allowVarargs;
    boolean allowAsserts;
    boolean allowEnums;
    boolean allowForeach;
    boolean allowStaticImport;
    boolean allowAnnotations;
    boolean allowTWR;
    boolean allowStringFolding;
    boolean allowLambda;
    boolean allowMethodReferences;
    boolean allowDefaultMethods;
    boolean allowStaticInterfaceMethods;
    boolean allowIntersectionTypesInCast;
    boolean keepDocComments;
    boolean keepLineMap;
    boolean allowTypeAnnotations;
    boolean allowThisIdent;
    JCVariableDecl receiverParam;
    ArrayList<JCExpression[]> odStackSupply = new ArrayList<JCExpression[]>();
    ArrayList<Token[]> opStackSupply = new ArrayList<Token[]>();
    Filter<TokenKind> LAX_IDENTIFIER = new Filter<TokenKind>() {
        public boolean accepts(TokenKind t) {
            return t == IDENTIFIER || t == UNDERSCORE || t == ASSERT || t == ENUM;
        }
    };
    private Log log;
    private Source source;
    private Names names;
    private List<JCAnnotation> typeAnnotationsPushedBack = List.nil();
    private boolean permitTypeAnnotationsPushBack = false;
    private int mode = 0;

    private int lastmode = 0;
    private JCErroneous errorTree;
    private int errorPos = Position.NOPOS;

    protected JavacParser(ParserFactory fac,
                          Lexer S,
                          boolean keepDocComments,
                          boolean keepLineMap,
                          boolean keepEndPositions) {
        this.S = S;
        nextToken();
        this.F = fac.F;
        this.log = fac.log;
        this.names = fac.names;
        this.source = fac.source;
        this.allowGenerics = source.allowGenerics();
        this.allowVarargs = source.allowVarargs();
        this.allowAsserts = source.allowAsserts();
        this.allowEnums = source.allowEnums();
        this.allowForeach = source.allowForeach();
        this.allowStaticImport = source.allowStaticImport();
        this.allowAnnotations = source.allowAnnotations();
        this.allowTWR = source.allowTryWithResources();
        this.allowDiamond = source.allowDiamond();
        this.allowMulticatch = source.allowMulticatch();
        this.allowStringFolding = fac.options.getBoolean("allowStringFolding", true);
        this.allowLambda = source.allowLambda();
        this.allowMethodReferences = source.allowMethodReferences();
        this.allowDefaultMethods = source.allowDefaultMethods();
        this.allowStaticInterfaceMethods = source.allowStaticInterfaceMethods();
        this.allowIntersectionTypesInCast = source.allowIntersectionTypesInCast();
        this.allowTypeAnnotations = source.allowTypeAnnotations();
        this.keepDocComments = keepDocComments;
        docComments = newDocCommentTable(keepDocComments, fac);
        this.keepLineMap = keepLineMap;
        this.errorTree = F.Erroneous();
        endPosTable = newEndPosTable(keepEndPositions);
    }

    static int prec(TokenKind token) {
        Tag oc = optag(token);
        return (oc != NO_TAG) ? TreeInfo.opPrec(oc) : -1;
    }

    static int earlier(int pos1, int pos2) {
        if (pos1 == Position.NOPOS)
            return pos2;
        if (pos2 == Position.NOPOS)
            return pos1;
        return (pos1 < pos2 ? pos1 : pos2);
    }

    static Tag optag(TokenKind token) {
        switch (token) {
            case BARBAR:
                return OR;
            case AMPAMP:
                return AND;
            case BAR:
                return BITOR;
            case BAREQ:
                return BITOR_ASG;
            case CARET:
                return BITXOR;
            case CARETEQ:
                return BITXOR_ASG;
            case AMP:
                return BITAND;
            case AMPEQ:
                return BITAND_ASG;
            case EQEQ:
                return Tag.EQ;
            case BANGEQ:
                return NE;
            case LT:
                return Tag.LT;
            case GT:
                return Tag.GT;
            case LTEQ:
                return LE;
            case GTEQ:
                return GE;
            case LTLT:
                return SL;
            case LTLTEQ:
                return SL_ASG;
            case GTGT:
                return SR;
            case GTGTEQ:
                return SR_ASG;
            case GTGTGT:
                return USR;
            case GTGTGTEQ:
                return USR_ASG;
            case PLUS:
                return Tag.PLUS;
            case PLUSEQ:
                return PLUS_ASG;
            case SUB:
                return MINUS;
            case SUBEQ:
                return MINUS_ASG;
            case STAR:
                return MUL;
            case STAREQ:
                return MUL_ASG;
            case SLASH:
                return DIV;
            case SLASHEQ:
                return DIV_ASG;
            case PERCENT:
                return MOD;
            case PERCENTEQ:
                return MOD_ASG;
            case INSTANCEOF:
                return TYPETEST;
            default:
                return NO_TAG;
        }
    }

    static Tag unoptag(TokenKind token) {
        switch (token) {
            case PLUS:
                return POS;
            case SUB:
                return NEG;
            case BANG:
                return NOT;
            case TILDE:
                return COMPL;
            case PLUSPLUS:
                return PREINC;
            case SUBSUB:
                return PREDEC;
            default:
                return NO_TAG;
        }
    }

    static TypeTag typetag(TokenKind token) {
        switch (token) {
            case BYTE:
                return TypeTag.BYTE;
            case CHAR:
                return TypeTag.CHAR;
            case SHORT:
                return TypeTag.SHORT;
            case INT:
                return TypeTag.INT;
            case LONG:
                return TypeTag.LONG;
            case FLOAT:
                return TypeTag.FLOAT;
            case DOUBLE:
                return TypeTag.DOUBLE;
            case BOOLEAN:
                return TypeTag.BOOLEAN;
            default:
                return TypeTag.NONE;
        }
    }

    protected AbstractEndPosTable newEndPosTable(boolean keepEndPositions) {
        return keepEndPositions
                ? new SimpleEndPosTable(this)
                : new EmptyEndPosTable(this);
    }

    protected DocCommentTable newDocCommentTable(boolean keepDocComments, ParserFactory fac) {
        return keepDocComments ? new LazyDocCommentTable(fac) : null;
    }

    public Token token() {
        return token;
    }

    public void nextToken() {
        S.nextToken();
        token = S.token();
    }

    protected boolean peekToken(Filter<TokenKind> tk) {
        return peekToken(0, tk);
    }

    protected boolean peekToken(int lookahead, Filter<TokenKind> tk) {
        return tk.accepts(S.token(lookahead + 1).kind);
    }

    protected boolean peekToken(Filter<TokenKind> tk1, Filter<TokenKind> tk2) {
        return peekToken(0, tk1, tk2);
    }

    protected boolean peekToken(int lookahead, Filter<TokenKind> tk1, Filter<TokenKind> tk2) {
        return tk1.accepts(S.token(lookahead + 1).kind) &&
                tk2.accepts(S.token(lookahead + 2).kind);
    }

    protected boolean peekToken(Filter<TokenKind> tk1, Filter<TokenKind> tk2, Filter<TokenKind> tk3) {
        return peekToken(0, tk1, tk2, tk3);
    }

    protected boolean peekToken(int lookahead, Filter<TokenKind> tk1, Filter<TokenKind> tk2, Filter<TokenKind> tk3) {
        return tk1.accepts(S.token(lookahead + 1).kind) &&
                tk2.accepts(S.token(lookahead + 2).kind) &&
                tk3.accepts(S.token(lookahead + 3).kind);
    }

    @SuppressWarnings("unchecked")
    protected boolean peekToken(Filter<TokenKind>... kinds) {
        return peekToken(0, kinds);
    }

    @SuppressWarnings("unchecked")
    protected boolean peekToken(int lookahead, Filter<TokenKind>... kinds) {
        for (; lookahead < kinds.length; lookahead++) {
            if (!kinds[lookahead].accepts(S.token(lookahead + 1).kind)) {
                return false;
            }
        }
        return true;
    }

    private void skip(boolean stopAtImport, boolean stopAtMemberDecl, boolean stopAtIdentifier, boolean stopAtStatement) {
        while (true) {
            switch (token.kind) {
                case SEMI:
                    nextToken();
                    return;
                case PUBLIC:
                case FINAL:
                case ABSTRACT:
                case MONKEYS_AT:
                case EOF:
                case CLASS:
                case INTERFACE:
                case ENUM:
                    return;
                case IMPORT:
                    if (stopAtImport)
                        return;
                    break;
                case LBRACE:
                case RBRACE:
                case PRIVATE:
                case PROTECTED:
                case STATIC:
                case TRANSIENT:
                case NATIVE:
                case VOLATILE:
                case SYNCHRONIZED:
                case STRICTFP:
                case LT:
                case BYTE:
                case SHORT:
                case CHAR:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BOOLEAN:
                case VOID:
                    if (stopAtMemberDecl)
                        return;
                    break;
                case UNDERSCORE:
                case IDENTIFIER:
                    if (stopAtIdentifier)
                        return;
                    break;
                case CASE:
                case DEFAULT:
                case IF:
                case FOR:
                case WHILE:
                case DO:
                case TRY:
                case SWITCH:
                case RETURN:
                case THROW:
                case BREAK:
                case CONTINUE:
                case ELSE:
                case FINALLY:
                case CATCH:
                    if (stopAtStatement)
                        return;
                    break;
            }
            nextToken();
        }
    }

    private JCErroneous syntaxError(int pos, String key, TokenKind... args) {
        return syntaxError(pos, List.nil(), key, args);
    }

    @SuppressWarnings("all")
    private JCErroneous syntaxError(int pos, List<JCTree> errs, String key, TokenKind... args) {
        setErrorEndPos(pos);
        JCErroneous err = F.at(pos).Erroneous(errs);
        reportSyntaxError(err, key, args);
        if (errs != null) {
            JCTree last = errs.last();
            if (last != null)
                storeEnd(last, pos);
        }
        return toP(err);
    }

    private void reportSyntaxError(int pos, String key, Object... args) {
        DiagnosticPosition diag = new JCDiagnostic.SimpleDiagnosticPosition(pos);
        reportSyntaxError(diag, key, args);
    }

    private void reportSyntaxError(DiagnosticPosition diagPos, String key, Object... args) {
        int pos = diagPos.getPreferredPosition();
        if (pos > S.errPos() || pos == Position.NOPOS) {
            if (token.kind == EOF) {
                error(diagPos, "premature.eof");
            } else {
                error(diagPos, key, args);
            }
        }
        S.errPos(pos);
        if (token.pos == errorPos)
            nextToken();
        errorPos = token.pos;
    }

    private JCErroneous syntaxError(String key) {
        return syntaxError(token.pos, key);
    }

    private JCErroneous syntaxError(String key, TokenKind arg) {
        return syntaxError(token.pos, key, arg);
    }

    public void accept(TokenKind tk) {
        if (token.kind == tk) {
            nextToken();
        } else {
            setErrorEndPos(token.pos);
            reportSyntaxError(S.prevToken().endPos, "expected", tk);
        }
    }

    JCExpression illegal(int pos) {
        setErrorEndPos(pos);
        if ((mode & EXPR) != 0)
            return syntaxError(pos, "illegal.start.of.expr");
        else
            return syntaxError(pos, "illegal.start.of.type");
    }

    JCExpression illegal() {
        return illegal(token.pos);
    }

    void checkNoMods(long mods) {
        if (mods != 0) {
            long lowestMod = mods & -mods;
            error(token.pos, "mod.not.allowed.here",
                    Flags.asFlagSet(lowestMod));
        }
    }

    void attach(JCTree tree, Comment dc) {
        if (keepDocComments && dc != null) {
            docComments.putComment(tree, dc);
        }
    }

    private void setErrorEndPos(int errPos) {
        endPosTable.setErrorEndPos(errPos);
    }

    private void storeEnd(JCTree tree, int endpos) {
        endPosTable.storeEnd(tree, endpos);
    }

    private <T extends JCTree> T to(T t) {
        return endPosTable.to(t);
    }

    private <T extends JCTree> T toP(T t) {
        return endPosTable.toP(t);
    }

    public int getStartPos(JCTree tree) {
        return TreeInfo.getStartPos(tree);
    }

    public int getEndPos(JCTree tree) {
        return endPosTable.getEndPos(tree);
    }

    Name ident() {
        if (token.kind == IDENTIFIER) {
            Name name = token.name();
            nextToken();
            return name;
        } else if (token.kind == ASSERT) {
            if (allowAsserts) {
                error(token.pos, "assert.as.identifier");
                nextToken();
                return names.error;
            } else {
                warning(token.pos, "assert.as.identifier");
                Name name = token.name();
                nextToken();
                return name;
            }
        } else if (token.kind == ENUM) {
            if (allowEnums) {
                error(token.pos, "enum.as.identifier");
                nextToken();
                return names.error;
            } else {
                warning(token.pos, "enum.as.identifier");
                Name name = token.name();
                nextToken();
                return name;
            }
        } else if (token.kind == THIS) {
            if (allowThisIdent) {

                checkTypeAnnotations();
                Name name = token.name();
                nextToken();
                return name;
            } else {
                error(token.pos, "this.as.identifier");
                nextToken();
                return names.error;
            }
        } else if (token.kind == UNDERSCORE) {
            warning(token.pos, "underscore.as.identifier");
            Name name = token.name();
            nextToken();
            return name;
        } else {
            accept(IDENTIFIER);
            return names.error;
        }
    }

    public JCExpression qualident(boolean allowAnnos) {
        JCExpression t = toP(F.at(token.pos).Ident(ident()));
        while (token.kind == DOT) {
            int pos = token.pos;
            nextToken();
            List<JCAnnotation> tyannos = null;
            if (allowAnnos) {
                tyannos = typeAnnotationsOpt();
            }
            t = toP(F.at(pos).Select(t, ident()));
            if (tyannos != null && tyannos.nonEmpty()) {
                t = toP(F.at(tyannos.head.pos).AnnotatedType(tyannos, t));
            }
        }
        return t;
    }

    JCExpression literal(Name prefix) {
        return literal(prefix, token.pos);
    }

    JCExpression literal(Name prefix, int pos) {
        JCExpression t = errorTree;
        switch (token.kind) {
            case INTLITERAL:
                try {
                    t = F.at(pos).Literal(
                            TypeTag.INT,
                            Convert.string2int(strval(prefix), token.radix()));
                } catch (NumberFormatException ex) {
                    error(token.pos, "int.number.too.large", strval(prefix));
                }
                break;
            case LONGLITERAL:
                try {
                    t = F.at(pos).Literal(
                            TypeTag.LONG,
                            new Long(Convert.string2long(strval(prefix), token.radix())));
                } catch (NumberFormatException ex) {
                    error(token.pos, "int.number.too.large", strval(prefix));
                }
                break;
            case FLOATLITERAL: {
                String proper = token.radix() == 16 ?
                        ("0x" + token.stringVal()) :
                        token.stringVal();
                Float n;
                try {
                    n = Float.valueOf(proper);
                } catch (NumberFormatException ex) {

                    n = Float.NaN;
                }
                if (n.floatValue() == 0.0f && !isZero(proper))
                    error(token.pos, "fp.number.too.small");
                else if (n.floatValue() == Float.POSITIVE_INFINITY)
                    error(token.pos, "fp.number.too.large");
                else
                    t = F.at(pos).Literal(TypeTag.FLOAT, n);
                break;
            }
            case DOUBLELITERAL: {
                String proper = token.radix() == 16 ?
                        ("0x" + token.stringVal()) :
                        token.stringVal();
                Double n;
                try {
                    n = Double.valueOf(proper);
                } catch (NumberFormatException ex) {

                    n = Double.NaN;
                }
                if (n.doubleValue() == 0.0d && !isZero(proper))
                    error(token.pos, "fp.number.too.small");
                else if (n.doubleValue() == Double.POSITIVE_INFINITY)
                    error(token.pos, "fp.number.too.large");
                else
                    t = F.at(pos).Literal(TypeTag.DOUBLE, n);
                break;
            }
            case CHARLITERAL:
                t = F.at(pos).Literal(
                        TypeTag.CHAR,
                        token.stringVal().charAt(0) + 0);
                break;
            case STRINGLITERAL:
                t = F.at(pos).Literal(
                        TypeTag.CLASS,
                        token.stringVal());
                break;
            case TRUE:
            case FALSE:
                t = F.at(pos).Literal(
                        TypeTag.BOOLEAN,
                        (token.kind == TRUE ? 1 : 0));
                break;
            case NULL:
                t = F.at(pos).Literal(
                        TypeTag.BOT,
                        null);
                break;
            default:
                Assert.error();
        }
        if (t == errorTree)
            t = F.at(pos).Erroneous();
        storeEnd(t, token.endPos);
        nextToken();
        return t;
    }

    boolean isZero(String s) {
        char[] cs = s.toCharArray();
        int base = ((cs.length > 1 && Character.toLowerCase(cs[1]) == 'x') ? 16 : 10);
        int i = ((base == 16) ? 2 : 0);
        while (i < cs.length && (cs[i] == '0' || cs[i] == '.')) i++;
        return !(i < cs.length && (Character.digit(cs[i], base) > 0));
    }

    String strval(Name prefix) {
        String s = token.stringVal();
        return prefix.isEmpty() ? s : prefix + s;
    }

    public JCExpression parseExpression() {
        return term(EXPR);
    }

    public JCExpression parseType() {
        List<JCAnnotation> annotations = typeAnnotationsOpt();
        return parseType(annotations);
    }

    public JCExpression parseType(List<JCAnnotation> annotations) {
        JCExpression result = unannotatedType();
        if (annotations.nonEmpty()) {
            result = insertAnnotationsToMostInner(result, annotations, false);
        }
        return result;
    }

    public JCExpression unannotatedType() {
        return term(TYPE);
    }

    JCExpression term(int newmode) {
        int prevmode = mode;
        mode = newmode;
        JCExpression t = term();
        lastmode = mode;
        mode = prevmode;
        return t;
    }

    JCExpression term() {
        JCExpression t = term1();
        if ((mode & EXPR) != 0 &&
                token.kind == EQ || PLUSEQ.compareTo(token.kind) <= 0 && token.kind.compareTo(GTGTGTEQ) <= 0)
            return termRest(t);
        else
            return t;
    }

    JCExpression termRest(JCExpression t) {
        switch (token.kind) {
            case EQ: {
                int pos = token.pos;
                nextToken();
                mode = EXPR;
                JCExpression t1 = term();
                return toP(F.at(pos).Assign(t, t1));
            }
            case PLUSEQ:
            case SUBEQ:
            case STAREQ:
            case SLASHEQ:
            case PERCENTEQ:
            case AMPEQ:
            case BAREQ:
            case CARETEQ:
            case LTLTEQ:
            case GTGTEQ:
            case GTGTGTEQ:
                int pos = token.pos;
                TokenKind tk = token.kind;
                nextToken();
                mode = EXPR;
                JCExpression t1 = term();
                return F.at(pos).Assignop(optag(tk), t, t1);
            default:
                return t;
        }
    }

    JCExpression term1() {
        JCExpression t = term2();
        if ((mode & EXPR) != 0 && token.kind == QUES) {
            mode = EXPR;
            return term1Rest(t);
        } else {
            return t;
        }
    }

    JCExpression term1Rest(JCExpression t) {
        if (token.kind == QUES) {
            int pos = token.pos;
            nextToken();
            JCExpression t1 = term();
            accept(COLON);
            JCExpression t2 = term1();
            return F.at(pos).Conditional(t, t1, t2);
        } else {
            return t;
        }
    }

    JCExpression term2() {
        JCExpression t = term3();
        if ((mode & EXPR) != 0 && prec(token.kind) >= TreeInfo.orPrec) {
            mode = EXPR;
            return term2Rest(t, TreeInfo.orPrec);
        } else {
            return t;
        }
    }

    JCExpression term2Rest(JCExpression t, int minprec) {
        JCExpression[] odStack = newOdStack();
        Token[] opStack = newOpStack();

        int top = 0;
        odStack[0] = t;
        int startPos = token.pos;
        Token topOp = Tokens.DUMMY;
        while (prec(token.kind) >= minprec) {
            opStack[top] = topOp;
            top++;
            topOp = token;
            nextToken();
            odStack[top] = (topOp.kind == INSTANCEOF) ? parseType() : term3();
            while (top > 0 && prec(topOp.kind) >= prec(token.kind)) {
                odStack[top - 1] = makeOp(topOp.pos, topOp.kind, odStack[top - 1],
                        odStack[top]);
                top--;
                topOp = opStack[top];
            }
        }
        Assert.check(top == 0);
        t = odStack[0];
        if (t.hasTag(Tag.PLUS)) {
            StringBuilder buf = foldStrings(t);
            if (buf != null) {
                t = toP(F.at(startPos).Literal(TypeTag.CLASS, buf.toString()));
            }
        }
        odStackSupply.add(odStack);
        opStackSupply.add(opStack);
        return t;
    }

    private JCExpression makeOp(int pos,
                                TokenKind topOp,
                                JCExpression od1,
                                JCExpression od2) {
        if (topOp == INSTANCEOF) {
            return F.at(pos).TypeTest(od1, od2);
        } else {
            return F.at(pos).Binary(optag(topOp), od1, od2);
        }
    }

    protected StringBuilder foldStrings(JCTree tree) {
        if (!allowStringFolding)
            return null;
        List<String> buf = List.nil();
        while (true) {
            if (tree.hasTag(LITERAL)) {
                JCLiteral lit = (JCLiteral) tree;
                if (lit.typetag == TypeTag.CLASS) {
                    StringBuilder sbuf =
                            new StringBuilder((String) lit.value);
                    while (buf.nonEmpty()) {
                        sbuf.append(buf.head);
                        buf = buf.tail;
                    }
                    return sbuf;
                }
            } else if (tree.hasTag(Tag.PLUS)) {
                JCBinary op = (JCBinary) tree;
                if (op.rhs.hasTag(LITERAL)) {
                    JCLiteral lit = (JCLiteral) op.rhs;
                    if (lit.typetag == TypeTag.CLASS) {
                        buf = buf.prepend((String) lit.value);
                        tree = op.lhs;
                        continue;
                    }
                }
            }
            return null;
        }
    }

    private JCExpression[] newOdStack() {
        if (odStackSupply.isEmpty())
            return new JCExpression[infixPrecedenceLevels + 1];
        return odStackSupply.remove(odStackSupply.size() - 1);
    }

    private Token[] newOpStack() {
        if (opStackSupply.isEmpty())
            return new Token[infixPrecedenceLevels + 1];
        return opStackSupply.remove(opStackSupply.size() - 1);
    }

    protected JCExpression term3() {
        int pos = token.pos;
        JCExpression t;
        List<JCExpression> typeArgs = typeArgumentsOpt(EXPR);
        switch (token.kind) {
            case QUES:
                if ((mode & TYPE) != 0 && (mode & (TYPEARG | NOPARAMS)) == TYPEARG) {
                    mode = TYPE;
                    return typeArgument();
                } else
                    return illegal();
            case PLUSPLUS:
            case SUBSUB:
            case BANG:
            case TILDE:
            case PLUS:
            case SUB:
                if (typeArgs == null && (mode & EXPR) != 0) {
                    TokenKind tk = token.kind;
                    nextToken();
                    mode = EXPR;
                    if (tk == SUB &&
                            (token.kind == INTLITERAL || token.kind == LONGLITERAL) &&
                            token.radix() == 10) {
                        mode = EXPR;
                        t = literal(names.hyphen, pos);
                    } else {
                        t = term3();
                        return F.at(pos).Unary(unoptag(tk), t);
                    }
                } else return illegal();
                break;
            case LPAREN:
                if (typeArgs == null && (mode & EXPR) != 0) {
                    ParensResult pres = analyzeParens();
                    switch (pres) {
                        case CAST:
                            accept(LPAREN);
                            mode = TYPE;
                            int pos1 = pos;
                            List<JCExpression> targets = List.of(t = term3());
                            while (token.kind == AMP) {
                                checkIntersectionTypesInCast();
                                accept(AMP);
                                targets = targets.prepend(term3());
                            }
                            if (targets.length() > 1) {
                                t = toP(F.at(pos1).TypeIntersection(targets.reverse()));
                            }
                            accept(RPAREN);
                            mode = EXPR;
                            JCExpression t1 = term3();
                            return F.at(pos).TypeCast(t, t1);
                        case IMPLICIT_LAMBDA:
                        case EXPLICIT_LAMBDA:
                            t = lambdaExpressionOrStatement(true, pres == ParensResult.EXPLICIT_LAMBDA, pos);
                            break;
                        default:
                            accept(LPAREN);
                            mode = EXPR;
                            t = termRest(term1Rest(term2Rest(term3(), TreeInfo.orPrec)));
                            accept(RPAREN);
                            t = toP(F.at(pos).Parens(t));
                            break;
                    }
                } else {
                    return illegal();
                }
                break;
            case THIS:
                if ((mode & EXPR) != 0) {
                    mode = EXPR;
                    t = to(F.at(pos).Ident(names._this));
                    nextToken();
                    if (typeArgs == null)
                        t = argumentsOpt(null, t);
                    else
                        t = arguments(typeArgs, t);
                    typeArgs = null;
                } else return illegal();
                break;
            case SUPER:
                if ((mode & EXPR) != 0) {
                    mode = EXPR;
                    t = to(F.at(pos).Ident(names._super));
                    t = superSuffix(typeArgs, t);
                    typeArgs = null;
                } else return illegal();
                break;
            case INTLITERAL:
            case LONGLITERAL:
            case FLOATLITERAL:
            case DOUBLELITERAL:
            case CHARLITERAL:
            case STRINGLITERAL:
            case TRUE:
            case FALSE:
            case NULL:
                if (typeArgs == null && (mode & EXPR) != 0) {
                    mode = EXPR;
                    t = literal(names.empty);
                } else return illegal();
                break;
            case NEW:
                if (typeArgs != null) return illegal();
                if ((mode & EXPR) != 0) {
                    mode = EXPR;
                    nextToken();
                    if (token.kind == LT) typeArgs = typeArguments(false);
                    t = creator(pos, typeArgs);
                    typeArgs = null;
                } else return illegal();
                break;
            case MONKEYS_AT:

                List<JCAnnotation> typeAnnos = typeAnnotationsOpt();
                if (typeAnnos.isEmpty()) {

                    throw new AssertionError("Expected type annotations, but found none!");
                }
                JCExpression expr = term3();
                if ((mode & TYPE) == 0) {

                    switch (expr.getTag()) {
                        case REFERENCE: {
                            JCMemberReference mref = (JCMemberReference) expr;
                            mref.expr = toP(F.at(pos).AnnotatedType(typeAnnos, mref.expr));
                            t = mref;
                            break;
                        }
                        case SELECT: {
                            JCFieldAccess sel = (JCFieldAccess) expr;
                            if (sel.name != names._class) {
                                return illegal();
                            } else {
                                log.error(token.pos, "no.annotations.on.dot.class");
                                return expr;
                            }
                        }
                        default:
                            return illegal(typeAnnos.head.pos);
                    }
                } else {

                    t = insertAnnotationsToMostInner(expr, typeAnnos, false);
                }
                break;
            case UNDERSCORE:
            case IDENTIFIER:
            case ASSERT:
            case ENUM:
                if (typeArgs != null) return illegal();
                if ((mode & EXPR) != 0 && peekToken(ARROW)) {
                    t = lambdaExpressionOrStatement(false, false, pos);
                } else {
                    t = toP(F.at(token.pos).Ident(ident()));
                    loop:
                    while (true) {
                        pos = token.pos;
                        final List<JCAnnotation> annos = typeAnnotationsOpt();


                        if (!annos.isEmpty() && token.kind != LBRACKET && token.kind != ELLIPSIS)
                            return illegal(annos.head.pos);
                        switch (token.kind) {
                            case LBRACKET:
                                nextToken();
                                if (token.kind == RBRACKET) {
                                    nextToken();
                                    t = bracketsOpt(t);
                                    t = toP(F.at(pos).TypeArray(t));
                                    if (annos.nonEmpty()) {
                                        t = toP(F.at(pos).AnnotatedType(annos, t));
                                    }

                                    JCExpression nt = bracketsSuffix(t);
                                    if (nt != t && (annos.nonEmpty() || TreeInfo.containsTypeAnnotation(t))) {


                                        syntaxError("no.annotations.on.dot.class");
                                    }
                                    t = nt;
                                } else {
                                    if ((mode & EXPR) != 0) {
                                        mode = EXPR;
                                        JCExpression t1 = term();
                                        if (!annos.isEmpty()) t = illegal(annos.head.pos);
                                        t = to(F.at(pos).Indexed(t, t1));
                                    }
                                    accept(RBRACKET);
                                }
                                break loop;
                            case LPAREN:
                                if ((mode & EXPR) != 0) {
                                    mode = EXPR;
                                    t = arguments(typeArgs, t);
                                    if (!annos.isEmpty()) t = illegal(annos.head.pos);
                                    typeArgs = null;
                                }
                                break loop;
                            case DOT:
                                nextToken();
                                int oldmode = mode;
                                mode &= ~NOPARAMS;
                                typeArgs = typeArgumentsOpt(EXPR);
                                mode = oldmode;
                                if ((mode & EXPR) != 0) {
                                    switch (token.kind) {
                                        case CLASS:
                                            if (typeArgs != null) return illegal();
                                            mode = EXPR;
                                            t = to(F.at(pos).Select(t, names._class));
                                            nextToken();
                                            break loop;
                                        case THIS:
                                            if (typeArgs != null) return illegal();
                                            mode = EXPR;
                                            t = to(F.at(pos).Select(t, names._this));
                                            nextToken();
                                            break loop;
                                        case SUPER:
                                            mode = EXPR;
                                            t = to(F.at(pos).Select(t, names._super));
                                            t = superSuffix(typeArgs, t);
                                            typeArgs = null;
                                            break loop;
                                        case NEW:
                                            if (typeArgs != null) return illegal();
                                            mode = EXPR;
                                            int pos1 = token.pos;
                                            nextToken();
                                            if (token.kind == LT) typeArgs = typeArguments(false);
                                            t = innerCreator(pos1, typeArgs, t);
                                            typeArgs = null;
                                            break loop;
                                    }
                                }
                                List<JCAnnotation> tyannos = null;
                                if ((mode & TYPE) != 0 && token.kind == MONKEYS_AT) {
                                    tyannos = typeAnnotationsOpt();
                                }

                                t = toP(F.at(pos).Select(t, ident()));
                                if (tyannos != null && tyannos.nonEmpty()) {
                                    t = toP(F.at(tyannos.head.pos).AnnotatedType(tyannos, t));
                                }
                                break;
                            case ELLIPSIS:
                                if (this.permitTypeAnnotationsPushBack) {
                                    this.typeAnnotationsPushedBack = annos;
                                } else if (annos.nonEmpty()) {

                                    illegal(annos.head.pos);
                                }
                                break loop;
                            case LT:
                                if ((mode & TYPE) == 0 && isUnboundMemberRef()) {


                                    int pos1 = token.pos;
                                    accept(LT);
                                    ListBuffer<JCExpression> args = new ListBuffer<JCExpression>();
                                    args.append(typeArgument());
                                    while (token.kind == COMMA) {
                                        nextToken();
                                        args.append(typeArgument());
                                    }
                                    accept(GT);
                                    t = toP(F.at(pos1).TypeApply(t, args.toList()));
                                    checkGenerics();
                                    while (token.kind == DOT) {
                                        nextToken();
                                        mode = TYPE;
                                        t = toP(F.at(token.pos).Select(t, ident()));
                                        t = typeArgumentsOpt(t);
                                    }
                                    t = bracketsOpt(t);
                                    if (token.kind != COLCOL) {

                                        t = illegal();
                                    }
                                    mode = EXPR;
                                    return term3Rest(t, typeArgs);
                                }
                                break loop;
                            default:
                                break loop;
                        }
                    }
                }
                if (typeArgs != null) illegal();
                t = typeArgumentsOpt(t);
                break;
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                if (typeArgs != null) illegal();
                t = bracketsSuffix(bracketsOpt(basicType()));
                break;
            case VOID:
                if (typeArgs != null) illegal();
                if ((mode & EXPR) != 0) {
                    nextToken();
                    if (token.kind == DOT) {
                        JCPrimitiveTypeTree ti = toP(F.at(pos).TypeIdent(TypeTag.VOID));
                        t = bracketsSuffix(ti);
                    } else {
                        return illegal(pos);
                    }
                } else {


                    JCPrimitiveTypeTree ti = to(F.at(pos).TypeIdent(TypeTag.VOID));
                    nextToken();
                    return ti;

                }
                break;
            default:
                return illegal();
        }
        return term3Rest(t, typeArgs);
    }

    JCExpression term3Rest(JCExpression t, List<JCExpression> typeArgs) {
        if (typeArgs != null) illegal();
        while (true) {
            int pos1 = token.pos;
            final List<JCAnnotation> annos = typeAnnotationsOpt();
            if (token.kind == LBRACKET) {
                nextToken();
                if ((mode & TYPE) != 0) {
                    int oldmode = mode;
                    mode = TYPE;
                    if (token.kind == RBRACKET) {
                        nextToken();
                        t = bracketsOpt(t);
                        t = toP(F.at(pos1).TypeArray(t));
                        if (token.kind == COLCOL) {
                            mode = EXPR;
                            continue;
                        }
                        if (annos.nonEmpty()) {
                            t = toP(F.at(pos1).AnnotatedType(annos, t));
                        }
                        return t;
                    }
                    mode = oldmode;
                }
                if ((mode & EXPR) != 0) {
                    mode = EXPR;
                    JCExpression t1 = term();
                    t = to(F.at(pos1).Indexed(t, t1));
                }
                accept(RBRACKET);
            } else if (token.kind == DOT) {
                nextToken();
                typeArgs = typeArgumentsOpt(EXPR);
                if (token.kind == SUPER && (mode & EXPR) != 0) {
                    mode = EXPR;
                    t = to(F.at(pos1).Select(t, names._super));
                    nextToken();
                    t = arguments(typeArgs, t);
                    typeArgs = null;
                } else if (token.kind == NEW && (mode & EXPR) != 0) {
                    if (typeArgs != null) return illegal();
                    mode = EXPR;
                    int pos2 = token.pos;
                    nextToken();
                    if (token.kind == LT) typeArgs = typeArguments(false);
                    t = innerCreator(pos2, typeArgs, t);
                    typeArgs = null;
                } else {
                    List<JCAnnotation> tyannos = null;
                    if ((mode & TYPE) != 0 && token.kind == MONKEYS_AT) {

                        tyannos = typeAnnotationsOpt();
                    }
                    t = toP(F.at(pos1).Select(t, ident()));
                    if (tyannos != null && tyannos.nonEmpty()) {
                        t = toP(F.at(tyannos.head.pos).AnnotatedType(tyannos, t));
                    }
                    t = argumentsOpt(typeArgs, typeArgumentsOpt(t));
                    typeArgs = null;
                }
            } else if ((mode & EXPR) != 0 && token.kind == COLCOL) {
                mode = EXPR;
                if (typeArgs != null) return illegal();
                accept(COLCOL);
                t = memberReferenceSuffix(pos1, t);
            } else {
                if (!annos.isEmpty()) {
                    if (permitTypeAnnotationsPushBack)
                        typeAnnotationsPushedBack = annos;
                    else
                        return illegal(annos.head.pos);
                }
                break;
            }
        }
        while ((token.kind == PLUSPLUS || token.kind == SUBSUB) && (mode & EXPR) != 0) {
            mode = EXPR;
            t = to(F.at(token.pos).Unary(
                    token.kind == PLUSPLUS ? POSTINC : POSTDEC, t));
            nextToken();
        }
        return toP(t);
    }

    @SuppressWarnings("fallthrough")
    boolean isUnboundMemberRef() {
        int pos = 0, depth = 0;
        outer:
        for (Token t = S.token(pos); ; t = S.token(++pos)) {
            switch (t.kind) {
                case IDENTIFIER:
                case UNDERSCORE:
                case QUES:
                case EXTENDS:
                case SUPER:
                case DOT:
                case RBRACKET:
                case LBRACKET:
                case COMMA:
                case BYTE:
                case SHORT:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BOOLEAN:
                case CHAR:
                case MONKEYS_AT:
                    break;
                case LPAREN:

                    int nesting = 0;
                    for (; ; pos++) {
                        TokenKind tk2 = S.token(pos).kind;
                        switch (tk2) {
                            case EOF:
                                return false;
                            case LPAREN:
                                nesting++;
                                break;
                            case RPAREN:
                                nesting--;
                                if (nesting == 0) {
                                    continue outer;
                                }
                                break;
                        }
                    }
                case LT:
                    depth++;
                    break;
                case GTGTGT:
                    depth--;
                case GTGT:
                    depth--;
                case GT:
                    depth--;
                    if (depth == 0) {
                        TokenKind nextKind = S.token(pos + 1).kind;
                        return
                                nextKind == TokenKind.DOT ||
                                        nextKind == TokenKind.LBRACKET ||
                                        nextKind == TokenKind.COLCOL;
                    }
                    break;
                default:
                    return false;
            }
        }
    }

    @SuppressWarnings("fallthrough")
    ParensResult analyzeParens() {
        int depth = 0;
        boolean type = false;
        outer:
        for (int lookahead = 0; ; lookahead++) {
            TokenKind tk = S.token(lookahead).kind;
            switch (tk) {
                case COMMA:
                    type = true;
                case EXTENDS:
                case SUPER:
                case DOT:
                case AMP:

                    break;
                case QUES:
                    if (peekToken(lookahead, EXTENDS) ||
                            peekToken(lookahead, SUPER)) {

                        type = true;
                    }
                    break;
                case BYTE:
                case SHORT:
                case INT:
                case LONG:
                case FLOAT:
                case DOUBLE:
                case BOOLEAN:
                case CHAR:
                    if (peekToken(lookahead, RPAREN)) {

                        return ParensResult.CAST;
                    } else if (peekToken(lookahead, LAX_IDENTIFIER)) {

                        return ParensResult.EXPLICIT_LAMBDA;
                    }
                    break;
                case LPAREN:
                    if (lookahead != 0) {

                        return ParensResult.PARENS;
                    } else if (peekToken(lookahead, RPAREN)) {

                        return ParensResult.EXPLICIT_LAMBDA;
                    }
                    break;
                case RPAREN:


                    if (type) return ParensResult.CAST;


                    switch (S.token(lookahead + 1).kind) {

                        case BANG:
                        case TILDE:
                        case LPAREN:
                        case THIS:
                        case SUPER:
                        case INTLITERAL:
                        case LONGLITERAL:
                        case FLOATLITERAL:
                        case DOUBLELITERAL:
                        case CHARLITERAL:
                        case STRINGLITERAL:
                        case TRUE:
                        case FALSE:
                        case NULL:
                        case NEW:
                        case IDENTIFIER:
                        case ASSERT:
                        case ENUM:
                        case UNDERSCORE:
                        case BYTE:
                        case SHORT:
                        case CHAR:
                        case INT:
                        case LONG:
                        case FLOAT:
                        case DOUBLE:
                        case BOOLEAN:
                        case VOID:
                            return ParensResult.CAST;
                        default:
                            return ParensResult.PARENS;
                    }
                case UNDERSCORE:
                case ASSERT:
                case ENUM:
                case IDENTIFIER:
                    if (peekToken(lookahead, LAX_IDENTIFIER)) {

                        return ParensResult.EXPLICIT_LAMBDA;
                    } else if (peekToken(lookahead, RPAREN, ARROW)) {

                        return ParensResult.IMPLICIT_LAMBDA;
                    }
                    type = false;
                    break;
                case FINAL:
                case ELLIPSIS:

                    return ParensResult.EXPLICIT_LAMBDA;
                case MONKEYS_AT:
                    type = true;
                    lookahead += 1;
                    while (peekToken(lookahead, DOT)) {
                        lookahead += 2;
                    }
                    if (peekToken(lookahead, LPAREN)) {
                        lookahead++;

                        int nesting = 0;
                        for (; ; lookahead++) {
                            TokenKind tk2 = S.token(lookahead).kind;
                            switch (tk2) {
                                case EOF:
                                    return ParensResult.PARENS;
                                case LPAREN:
                                    nesting++;
                                    break;
                                case RPAREN:
                                    nesting--;
                                    if (nesting == 0) {
                                        continue outer;
                                    }
                                    break;
                            }
                        }
                    }
                    break;
                case LBRACKET:
                    if (peekToken(lookahead, RBRACKET, LAX_IDENTIFIER)) {

                        return ParensResult.EXPLICIT_LAMBDA;
                    } else if (peekToken(lookahead, RBRACKET, RPAREN) ||
                            peekToken(lookahead, RBRACKET, AMP)) {


                        return ParensResult.CAST;
                    } else if (peekToken(lookahead, RBRACKET)) {

                        type = true;
                        lookahead++;
                        break;
                    } else {
                        return ParensResult.PARENS;
                    }
                case LT:
                    depth++;
                    break;
                case GTGTGT:
                    depth--;
                case GTGT:
                    depth--;
                case GT:
                    depth--;
                    if (depth == 0) {
                        if (peekToken(lookahead, RPAREN) ||
                                peekToken(lookahead, AMP)) {


                            return ParensResult.CAST;
                        } else if (peekToken(lookahead, LAX_IDENTIFIER, COMMA) ||
                                peekToken(lookahead, LAX_IDENTIFIER, RPAREN, ARROW) ||
                                peekToken(lookahead, ELLIPSIS)) {


                            return ParensResult.EXPLICIT_LAMBDA;
                        }


                        type = true;
                        break;
                    } else if (depth < 0) {

                        return ParensResult.PARENS;
                    }
                    break;
                default:

                    return ParensResult.PARENS;
            }
        }
    }

    JCExpression lambdaExpressionOrStatement(boolean hasParens, boolean explicitParams, int pos) {
        List<JCVariableDecl> params = explicitParams ?
                formalParameters(true) :
                implicitParameters(hasParens);
        return lambdaExpressionOrStatementRest(params, pos);
    }

    JCExpression lambdaExpressionOrStatementRest(List<JCVariableDecl> args, int pos) {
        checkLambda();
        accept(ARROW);
        return token.kind == LBRACE ?
                lambdaStatement(args, pos, pos) :
                lambdaExpression(args, pos);
    }

    JCExpression lambdaStatement(List<JCVariableDecl> args, int pos, int pos2) {
        JCBlock block = block(pos2, 0);
        return toP(F.at(pos).Lambda(args, block));
    }

    JCExpression lambdaExpression(List<JCVariableDecl> args, int pos) {
        JCTree expr = parseExpression();
        return toP(F.at(pos).Lambda(args, expr));
    }

    JCExpression superSuffix(List<JCExpression> typeArgs, JCExpression t) {
        nextToken();
        if (token.kind == LPAREN || typeArgs != null) {
            t = arguments(typeArgs, t);
        } else if (token.kind == COLCOL) {
            if (typeArgs != null) return illegal();
            t = memberReferenceSuffix(t);
        } else {
            int pos = token.pos;
            accept(DOT);
            typeArgs = (token.kind == LT) ? typeArguments(false) : null;
            t = toP(F.at(pos).Select(t, ident()));
            t = argumentsOpt(typeArgs, t);
        }
        return t;
    }

    JCPrimitiveTypeTree basicType() {
        JCPrimitiveTypeTree t = to(F.at(token.pos).TypeIdent(typetag(token.kind)));
        nextToken();
        return t;
    }

    JCExpression argumentsOpt(List<JCExpression> typeArgs, JCExpression t) {
        if ((mode & EXPR) != 0 && token.kind == LPAREN || typeArgs != null) {
            mode = EXPR;
            return arguments(typeArgs, t);
        } else {
            return t;
        }
    }

    List<JCExpression> arguments() {
        ListBuffer<JCExpression> args = new ListBuffer<>();
        if (token.kind == LPAREN) {
            nextToken();
            if (token.kind != RPAREN) {
                args.append(parseExpression());
                while (token.kind == COMMA) {
                    nextToken();
                    args.append(parseExpression());
                }
            }
            accept(RPAREN);
        } else {
            syntaxError(token.pos, "expected", LPAREN);
        }
        return args.toList();
    }

    JCMethodInvocation arguments(List<JCExpression> typeArgs, JCExpression t) {
        int pos = token.pos;
        List<JCExpression> args = arguments();
        return toP(F.at(pos).Apply(typeArgs, t, args));
    }

    JCExpression typeArgumentsOpt(JCExpression t) {
        if (token.kind == LT &&
                (mode & TYPE) != 0 &&
                (mode & NOPARAMS) == 0) {
            mode = TYPE;
            checkGenerics();
            return typeArguments(t, false);
        } else {
            return t;
        }
    }

    List<JCExpression> typeArgumentsOpt() {
        return typeArgumentsOpt(TYPE);
    }

    List<JCExpression> typeArgumentsOpt(int useMode) {
        if (token.kind == LT) {
            checkGenerics();
            if ((mode & useMode) == 0 ||
                    (mode & NOPARAMS) != 0) {
                illegal();
            }
            mode = useMode;
            return typeArguments(false);
        }
        return null;
    }

    List<JCExpression> typeArguments(boolean diamondAllowed) {
        if (token.kind == LT) {
            nextToken();
            if (token.kind == GT && diamondAllowed) {
                checkDiamond();
                mode |= DIAMOND;
                nextToken();
                return List.nil();
            } else {
                ListBuffer<JCExpression> args = new ListBuffer<>();
                args.append(((mode & EXPR) == 0) ? typeArgument() : parseType());
                while (token.kind == COMMA) {
                    nextToken();
                    args.append(((mode & EXPR) == 0) ? typeArgument() : parseType());
                }
                switch (token.kind) {
                    case GTGTGTEQ:
                    case GTGTEQ:
                    case GTEQ:
                    case GTGTGT:
                    case GTGT:
                        token = S.split();
                        break;
                    case GT:
                        nextToken();
                        break;
                    default:
                        args.append(syntaxError(token.pos, "expected", GT));
                        break;
                }
                return args.toList();
            }
        } else {
            return List.of(syntaxError(token.pos, "expected", LT));
        }
    }

    JCExpression typeArgument() {
        List<JCAnnotation> annotations = typeAnnotationsOpt();
        if (token.kind != QUES) return parseType(annotations);
        int pos = token.pos;
        nextToken();
        JCExpression result;
        if (token.kind == EXTENDS) {
            TypeBoundKind t = to(F.at(pos).TypeBoundKind(BoundKind.EXTENDS));
            nextToken();
            JCExpression bound = parseType();
            result = F.at(pos).Wildcard(t, bound);
        } else if (token.kind == SUPER) {
            TypeBoundKind t = to(F.at(pos).TypeBoundKind(BoundKind.SUPER));
            nextToken();
            JCExpression bound = parseType();
            result = F.at(pos).Wildcard(t, bound);
        } else if (LAX_IDENTIFIER.accepts(token.kind)) {

            TypeBoundKind t = F.at(Position.NOPOS).TypeBoundKind(BoundKind.UNBOUND);
            JCExpression wc = toP(F.at(pos).Wildcard(t, null));
            JCIdent id = toP(F.at(token.pos).Ident(ident()));
            JCErroneous err = F.at(pos).Erroneous(List.<JCTree>of(wc, id));
            reportSyntaxError(err, "expected3", GT, EXTENDS, SUPER);
            result = err;
        } else {
            TypeBoundKind t = toP(F.at(pos).TypeBoundKind(BoundKind.UNBOUND));
            result = toP(F.at(pos).Wildcard(t, null));
        }
        if (!annotations.isEmpty()) {
            result = toP(F.at(annotations.head.pos).AnnotatedType(annotations, result));
        }
        return result;
    }

    JCTypeApply typeArguments(JCExpression t, boolean diamondAllowed) {
        int pos = token.pos;
        List<JCExpression> args = typeArguments(diamondAllowed);
        return toP(F.at(pos).TypeApply(t, args));
    }

    private JCExpression bracketsOpt(JCExpression t,
                                     List<JCAnnotation> annotations) {
        List<JCAnnotation> nextLevelAnnotations = typeAnnotationsOpt();
        if (token.kind == LBRACKET) {
            int pos = token.pos;
            nextToken();
            t = bracketsOptCont(t, pos, nextLevelAnnotations);
        } else if (!nextLevelAnnotations.isEmpty()) {
            if (permitTypeAnnotationsPushBack) {
                this.typeAnnotationsPushedBack = nextLevelAnnotations;
            } else {
                return illegal(nextLevelAnnotations.head.pos);
            }
        }
        if (!annotations.isEmpty()) {
            t = toP(F.at(token.pos).AnnotatedType(annotations, t));
        }
        return t;
    }

    private JCExpression bracketsOpt(JCExpression t) {
        return bracketsOpt(t, List.nil());
    }

    private JCExpression bracketsOptCont(JCExpression t, int pos,
                                         List<JCAnnotation> annotations) {
        accept(RBRACKET);
        t = bracketsOpt(t);
        t = toP(F.at(pos).TypeArray(t));
        if (annotations.nonEmpty()) {
            t = toP(F.at(pos).AnnotatedType(annotations, t));
        }
        return t;
    }

    JCExpression bracketsSuffix(JCExpression t) {
        if ((mode & EXPR) != 0 && token.kind == DOT) {
            mode = EXPR;
            int pos = token.pos;
            nextToken();
            accept(CLASS);
            if (token.pos == endPosTable.errorEndPos) {

                Name name;
                if (LAX_IDENTIFIER.accepts(token.kind)) {
                    name = token.name();
                    nextToken();
                } else {
                    name = names.error;
                }
                t = F.at(pos).Erroneous(List.<JCTree>of(toP(F.at(pos).Select(t, name))));
            } else {
                t = toP(F.at(pos).Select(t, names._class));
            }
        } else if ((mode & TYPE) != 0) {
            if (token.kind != COLCOL) {
                mode = TYPE;
            }
        } else if (token.kind != COLCOL) {
            syntaxError(token.pos, "dot.class.expected");
        }
        return t;
    }

    JCExpression memberReferenceSuffix(JCExpression t) {
        int pos1 = token.pos;
        accept(COLCOL);
        return memberReferenceSuffix(pos1, t);
    }

    JCExpression memberReferenceSuffix(int pos1, JCExpression t) {
        checkMethodReferences();
        mode = EXPR;
        List<JCExpression> typeArgs = null;
        if (token.kind == LT) {
            typeArgs = typeArguments(false);
        }
        Name refName;
        ReferenceMode refMode;
        if (token.kind == NEW) {
            refMode = ReferenceMode.NEW;
            refName = names.init;
            nextToken();
        } else {
            refMode = ReferenceMode.INVOKE;
            refName = ident();
        }
        return toP(F.at(t.getStartPosition()).Reference(refMode, refName, t, typeArgs));
    }

    JCExpression creator(int newpos, List<JCExpression> typeArgs) {
        List<JCAnnotation> newAnnotations = annotationsOpt(Tag.ANNOTATION);
        switch (token.kind) {
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
            case LONG:
            case FLOAT:
            case DOUBLE:
            case BOOLEAN:
                if (typeArgs == null) {
                    if (newAnnotations.isEmpty()) {
                        return arrayCreatorRest(newpos, basicType());
                    } else {
                        return arrayCreatorRest(newpos, toP(F.at(newAnnotations.head.pos).AnnotatedType(newAnnotations, basicType())));
                    }
                }
                break;
            default:
        }
        JCExpression t = qualident(true);
        int oldmode = mode;
        mode = TYPE;
        boolean diamondFound = false;
        int lastTypeargsPos = -1;
        if (token.kind == LT) {
            checkGenerics();
            lastTypeargsPos = token.pos;
            t = typeArguments(t, true);
            diamondFound = (mode & DIAMOND) != 0;
        }
        while (token.kind == DOT) {
            if (diamondFound) {

                illegal();
            }
            int pos = token.pos;
            nextToken();
            List<JCAnnotation> tyannos = typeAnnotationsOpt();
            t = toP(F.at(pos).Select(t, ident()));
            if (tyannos != null && tyannos.nonEmpty()) {
                t = toP(F.at(tyannos.head.pos).AnnotatedType(tyannos, t));
            }
            if (token.kind == LT) {
                lastTypeargsPos = token.pos;
                checkGenerics();
                t = typeArguments(t, true);
                diamondFound = (mode & DIAMOND) != 0;
            }
        }
        mode = oldmode;
        if (token.kind == LBRACKET || token.kind == MONKEYS_AT) {

            if (newAnnotations.nonEmpty()) {
                t = insertAnnotationsToMostInner(t, newAnnotations, false);
            }
            JCExpression e = arrayCreatorRest(newpos, t);
            if (diamondFound) {
                reportSyntaxError(lastTypeargsPos, "cannot.create.array.with.diamond");
                return toP(F.at(newpos).Erroneous(List.of(e)));
            } else if (typeArgs != null) {
                int pos = newpos;
                if (!typeArgs.isEmpty() && typeArgs.head.pos != Position.NOPOS) {


                    pos = typeArgs.head.pos;
                }
                setErrorEndPos(S.prevToken().endPos);
                JCErroneous err = F.at(pos).Erroneous(typeArgs.prepend(e));
                reportSyntaxError(err, "cannot.create.array.with.type.arguments");
                return toP(err);
            }
            return e;
        } else if (token.kind == LPAREN) {
            JCNewClass newClass = classCreatorRest(newpos, null, typeArgs, t);
            if (newClass.def != null) {
                assert newClass.def.mods.annotations.isEmpty();
                if (newAnnotations.nonEmpty()) {


                    newClass.def.mods.pos = earlier(newClass.def.mods.pos, newAnnotations.head.pos);
                    newClass.def.mods.annotations = newAnnotations;
                }
            } else {

                if (newAnnotations.nonEmpty()) {
                    t = insertAnnotationsToMostInner(t, newAnnotations, false);
                    newClass.clazz = t;
                }
            }
            return newClass;
        } else {
            setErrorEndPos(token.pos);
            reportSyntaxError(token.pos, "expected2", LPAREN, LBRACKET);
            t = toP(F.at(newpos).NewClass(null, typeArgs, t, List.nil(), null));
            return toP(F.at(newpos).Erroneous(List.<JCTree>of(t)));
        }
    }

    JCExpression innerCreator(int newpos, List<JCExpression> typeArgs, JCExpression encl) {
        List<JCAnnotation> newAnnotations = typeAnnotationsOpt();
        JCExpression t = toP(F.at(token.pos).Ident(ident()));
        if (newAnnotations.nonEmpty()) {
            t = toP(F.at(newAnnotations.head.pos).AnnotatedType(newAnnotations, t));
        }
        if (token.kind == LT) {
            int oldmode = mode;
            checkGenerics();
            t = typeArguments(t, true);
            mode = oldmode;
        }
        return classCreatorRest(newpos, encl, typeArgs, t);
    }

    JCExpression arrayCreatorRest(int newpos, JCExpression elemtype) {
        List<JCAnnotation> annos = typeAnnotationsOpt();
        accept(LBRACKET);
        if (token.kind == RBRACKET) {
            accept(RBRACKET);
            elemtype = bracketsOpt(elemtype, annos);
            if (token.kind == LBRACE) {
                JCNewArray na = (JCNewArray) arrayInitializer(newpos, elemtype);
                if (annos.nonEmpty()) {


                    JCAnnotatedType annotated = (JCAnnotatedType) elemtype;
                    assert annotated.annotations == annos;
                    na.annotations = annotated.annotations;
                    na.elemtype = annotated.underlyingType;
                }
                return na;
            } else {
                JCExpression t = toP(F.at(newpos).NewArray(elemtype, List.nil(), null));
                return syntaxError(token.pos, List.of(t), "array.dimension.missing");
            }
        } else {
            ListBuffer<JCExpression> dims = new ListBuffer<JCExpression>();

            ListBuffer<List<JCAnnotation>> dimAnnotations = new ListBuffer<>();
            dimAnnotations.append(annos);
            dims.append(parseExpression());
            accept(RBRACKET);
            while (token.kind == LBRACKET
                    || token.kind == MONKEYS_AT) {
                List<JCAnnotation> maybeDimAnnos = typeAnnotationsOpt();
                int pos = token.pos;
                nextToken();
                if (token.kind == RBRACKET) {
                    elemtype = bracketsOptCont(elemtype, pos, maybeDimAnnos);
                } else {
                    if (token.kind == RBRACKET) {
                        elemtype = bracketsOptCont(elemtype, pos, maybeDimAnnos);
                    } else {
                        dimAnnotations.append(maybeDimAnnos);
                        dims.append(parseExpression());
                        accept(RBRACKET);
                    }
                }
            }
            JCNewArray na = toP(F.at(newpos).NewArray(elemtype, dims.toList(), null));
            na.dimAnnotations = dimAnnotations.toList();
            return na;
        }
    }

    JCNewClass classCreatorRest(int newpos,
                                JCExpression encl,
                                List<JCExpression> typeArgs,
                                JCExpression t) {
        List<JCExpression> args = arguments();
        JCClassDecl body = null;
        if (token.kind == LBRACE) {
            int pos = token.pos;
            List<JCTree> defs = classOrInterfaceBody(names.empty, false);
            JCModifiers mods = F.at(Position.NOPOS).Modifiers(0);
            body = toP(F.at(pos).AnonymousClassDef(mods, defs));
        }
        return toP(F.at(newpos).NewClass(encl, typeArgs, t, args, body));
    }

    JCExpression arrayInitializer(int newpos, JCExpression t) {
        accept(LBRACE);
        ListBuffer<JCExpression> elems = new ListBuffer<JCExpression>();
        if (token.kind == COMMA) {
            nextToken();
        } else if (token.kind != RBRACE) {
            elems.append(variableInitializer());
            while (token.kind == COMMA) {
                nextToken();
                if (token.kind == RBRACE) break;
                elems.append(variableInitializer());
            }
        }
        accept(RBRACE);
        return toP(F.at(newpos).NewArray(t, List.nil(), elems.toList()));
    }

    public JCExpression variableInitializer() {
        return token.kind == LBRACE ? arrayInitializer(token.pos, null) : parseExpression();
    }

    JCExpression parExpression() {
        int pos = token.pos;
        accept(LPAREN);
        JCExpression t = parseExpression();
        accept(RPAREN);
        return toP(F.at(pos).Parens(t));
    }

    JCBlock block(int pos, long flags) {
        accept(LBRACE);
        List<JCStatement> stats = blockStatements();
        JCBlock t = F.at(pos).Block(flags, stats);
        while (token.kind == CASE || token.kind == DEFAULT) {
            syntaxError("orphaned", token.kind);
            switchBlockStatementGroups();
        }


        t.endpos = token.pos;
        accept(RBRACE);
        return toP(t);
    }

    public JCBlock block() {
        return block(token.pos, 0);
    }

    @SuppressWarnings("fallthrough")
    List<JCStatement> blockStatements() {

        ListBuffer<JCStatement> stats = new ListBuffer<JCStatement>();
        while (true) {
            List<JCStatement> stat = blockStatement();
            if (stat.isEmpty()) {
                return stats.toList();
            } else {
                if (token.pos <= endPosTable.errorEndPos) {
                    skip(false, true, true, true);
                }
                stats.addAll(stat);
            }
        }
    }

    JCStatement parseStatementAsBlock() {
        int pos = token.pos;
        List<JCStatement> stats = blockStatement();
        if (stats.isEmpty()) {
            JCErroneous e = F.at(pos).Erroneous();
            error(e, "illegal.start.of.stmt");
            return F.at(pos).Exec(e);
        } else {
            JCStatement first = stats.head;
            String error = null;
            switch (first.getTag()) {
                case CLASSDEF:
                    error = "class.not.allowed";
                    break;
                case VARDEF:
                    error = "variable.not.allowed";
                    break;
            }
            if (error != null) {
                error(first, error);
                List<JCBlock> blist = List.of(F.at(first.pos).Block(0, stats));
                return toP(F.at(pos).Exec(F.at(first.pos).Erroneous(blist)));
            }
            return first;
        }
    }

    @SuppressWarnings("fallthrough")
    List<JCStatement> blockStatement() {

        int pos = token.pos;
        switch (token.kind) {
            case RBRACE:
            case CASE:
            case DEFAULT:
            case EOF:
                return List.nil();
            case LBRACE:
            case IF:
            case FOR:
            case WHILE:
            case DO:
            case TRY:
            case SWITCH:
            case SYNCHRONIZED:
            case RETURN:
            case THROW:
            case BREAK:
            case CONTINUE:
            case SEMI:
            case ELSE:
            case FINALLY:
            case CATCH:
                return List.of(parseStatement());
            case MONKEYS_AT:
            case FINAL: {
                Comment dc = token.comment(CommentStyle.JAVADOC);
                JCModifiers mods = modifiersOpt();
                if (token.kind == INTERFACE ||
                        token.kind == CLASS ||
                        allowEnums && token.kind == ENUM) {
                    return List.of(classOrInterfaceOrEnumDeclaration(mods, dc));
                } else {
                    JCExpression t = parseType();
                    ListBuffer<JCStatement> stats =
                            variableDeclarators(mods, t, new ListBuffer<JCStatement>());

                    storeEnd(stats.last(), token.endPos);
                    accept(SEMI);
                    return stats.toList();
                }
            }
            case ABSTRACT:
            case STRICTFP: {
                Comment dc = token.comment(CommentStyle.JAVADOC);
                JCModifiers mods = modifiersOpt();
                return List.of(classOrInterfaceOrEnumDeclaration(mods, dc));
            }
            case INTERFACE:
            case CLASS:
                Comment dc = token.comment(CommentStyle.JAVADOC);
                return List.of(classOrInterfaceOrEnumDeclaration(modifiersOpt(), dc));
            case ENUM:
            case ASSERT:
                if (allowEnums && token.kind == ENUM) {
                    error(token.pos, "local.enum");
                    dc = token.comment(CommentStyle.JAVADOC);
                    return List.of(classOrInterfaceOrEnumDeclaration(modifiersOpt(), dc));
                } else if (allowAsserts && token.kind == ASSERT) {
                    return List.of(parseStatement());
                }

            default:
                Token prevToken = token;
                JCExpression t = term(EXPR | TYPE);
                if (token.kind == COLON && t.hasTag(IDENT)) {
                    nextToken();
                    JCStatement stat = parseStatement();
                    return List.of(F.at(pos).Labelled(prevToken.name(), stat));
                } else if ((lastmode & TYPE) != 0 && LAX_IDENTIFIER.accepts(token.kind)) {
                    pos = token.pos;
                    JCModifiers mods = F.at(Position.NOPOS).Modifiers(0);
                    F.at(pos);
                    ListBuffer<JCStatement> stats =
                            variableDeclarators(mods, t, new ListBuffer<JCStatement>());

                    storeEnd(stats.last(), token.endPos);
                    accept(SEMI);
                    return stats.toList();
                } else {

                    JCExpressionStatement expr = to(F.at(pos).Exec(checkExprStat(t)));
                    accept(SEMI);
                    return List.of(expr);
                }
        }
    }

    @SuppressWarnings("fallthrough")
    public JCStatement parseStatement() {
        int pos = token.pos;
        switch (token.kind) {
            case LBRACE:
                return block();
            case IF: {
                nextToken();
                JCExpression cond = parExpression();
                JCStatement thenpart = parseStatementAsBlock();
                JCStatement elsepart = null;
                if (token.kind == ELSE) {
                    nextToken();
                    elsepart = parseStatementAsBlock();
                }
                return F.at(pos).If(cond, thenpart, elsepart);
            }
            case FOR: {
                nextToken();
                accept(LPAREN);
                List<JCStatement> inits = token.kind == SEMI ? List.nil() : forInit();
                if (inits.length() == 1 &&
                        inits.head.hasTag(VARDEF) &&
                        ((JCVariableDecl) inits.head).init == null &&
                        token.kind == COLON) {
                    checkForeach();
                    JCVariableDecl var = (JCVariableDecl) inits.head;
                    accept(COLON);
                    JCExpression expr = parseExpression();
                    accept(RPAREN);
                    JCStatement body = parseStatementAsBlock();
                    return F.at(pos).ForeachLoop(var, expr, body);
                } else {
                    accept(SEMI);
                    JCExpression cond = token.kind == SEMI ? null : parseExpression();
                    accept(SEMI);
                    List<JCExpressionStatement> steps = token.kind == RPAREN ? List.nil() : forUpdate();
                    accept(RPAREN);
                    JCStatement body = parseStatementAsBlock();
                    return F.at(pos).ForLoop(inits, cond, steps, body);
                }
            }
            case WHILE: {
                nextToken();
                JCExpression cond = parExpression();
                JCStatement body = parseStatementAsBlock();
                return F.at(pos).WhileLoop(cond, body);
            }
            case DO: {
                nextToken();
                JCStatement body = parseStatementAsBlock();
                accept(WHILE);
                JCExpression cond = parExpression();
                JCDoWhileLoop t = to(F.at(pos).DoLoop(body, cond));
                accept(SEMI);
                return t;
            }
            case TRY: {
                nextToken();
                List<JCTree> resources = List.nil();
                if (token.kind == LPAREN) {
                    checkTryWithResources();
                    nextToken();
                    resources = resources();
                    accept(RPAREN);
                }
                JCBlock body = block();
                ListBuffer<JCCatch> catchers = new ListBuffer<JCCatch>();
                JCBlock finalizer = null;
                if (token.kind == CATCH || token.kind == FINALLY) {
                    while (token.kind == CATCH) catchers.append(catchClause());
                    if (token.kind == FINALLY) {
                        nextToken();
                        finalizer = block();
                    }
                } else {
                    if (allowTWR) {
                        if (resources.isEmpty())
                            error(pos, "try.without.catch.finally.or.resource.decls");
                    } else
                        error(pos, "try.without.catch.or.finally");
                }
                return F.at(pos).Try(resources, body, catchers.toList(), finalizer);
            }
            case SWITCH: {
                nextToken();
                JCExpression selector = parExpression();
                accept(LBRACE);
                List<JCCase> cases = switchBlockStatementGroups();
                JCSwitch t = to(F.at(pos).Switch(selector, cases));
                accept(RBRACE);
                return t;
            }
            case SYNCHRONIZED: {
                nextToken();
                JCExpression lock = parExpression();
                JCBlock body = block();
                return F.at(pos).Synchronized(lock, body);
            }
            case RETURN: {
                nextToken();
                JCExpression result = token.kind == SEMI ? null : parseExpression();
                JCReturn t = to(F.at(pos).Return(result));
                accept(SEMI);
                return t;
            }
            case THROW: {
                nextToken();
                JCExpression exc = parseExpression();
                JCThrow t = to(F.at(pos).Throw(exc));
                accept(SEMI);
                return t;
            }
            case BREAK: {
                nextToken();
                Name label = LAX_IDENTIFIER.accepts(token.kind) ? ident() : null;
                JCBreak t = to(F.at(pos).Break(label));
                accept(SEMI);
                return t;
            }
            case CONTINUE: {
                nextToken();
                Name label = LAX_IDENTIFIER.accepts(token.kind) ? ident() : null;
                JCContinue t = to(F.at(pos).Continue(label));
                accept(SEMI);
                return t;
            }
            case SEMI:
                nextToken();
                return toP(F.at(pos).Skip());
            case ELSE:
                int elsePos = token.pos;
                nextToken();
                return doRecover(elsePos, BasicErrorRecoveryAction.BLOCK_STMT, "else.without.if");
            case FINALLY:
                int finallyPos = token.pos;
                nextToken();
                return doRecover(finallyPos, BasicErrorRecoveryAction.BLOCK_STMT, "finally.without.try");
            case CATCH:
                return doRecover(token.pos, BasicErrorRecoveryAction.CATCH_CLAUSE, "catch.without.try");
            case ASSERT: {
                if (allowAsserts && token.kind == ASSERT) {
                    nextToken();
                    JCExpression assertion = parseExpression();
                    JCExpression message = null;
                    if (token.kind == COLON) {
                        nextToken();
                        message = parseExpression();
                    }
                    JCAssert t = to(F.at(pos).Assert(assertion, message));
                    accept(SEMI);
                    return t;
                }

            }
            case ENUM:
            default:
                Token prevToken = token;
                JCExpression expr = parseExpression();
                if (token.kind == COLON && expr.hasTag(IDENT)) {
                    nextToken();
                    JCStatement stat = parseStatement();
                    return F.at(pos).Labelled(prevToken.name(), stat);
                } else {

                    JCExpressionStatement stat = to(F.at(pos).Exec(checkExprStat(expr)));
                    accept(SEMI);
                    return stat;
                }
        }
    }

    private JCStatement doRecover(int startPos, ErrorRecoveryAction action, String key) {
        int errPos = S.errPos();
        JCTree stm = action.doRecover(this);
        S.errPos(errPos);
        return toP(F.Exec(syntaxError(startPos, List.of(stm), key)));
    }

    protected JCCatch catchClause() {
        int pos = token.pos;
        accept(CATCH);
        accept(LPAREN);
        JCModifiers mods = optFinal(Flags.PARAMETER);
        List<JCExpression> catchTypes = catchTypes();
        JCExpression paramType = catchTypes.size() > 1 ?
                toP(F.at(catchTypes.head.getStartPosition()).TypeUnion(catchTypes)) :
                catchTypes.head;
        JCVariableDecl formal = variableDeclaratorId(mods, paramType);
        accept(RPAREN);
        JCBlock body = block();
        return F.at(pos).Catch(formal, body);
    }

    List<JCExpression> catchTypes() {
        ListBuffer<JCExpression> catchTypes = new ListBuffer<>();
        catchTypes.add(parseType());
        while (token.kind == BAR) {
            checkMulticatch();
            nextToken();


            catchTypes.add(parseType());
        }
        return catchTypes.toList();
    }

    List<JCCase> switchBlockStatementGroups() {
        ListBuffer<JCCase> cases = new ListBuffer<JCCase>();
        while (true) {
            int pos = token.pos;
            switch (token.kind) {
                case CASE:
                case DEFAULT:
                    cases.append(switchBlockStatementGroup());
                    break;
                case RBRACE:
                case EOF:
                    return cases.toList();
                default:
                    nextToken();
                    syntaxError(pos, "expected3",
                            CASE, DEFAULT, RBRACE);
            }
        }
    }

    protected JCCase switchBlockStatementGroup() {
        int pos = token.pos;
        List<JCStatement> stats;
        JCCase c;
        switch (token.kind) {
            case CASE:
                nextToken();
                JCExpression pat = parseExpression();
                accept(COLON);
                stats = blockStatements();
                c = F.at(pos).Case(pat, stats);
                if (stats.isEmpty())
                    storeEnd(c, S.prevToken().endPos);
                return c;
            case DEFAULT:
                nextToken();
                accept(COLON);
                stats = blockStatements();
                c = F.at(pos).Case(null, stats);
                if (stats.isEmpty())
                    storeEnd(c, S.prevToken().endPos);
                return c;
        }
        throw new AssertionError("should not reach here");
    }

    <T extends ListBuffer<? super JCExpressionStatement>> T moreStatementExpressions(int pos,
                                                                                     JCExpression first,
                                                                                     T stats) {

        stats.append(toP(F.at(pos).Exec(checkExprStat(first))));
        while (token.kind == COMMA) {
            nextToken();
            pos = token.pos;
            JCExpression t = parseExpression();

            stats.append(toP(F.at(pos).Exec(checkExprStat(t))));
        }
        return stats;
    }

    List<JCStatement> forInit() {
        ListBuffer<JCStatement> stats = new ListBuffer<>();
        int pos = token.pos;
        if (token.kind == FINAL || token.kind == MONKEYS_AT) {
            return variableDeclarators(optFinal(0), parseType(), stats).toList();
        } else {
            JCExpression t = term(EXPR | TYPE);
            if ((lastmode & TYPE) != 0 && LAX_IDENTIFIER.accepts(token.kind)) {
                return variableDeclarators(modifiersOpt(), t, stats).toList();
            } else if ((lastmode & TYPE) != 0 && token.kind == COLON) {
                error(pos, "bad.initializer", "for-loop");
                return List.of(F.at(pos).VarDef(null, null, t, null));
            } else {
                return moreStatementExpressions(pos, t, stats).toList();
            }
        }
    }

    List<JCExpressionStatement> forUpdate() {
        return moreStatementExpressions(token.pos,
                parseExpression(),
                new ListBuffer<JCExpressionStatement>()).toList();
    }

    List<JCAnnotation> annotationsOpt(Tag kind) {
        if (token.kind != MONKEYS_AT) return List.nil();
        ListBuffer<JCAnnotation> buf = new ListBuffer<JCAnnotation>();
        int prevmode = mode;
        while (token.kind == MONKEYS_AT) {
            int pos = token.pos;
            nextToken();
            buf.append(annotation(pos, kind));
        }
        lastmode = mode;
        mode = prevmode;
        List<JCAnnotation> annotations = buf.toList();
        return annotations;
    }

    List<JCAnnotation> typeAnnotationsOpt() {
        List<JCAnnotation> annotations = annotationsOpt(Tag.TYPE_ANNOTATION);
        return annotations;
    }

    JCModifiers modifiersOpt() {
        return modifiersOpt(null);
    }

    protected JCModifiers modifiersOpt(JCModifiers partial) {
        long flags;
        ListBuffer<JCAnnotation> annotations = new ListBuffer<JCAnnotation>();
        int pos;
        if (partial == null) {
            flags = 0;
            pos = token.pos;
        } else {
            flags = partial.flags;
            annotations.appendList(partial.annotations);
            pos = partial.pos;
        }
        if (token.deprecatedFlag()) {
            flags |= Flags.DEPRECATED;
        }
        int lastPos;
        loop:
        while (true) {
            long flag;
            switch (token.kind) {
                case PRIVATE:
                    flag = Flags.PRIVATE;
                    break;
                case PROTECTED:
                    flag = Flags.PROTECTED;
                    break;
                case PUBLIC:
                    flag = Flags.PUBLIC;
                    break;
                case STATIC:
                    flag = Flags.STATIC;
                    break;
                case TRANSIENT:
                    flag = Flags.TRANSIENT;
                    break;
                case FINAL:
                    flag = Flags.FINAL;
                    break;
                case ABSTRACT:
                    flag = Flags.ABSTRACT;
                    break;
                case NATIVE:
                    flag = Flags.NATIVE;
                    break;
                case VOLATILE:
                    flag = Flags.VOLATILE;
                    break;
                case SYNCHRONIZED:
                    flag = Flags.SYNCHRONIZED;
                    break;
                case STRICTFP:
                    flag = Flags.STRICTFP;
                    break;
                case MONKEYS_AT:
                    flag = Flags.ANNOTATION;
                    break;
                case DEFAULT:
                    checkDefaultMethods();
                    flag = Flags.DEFAULT;
                    break;
                case ERROR:
                    flag = 0;
                    nextToken();
                    break;
                default:
                    break loop;
            }
            if ((flags & flag) != 0) error(token.pos, "repeated.modifier");
            lastPos = token.pos;
            nextToken();
            if (flag == Flags.ANNOTATION) {
                checkAnnotations();
                if (token.kind != INTERFACE) {
                    JCAnnotation ann = annotation(lastPos, Tag.ANNOTATION);

                    if (flags == 0 && annotations.isEmpty())
                        pos = ann.pos;
                    annotations.append(ann);
                    flag = 0;
                }
            }
            flags |= flag;
        }
        switch (token.kind) {
            case ENUM:
                flags |= Flags.ENUM;
                break;
            case INTERFACE:
                flags |= Flags.INTERFACE;
                break;
            default:
                break;
        }

        if ((flags & (Flags.ModifierFlags | Flags.ANNOTATION)) == 0 && annotations.isEmpty())
            pos = Position.NOPOS;
        JCModifiers mods = F.at(pos).Modifiers(flags, annotations.toList());
        if (pos != Position.NOPOS)
            storeEnd(mods, S.prevToken().endPos);
        return mods;
    }

    JCAnnotation annotation(int pos, Tag kind) {

        checkAnnotations();
        if (kind == Tag.TYPE_ANNOTATION) {
            checkTypeAnnotations();
        }
        JCTree ident = qualident(false);
        List<JCExpression> fieldValues = annotationFieldValuesOpt();
        JCAnnotation ann;
        if (kind == Tag.ANNOTATION) {
            ann = F.at(pos).Annotation(ident, fieldValues);
        } else if (kind == Tag.TYPE_ANNOTATION) {
            ann = F.at(pos).TypeAnnotation(ident, fieldValues);
        } else {
            throw new AssertionError("Unhandled annotation kind: " + kind);
        }
        storeEnd(ann, S.prevToken().endPos);
        return ann;
    }

    List<JCExpression> annotationFieldValuesOpt() {
        return (token.kind == LPAREN) ? annotationFieldValues() : List.nil();
    }

    List<JCExpression> annotationFieldValues() {
        accept(LPAREN);
        ListBuffer<JCExpression> buf = new ListBuffer<JCExpression>();
        if (token.kind != RPAREN) {
            buf.append(annotationFieldValue());
            while (token.kind == COMMA) {
                nextToken();
                buf.append(annotationFieldValue());
            }
        }
        accept(RPAREN);
        return buf.toList();
    }

    JCExpression annotationFieldValue() {
        if (LAX_IDENTIFIER.accepts(token.kind)) {
            mode = EXPR;
            JCExpression t1 = term1();
            if (t1.hasTag(IDENT) && token.kind == EQ) {
                int pos = token.pos;
                accept(EQ);
                JCExpression v = annotationValue();
                return toP(F.at(pos).Assign(t1, v));
            } else {
                return t1;
            }
        }
        return annotationValue();
    }

    JCExpression annotationValue() {
        int pos;
        switch (token.kind) {
            case MONKEYS_AT:
                pos = token.pos;
                nextToken();
                return annotation(pos, Tag.ANNOTATION);
            case LBRACE:
                pos = token.pos;
                accept(LBRACE);
                ListBuffer<JCExpression> buf = new ListBuffer<JCExpression>();
                if (token.kind == COMMA) {
                    nextToken();
                } else if (token.kind != RBRACE) {
                    buf.append(annotationValue());
                    while (token.kind == COMMA) {
                        nextToken();
                        if (token.kind == RBRACE) break;
                        buf.append(annotationValue());
                    }
                }
                accept(RBRACE);
                return toP(F.at(pos).NewArray(null, List.nil(), buf.toList()));
            default:
                mode = EXPR;
                return term1();
        }
    }

    public <T extends ListBuffer<? super JCVariableDecl>> T variableDeclarators(JCModifiers mods,
                                                                                JCExpression type,
                                                                                T vdefs) {
        return variableDeclaratorsRest(token.pos, mods, type, ident(), false, null, vdefs);
    }

    <T extends ListBuffer<? super JCVariableDecl>> T variableDeclaratorsRest(int pos,
                                                                             JCModifiers mods,
                                                                             JCExpression type,
                                                                             Name name,
                                                                             boolean reqInit,
                                                                             Comment dc,
                                                                             T vdefs) {
        vdefs.append(variableDeclaratorRest(pos, mods, type, name, reqInit, dc));
        while (token.kind == COMMA) {

            storeEnd((JCTree) vdefs.last(), token.endPos);
            nextToken();
            vdefs.append(variableDeclarator(mods, type, reqInit, dc));
        }
        return vdefs;
    }

    JCVariableDecl variableDeclarator(JCModifiers mods, JCExpression type, boolean reqInit, Comment dc) {
        return variableDeclaratorRest(token.pos, mods, type, ident(), reqInit, dc);
    }

    JCVariableDecl variableDeclaratorRest(int pos, JCModifiers mods, JCExpression type, Name name,
                                          boolean reqInit, Comment dc) {
        type = bracketsOpt(type);
        JCExpression init = null;
        if (token.kind == EQ) {
            nextToken();
            init = variableInitializer();
        } else if (reqInit) syntaxError(token.pos, "expected", EQ);
        JCVariableDecl result =
                toP(F.at(pos).VarDef(mods, name, type, init));
        attach(result, dc);
        return result;
    }

    JCVariableDecl variableDeclaratorId(JCModifiers mods, JCExpression type) {
        return variableDeclaratorId(mods, type, false);
    }

    JCVariableDecl variableDeclaratorId(JCModifiers mods, JCExpression type, boolean lambdaParameter) {
        int pos = token.pos;
        Name name;
        if (lambdaParameter && token.kind == UNDERSCORE) {
            log.error(pos, "underscore.as.identifier.in.lambda");
            name = token.name();
            nextToken();
        } else {
            if (allowThisIdent) {
                JCExpression pn = qualident(false);
                if (pn.hasTag(Tag.IDENT) && ((JCIdent) pn).name != names._this) {
                    name = ((JCIdent) pn).name;
                } else {
                    if ((mods.flags & Flags.VARARGS) != 0) {
                        log.error(token.pos, "varargs.and.receiver");
                    }
                    if (token.kind == LBRACKET) {
                        log.error(token.pos, "array.and.receiver");
                    }
                    return toP(F.at(pos).ReceiverVarDef(mods, pn, type));
                }
            } else {
                name = ident();
            }
        }
        if ((mods.flags & Flags.VARARGS) != 0 &&
                token.kind == LBRACKET) {
            log.error(token.pos, "varargs.and.old.array.syntax");
        }
        type = bracketsOpt(type);
        return toP(F.at(pos).VarDef(mods, name, type, null));
    }

    List<JCTree> resources() {
        ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
        defs.append(resource());
        while (token.kind == SEMI) {

            storeEnd(defs.last(), token.endPos);
            int semiColonPos = token.pos;
            nextToken();
            if (token.kind == RPAREN) {

                break;
            }
            defs.append(resource());
        }
        return defs.toList();
    }

    protected JCTree resource() {
        JCModifiers optFinal = optFinal(Flags.FINAL);
        JCExpression type = parseType();
        int pos = token.pos;
        Name ident = ident();
        return variableDeclaratorRest(pos, optFinal, type, ident, true, null);
    }

    public JCCompilationUnit parseCompilationUnit() {
        Token firstToken = token;
        JCExpression pid = null;
        JCModifiers mods = null;
        boolean consumedToplevelDoc = false;
        boolean seenImport = false;
        boolean seenPackage = false;
        List<JCAnnotation> packageAnnotations = List.nil();
        if (token.kind == MONKEYS_AT)
            mods = modifiersOpt();
        if (token.kind == PACKAGE) {
            seenPackage = true;
            if (mods != null) {
                checkNoMods(mods.flags);
                packageAnnotations = mods.annotations;
                mods = null;
            }
            nextToken();
            pid = qualident(false);
            accept(SEMI);
        }
        ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
        boolean checkForImports = true;
        boolean firstTypeDecl = true;
        while (token.kind != EOF) {
            if (token.pos > 0 && token.pos <= endPosTable.errorEndPos) {

                skip(checkForImports, false, false, false);
                if (token.kind == EOF)
                    break;
            }
            if (checkForImports && mods == null && token.kind == IMPORT) {
                seenImport = true;
                defs.append(importDeclaration());
            } else {
                Comment docComment = token.comment(CommentStyle.JAVADOC);
                if (firstTypeDecl && !seenImport && !seenPackage) {
                    docComment = firstToken.comment(CommentStyle.JAVADOC);
                    consumedToplevelDoc = true;
                }
                JCTree def = typeDeclaration(mods, docComment);
                if (def instanceof JCExpressionStatement)
                    def = ((JCExpressionStatement) def).expr;
                defs.append(def);
                if (def instanceof JCClassDecl)
                    checkForImports = false;
                mods = null;
                firstTypeDecl = false;
            }
        }
        JCCompilationUnit toplevel = F.at(firstToken.pos).TopLevel(packageAnnotations, pid, defs.toList());
        if (!consumedToplevelDoc)
            attach(toplevel, firstToken.comment(CommentStyle.JAVADOC));
        if (defs.isEmpty())
            storeEnd(toplevel, S.prevToken().endPos);
        if (keepDocComments)
            toplevel.docComments = docComments;
        if (keepLineMap)
            toplevel.lineMap = S.getLineMap();
        this.endPosTable.setParser(null);
        toplevel.endPositions = this.endPosTable;
        return toplevel;
    }

    JCTree importDeclaration() {
        int pos = token.pos;
        nextToken();
        boolean importStatic = false;
        if (token.kind == STATIC) {
            checkStaticImports();
            importStatic = true;
            nextToken();
        }
        JCExpression pid = toP(F.at(token.pos).Ident(ident()));
        do {
            int pos1 = token.pos;
            accept(DOT);
            if (token.kind == STAR) {
                pid = to(F.at(pos1).Select(pid, names.asterisk));
                nextToken();
                break;
            } else {
                pid = toP(F.at(pos1).Select(pid, ident()));
            }
        } while (token.kind == DOT);
        accept(SEMI);
        return toP(F.at(pos).Import(pid, importStatic));
    }

    JCTree typeDeclaration(JCModifiers mods, Comment docComment) {
        int pos = token.pos;
        if (mods == null && token.kind == SEMI) {
            nextToken();
            return toP(F.at(pos).Skip());
        } else {
            return classOrInterfaceOrEnumDeclaration(modifiersOpt(mods), docComment);
        }
    }

    JCStatement classOrInterfaceOrEnumDeclaration(JCModifiers mods, Comment dc) {
        if (token.kind == CLASS) {
            return classDeclaration(mods, dc);
        } else if (token.kind == INTERFACE) {
            return interfaceDeclaration(mods, dc);
        } else if (allowEnums) {
            if (token.kind == ENUM) {
                return enumDeclaration(mods, dc);
            } else {
                int pos = token.pos;
                List<JCTree> errs;
                if (LAX_IDENTIFIER.accepts(token.kind)) {
                    errs = List.of(mods, toP(F.at(pos).Ident(ident())));
                    setErrorEndPos(token.pos);
                } else {
                    errs = List.of(mods);
                }
                return toP(F.Exec(syntaxError(pos, errs, "expected3",
                        CLASS, INTERFACE, ENUM)));
            }
        } else {
            if (token.kind == ENUM) {
                error(token.pos, "enums.not.supported.in.source", source.name);
                allowEnums = true;
                return enumDeclaration(mods, dc);
            }
            int pos = token.pos;
            List<JCTree> errs;
            if (LAX_IDENTIFIER.accepts(token.kind)) {
                errs = List.of(mods, toP(F.at(pos).Ident(ident())));
                setErrorEndPos(token.pos);
            } else {
                errs = List.of(mods);
            }
            return toP(F.Exec(syntaxError(pos, errs, "expected2",
                    CLASS, INTERFACE)));
        }
    }

    protected JCClassDecl classDeclaration(JCModifiers mods, Comment dc) {
        int pos = token.pos;
        accept(CLASS);
        Name name = ident();
        List<JCTypeParameter> typarams = typeParametersOpt();
        JCExpression extending = null;
        if (token.kind == EXTENDS) {
            nextToken();
            extending = parseType();
        }
        List<JCExpression> implementing = List.nil();
        if (token.kind == IMPLEMENTS) {
            nextToken();
            implementing = typeList();
        }
        List<JCTree> defs = classOrInterfaceBody(name, false);
        JCClassDecl result = toP(F.at(pos).ClassDef(
                mods, name, typarams, extending, implementing, defs));
        attach(result, dc);
        return result;
    }

    protected JCClassDecl interfaceDeclaration(JCModifiers mods, Comment dc) {
        int pos = token.pos;
        accept(INTERFACE);
        Name name = ident();
        List<JCTypeParameter> typarams = typeParametersOpt();
        List<JCExpression> extending = List.nil();
        if (token.kind == EXTENDS) {
            nextToken();
            extending = typeList();
        }
        List<JCTree> defs = classOrInterfaceBody(name, true);
        JCClassDecl result = toP(F.at(pos).ClassDef(
                mods, name, typarams, null, extending, defs));
        attach(result, dc);
        return result;
    }

    protected JCClassDecl enumDeclaration(JCModifiers mods, Comment dc) {
        int pos = token.pos;
        accept(ENUM);
        Name name = ident();
        List<JCExpression> implementing = List.nil();
        if (token.kind == IMPLEMENTS) {
            nextToken();
            implementing = typeList();
        }
        List<JCTree> defs = enumBody(name);
        mods.flags |= Flags.ENUM;
        JCClassDecl result = toP(F.at(pos).
                ClassDef(mods, name, List.nil(),
                        null, implementing, defs));
        attach(result, dc);
        return result;
    }

    List<JCTree> enumBody(Name enumName) {
        accept(LBRACE);
        ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
        if (token.kind == COMMA) {
            nextToken();
        } else if (token.kind != RBRACE && token.kind != SEMI) {
            defs.append(enumeratorDeclaration(enumName));
            while (token.kind == COMMA) {
                nextToken();
                if (token.kind == RBRACE || token.kind == SEMI) break;
                defs.append(enumeratorDeclaration(enumName));
            }
            if (token.kind != SEMI && token.kind != RBRACE) {
                defs.append(syntaxError(token.pos, "expected3",
                        COMMA, RBRACE, SEMI));
                nextToken();
            }
        }
        if (token.kind == SEMI) {
            nextToken();
            while (token.kind != RBRACE && token.kind != EOF) {
                defs.appendList(classOrInterfaceBodyDeclaration(enumName,
                        false));
                if (token.pos <= endPosTable.errorEndPos) {

                    skip(false, true, true, false);
                }
            }
        }
        accept(RBRACE);
        return defs.toList();
    }

    JCTree enumeratorDeclaration(Name enumName) {
        Comment dc = token.comment(CommentStyle.JAVADOC);
        int flags = Flags.PUBLIC | Flags.STATIC | Flags.FINAL | Flags.ENUM;
        if (token.deprecatedFlag()) {
            flags |= Flags.DEPRECATED;
        }
        int pos = token.pos;
        List<JCAnnotation> annotations = annotationsOpt(Tag.ANNOTATION);
        JCModifiers mods = F.at(annotations.isEmpty() ? Position.NOPOS : pos).Modifiers(flags, annotations);
        List<JCExpression> typeArgs = typeArgumentsOpt();
        int identPos = token.pos;
        Name name = ident();
        int createPos = token.pos;
        List<JCExpression> args = (token.kind == LPAREN)
                ? arguments() : List.nil();
        JCClassDecl body = null;
        if (token.kind == LBRACE) {
            JCModifiers mods1 = F.at(Position.NOPOS).Modifiers(Flags.ENUM | Flags.STATIC);
            List<JCTree> defs = classOrInterfaceBody(names.empty, false);
            body = toP(F.at(identPos).AnonymousClassDef(mods1, defs));
        }
        if (args.isEmpty() && body == null)
            createPos = identPos;
        JCIdent ident = F.at(identPos).Ident(enumName);
        JCNewClass create = F.at(createPos).NewClass(null, typeArgs, ident, args, body);
        if (createPos != identPos)
            storeEnd(create, S.prevToken().endPos);
        ident = F.at(identPos).Ident(enumName);
        JCTree result = toP(F.at(pos).VarDef(mods, name, ident, create));
        attach(result, dc);
        return result;
    }

    List<JCExpression> typeList() {
        ListBuffer<JCExpression> ts = new ListBuffer<JCExpression>();
        ts.append(parseType());
        while (token.kind == COMMA) {
            nextToken();
            ts.append(parseType());
        }
        return ts.toList();
    }

    List<JCTree> classOrInterfaceBody(Name className, boolean isInterface) {
        accept(LBRACE);
        if (token.pos <= endPosTable.errorEndPos) {

            skip(false, true, false, false);
            if (token.kind == LBRACE)
                nextToken();
        }
        ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
        while (token.kind != RBRACE && token.kind != EOF) {
            defs.appendList(classOrInterfaceBodyDeclaration(className, isInterface));
            if (token.pos <= endPosTable.errorEndPos) {

                skip(false, true, true, false);
            }
        }
        accept(RBRACE);
        return defs.toList();
    }

    protected List<JCTree> classOrInterfaceBodyDeclaration(Name className, boolean isInterface) {
        if (token.kind == SEMI) {
            nextToken();
            return List.nil();
        } else {
            Comment dc = token.comment(CommentStyle.JAVADOC);
            int pos = token.pos;
            JCModifiers mods = modifiersOpt();
            if (token.kind == CLASS ||
                    token.kind == INTERFACE ||
                    allowEnums && token.kind == ENUM) {
                return List.of(classOrInterfaceOrEnumDeclaration(mods, dc));
            } else if (token.kind == LBRACE && !isInterface &&
                    (mods.flags & Flags.StandardFlags & ~Flags.STATIC) == 0 &&
                    mods.annotations.isEmpty()) {
                return List.of(block(pos, mods.flags));
            } else {
                pos = token.pos;
                List<JCTypeParameter> typarams = typeParametersOpt();


                if (typarams.nonEmpty() && mods.pos == Position.NOPOS) {
                    mods.pos = pos;
                    storeEnd(mods, pos);
                }
                List<JCAnnotation> annosAfterParams = annotationsOpt(Tag.ANNOTATION);
                Token tk = token;
                pos = token.pos;
                JCExpression type;
                boolean isVoid = token.kind == VOID;
                if (isVoid) {
                    if (annosAfterParams.nonEmpty())
                        illegal(annosAfterParams.head.pos);
                    type = to(F.at(pos).TypeIdent(TypeTag.VOID));
                    nextToken();
                } else {
                    if (annosAfterParams.nonEmpty()) {
                        mods.annotations = mods.annotations.appendList(annosAfterParams);
                        if (mods.pos == Position.NOPOS)
                            mods.pos = mods.annotations.head.pos;
                    }

                    type = unannotatedType();
                }
                if (token.kind == LPAREN && !isInterface && type.hasTag(IDENT)) {
                    if (isInterface || tk.name() != className)
                        error(pos, "invalid.meth.decl.ret.type.req");
                    return List.of(methodDeclaratorRest(
                            pos, mods, null, names.init, typarams,
                            isInterface, true, dc));
                } else {
                    pos = token.pos;
                    Name name = ident();
                    if (token.kind == LPAREN) {
                        return List.of(methodDeclaratorRest(
                                pos, mods, type, name, typarams,
                                isInterface, isVoid, dc));
                    } else if (!isVoid && typarams.isEmpty()) {
                        List<JCTree> defs =
                                variableDeclaratorsRest(pos, mods, type, name, isInterface, dc,
                                        new ListBuffer<JCTree>()).toList();
                        storeEnd(defs.last(), token.endPos);
                        accept(SEMI);
                        return defs;
                    } else {
                        pos = token.pos;
                        List<JCTree> err = isVoid
                                ? List.of(toP(F.at(pos).MethodDef(mods, name, type, typarams,
                                List.nil(), List.nil(), null, null)))
                                : null;
                        return List.of(syntaxError(token.pos, err, "expected", LPAREN));
                    }
                }
            }
        }
    }

    protected JCTree methodDeclaratorRest(int pos,
                                          JCModifiers mods,
                                          JCExpression type,
                                          Name name,
                                          List<JCTypeParameter> typarams,
                                          boolean isInterface, boolean isVoid,
                                          Comment dc) {
        if (isInterface && (mods.flags & Flags.STATIC) != 0) {
            checkStaticInterfaceMethods();
        }
        JCVariableDecl prevReceiverParam = this.receiverParam;
        try {
            this.receiverParam = null;

            List<JCVariableDecl> params = formalParameters();
            if (!isVoid) type = bracketsOpt(type);
            List<JCExpression> thrown = List.nil();
            if (token.kind == THROWS) {
                nextToken();
                thrown = qualidentList();
            }
            JCBlock body = null;
            JCExpression defaultValue;
            if (token.kind == LBRACE) {
                body = block();
                defaultValue = null;
            } else {
                if (token.kind == DEFAULT) {
                    accept(DEFAULT);
                    defaultValue = annotationValue();
                } else {
                    defaultValue = null;
                }
                accept(SEMI);
                if (token.pos <= endPosTable.errorEndPos) {

                    skip(false, true, false, false);
                    if (token.kind == LBRACE) {
                        body = block();
                    }
                }
            }
            JCMethodDecl result =
                    toP(F.at(pos).MethodDef(mods, name, type, typarams,
                            receiverParam, params, thrown,
                            body, defaultValue));
            attach(result, dc);
            return result;
        } finally {
            this.receiverParam = prevReceiverParam;
        }
    }

    List<JCExpression> qualidentList() {
        ListBuffer<JCExpression> ts = new ListBuffer<JCExpression>();
        List<JCAnnotation> typeAnnos = typeAnnotationsOpt();
        JCExpression qi = qualident(true);
        if (!typeAnnos.isEmpty()) {
            JCExpression at = insertAnnotationsToMostInner(qi, typeAnnos, false);
            ts.append(at);
        } else {
            ts.append(qi);
        }
        while (token.kind == COMMA) {
            nextToken();
            typeAnnos = typeAnnotationsOpt();
            qi = qualident(true);
            if (!typeAnnos.isEmpty()) {
                JCExpression at = insertAnnotationsToMostInner(qi, typeAnnos, false);
                ts.append(at);
            } else {
                ts.append(qi);
            }
        }
        return ts.toList();
    }

    List<JCTypeParameter> typeParametersOpt() {
        if (token.kind == LT) {
            checkGenerics();
            ListBuffer<JCTypeParameter> typarams = new ListBuffer<JCTypeParameter>();
            nextToken();
            typarams.append(typeParameter());
            while (token.kind == COMMA) {
                nextToken();
                typarams.append(typeParameter());
            }
            accept(GT);
            return typarams.toList();
        } else {
            return List.nil();
        }
    }

    JCTypeParameter typeParameter() {
        int pos = token.pos;
        List<JCAnnotation> annos = typeAnnotationsOpt();
        Name name = ident();
        ListBuffer<JCExpression> bounds = new ListBuffer<JCExpression>();
        if (token.kind == EXTENDS) {
            nextToken();
            bounds.append(parseType());
            while (token.kind == AMP) {
                nextToken();
                bounds.append(parseType());
            }
        }
        return toP(F.at(pos).TypeParameter(name, bounds.toList(), annos));
    }

    List<JCVariableDecl> formalParameters() {
        return formalParameters(false);
    }

    List<JCVariableDecl> formalParameters(boolean lambdaParameters) {
        ListBuffer<JCVariableDecl> params = new ListBuffer<JCVariableDecl>();
        JCVariableDecl lastParam;
        accept(LPAREN);
        if (token.kind != RPAREN) {
            this.allowThisIdent = true;
            lastParam = formalParameter(lambdaParameters);
            if (lastParam.nameexpr != null) {
                this.receiverParam = lastParam;
            } else {
                params.append(lastParam);
            }
            this.allowThisIdent = false;
            while ((lastParam.mods.flags & Flags.VARARGS) == 0 && token.kind == COMMA) {
                nextToken();
                params.append(lastParam = formalParameter(lambdaParameters));
            }
        }
        accept(RPAREN);
        return params.toList();
    }

    List<JCVariableDecl> implicitParameters(boolean hasParens) {
        if (hasParens) {
            accept(LPAREN);
        }
        ListBuffer<JCVariableDecl> params = new ListBuffer<JCVariableDecl>();
        if (token.kind != RPAREN && token.kind != ARROW) {
            params.append(implicitParameter());
            while (token.kind == COMMA) {
                nextToken();
                params.append(implicitParameter());
            }
        }
        if (hasParens) {
            accept(RPAREN);
        }
        return params.toList();
    }

    JCModifiers optFinal(long flags) {
        JCModifiers mods = modifiersOpt();
        checkNoMods(mods.flags & ~(Flags.FINAL | Flags.DEPRECATED));
        mods.flags |= flags;
        return mods;
    }

    private JCExpression insertAnnotationsToMostInner(
            JCExpression type, List<JCAnnotation> annos,
            boolean createNewLevel) {
        int origEndPos = getEndPos(type);
        JCExpression mostInnerType = type;
        JCArrayTypeTree mostInnerArrayType = null;
        while (TreeInfo.typeIn(mostInnerType).hasTag(TYPEARRAY)) {
            mostInnerArrayType = (JCArrayTypeTree) TreeInfo.typeIn(mostInnerType);
            mostInnerType = mostInnerArrayType.elemtype;
        }
        if (createNewLevel) {
            mostInnerType = to(F.at(token.pos).TypeArray(mostInnerType));
        }
        JCExpression mostInnerTypeToReturn = mostInnerType;
        if (annos.nonEmpty()) {
            JCExpression lastToModify = mostInnerType;
            while (TreeInfo.typeIn(mostInnerType).hasTag(SELECT) ||
                    TreeInfo.typeIn(mostInnerType).hasTag(TYPEAPPLY)) {
                while (TreeInfo.typeIn(mostInnerType).hasTag(SELECT)) {
                    lastToModify = mostInnerType;
                    mostInnerType = ((JCFieldAccess) TreeInfo.typeIn(mostInnerType)).getExpression();
                }
                while (TreeInfo.typeIn(mostInnerType).hasTag(TYPEAPPLY)) {
                    lastToModify = mostInnerType;
                    mostInnerType = ((JCTypeApply) TreeInfo.typeIn(mostInnerType)).clazz;
                }
            }
            mostInnerType = F.at(annos.head.pos).AnnotatedType(annos, mostInnerType);
            if (TreeInfo.typeIn(lastToModify).hasTag(TYPEAPPLY)) {
                ((JCTypeApply) TreeInfo.typeIn(lastToModify)).clazz = mostInnerType;
            } else if (TreeInfo.typeIn(lastToModify).hasTag(SELECT)) {
                ((JCFieldAccess) TreeInfo.typeIn(lastToModify)).selected = mostInnerType;
            } else {

                mostInnerTypeToReturn = mostInnerType;
            }
        }
        if (mostInnerArrayType == null) {
            return mostInnerTypeToReturn;
        } else {
            mostInnerArrayType.elemtype = mostInnerTypeToReturn;
            storeEnd(type, origEndPos);
            return type;
        }
    }

    protected JCVariableDecl formalParameter() {
        return formalParameter(false);
    }

    protected JCVariableDecl formalParameter(boolean lambdaParameter) {
        JCModifiers mods = optFinal(Flags.PARAMETER);


        this.permitTypeAnnotationsPushBack = true;
        JCExpression type = parseType();
        this.permitTypeAnnotationsPushBack = false;
        if (token.kind == ELLIPSIS) {
            List<JCAnnotation> varargsAnnos = typeAnnotationsPushedBack;
            typeAnnotationsPushedBack = List.nil();
            checkVarargs();
            mods.flags |= Flags.VARARGS;

            type = insertAnnotationsToMostInner(type, varargsAnnos, true);
            nextToken();
        } else {

            if (typeAnnotationsPushedBack.nonEmpty()) {
                reportSyntaxError(typeAnnotationsPushedBack.head.pos,
                        "illegal.start.of.type");
            }
            typeAnnotationsPushedBack = List.nil();
        }
        return variableDeclaratorId(mods, type, lambdaParameter);
    }

    protected JCVariableDecl implicitParameter() {
        JCModifiers mods = F.at(token.pos).Modifiers(Flags.PARAMETER);
        return variableDeclaratorId(mods, null, true);
    }

    void error(int pos, String key, Object... args) {
        log.error(DiagnosticFlag.SYNTAX, pos, key, args);
    }

    void error(DiagnosticPosition pos, String key, Object... args) {
        log.error(DiagnosticFlag.SYNTAX, pos, key, args);
    }

    void warning(int pos, String key, Object... args) {
        log.warning(pos, key, args);
    }

    protected JCExpression checkExprStat(JCExpression t) {
        if (!TreeInfo.isExpressionStatement(t)) {
            JCExpression ret = F.at(t.pos).Erroneous(List.<JCTree>of(t));
            error(ret, "not.stmt");
            return ret;
        } else {
            return t;
        }
    }

    void checkGenerics() {
        if (!allowGenerics) {
            error(token.pos, "generics.not.supported.in.source", source.name);
            allowGenerics = true;
        }
    }

    void checkVarargs() {
        if (!allowVarargs) {
            error(token.pos, "varargs.not.supported.in.source", source.name);
            allowVarargs = true;
        }
    }

    void checkForeach() {
        if (!allowForeach) {
            error(token.pos, "foreach.not.supported.in.source", source.name);
            allowForeach = true;
        }
    }

    void checkStaticImports() {
        if (!allowStaticImport) {
            error(token.pos, "static.import.not.supported.in.source", source.name);
            allowStaticImport = true;
        }
    }

    void checkAnnotations() {
        if (!allowAnnotations) {
            error(token.pos, "annotations.not.supported.in.source", source.name);
            allowAnnotations = true;
        }
    }

    void checkDiamond() {
        if (!allowDiamond) {
            error(token.pos, "diamond.not.supported.in.source", source.name);
            allowDiamond = true;
        }
    }

    void checkMulticatch() {
        if (!allowMulticatch) {
            error(token.pos, "multicatch.not.supported.in.source", source.name);
            allowMulticatch = true;
        }
    }

    void checkTryWithResources() {
        if (!allowTWR) {
            error(token.pos, "try.with.resources.not.supported.in.source", source.name);
            allowTWR = true;
        }
    }

    void checkLambda() {
        if (!allowLambda) {
            log.error(token.pos, "lambda.not.supported.in.source", source.name);
            allowLambda = true;
        }
    }

    void checkMethodReferences() {
        if (!allowMethodReferences) {
            log.error(token.pos, "method.references.not.supported.in.source", source.name);
            allowMethodReferences = true;
        }
    }

    void checkDefaultMethods() {
        if (!allowDefaultMethods) {
            log.error(token.pos, "default.methods.not.supported.in.source", source.name);
            allowDefaultMethods = true;
        }
    }

    void checkIntersectionTypesInCast() {
        if (!allowIntersectionTypesInCast) {
            log.error(token.pos, "intersection.types.in.cast.not.supported.in.source", source.name);
            allowIntersectionTypesInCast = true;
        }
    }

    void checkStaticInterfaceMethods() {
        if (!allowStaticInterfaceMethods) {
            log.error(token.pos, "static.intf.methods.not.supported.in.source", source.name);
            allowStaticInterfaceMethods = true;
        }
    }

    void checkTypeAnnotations() {
        if (!allowTypeAnnotations) {
            log.error(token.pos, "type.annotations.not.supported.in.source", source.name);
            allowTypeAnnotations = true;
        }
    }

    enum BasicErrorRecoveryAction implements ErrorRecoveryAction {
        BLOCK_STMT {
            public JCTree doRecover(JavacParser parser) {
                return parser.parseStatementAsBlock();
            }
        },
        CATCH_CLAUSE {
            public JCTree doRecover(JavacParser parser) {
                return parser.catchClause();
            }
        }
    }

    enum ParensResult {
        CAST,
        EXPLICIT_LAMBDA,
        IMPLICIT_LAMBDA,
        PARENS
    }

    interface ErrorRecoveryAction {
        JCTree doRecover(JavacParser parser);
    }

    protected static class SimpleEndPosTable extends AbstractEndPosTable {
        private final Map<JCTree, Integer> endPosMap;

        SimpleEndPosTable(JavacParser parser) {
            super(parser);
            endPosMap = new HashMap<JCTree, Integer>();
        }

        public void storeEnd(JCTree tree, int endpos) {
            endPosMap.put(tree, errorEndPos > endpos ? errorEndPos : endpos);
        }

        protected <T extends JCTree> T to(T t) {
            storeEnd(t, parser.token.endPos);
            return t;
        }

        protected <T extends JCTree> T toP(T t) {
            storeEnd(t, parser.S.prevToken().endPos);
            return t;
        }

        public int getEndPos(JCTree tree) {
            Integer value = endPosMap.get(tree);
            return (value == null) ? Position.NOPOS : value;
        }

        public int replaceTree(JCTree oldTree, JCTree newTree) {
            Integer pos = endPosMap.remove(oldTree);
            if (pos != null) {
                endPosMap.put(newTree, pos);
                return pos;
            }
            return Position.NOPOS;
        }
    }

    protected static class EmptyEndPosTable extends AbstractEndPosTable {
        EmptyEndPosTable(JavacParser parser) {
            super(parser);
        }

        public void storeEnd(JCTree tree, int endpos) {
        }

        protected <T extends JCTree> T to(T t) {
            return t;
        }

        protected <T extends JCTree> T toP(T t) {
            return t;
        }

        public int getEndPos(JCTree tree) {
            return Position.NOPOS;
        }

        public int replaceTree(JCTree oldTree, JCTree newTree) {
            return Position.NOPOS;
        }
    }

    protected static abstract class AbstractEndPosTable implements EndPosTable {

        protected JavacParser parser;

        protected int errorEndPos;

        public AbstractEndPosTable(JavacParser parser) {
            this.parser = parser;
        }

        protected abstract <T extends JCTree> T to(T t);

        protected abstract <T extends JCTree> T toP(T t);

        protected void setErrorEndPos(int errPos) {
            if (errPos > errorEndPos) {
                errorEndPos = errPos;
            }
        }

        protected void setParser(JavacParser parser) {
            this.parser = parser;
        }
    }
}
