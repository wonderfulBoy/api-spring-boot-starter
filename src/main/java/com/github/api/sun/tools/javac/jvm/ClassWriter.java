package com.github.api.sun.tools.javac.jvm;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Attribute.RetentionPolicy;
import com.github.api.sun.tools.javac.code.Symbol.*;
import com.github.api.sun.tools.javac.code.Type.MethodType;
import com.github.api.sun.tools.javac.code.Types.UniqueType;
import com.github.api.sun.tools.javac.file.BaseFileObject;
import com.github.api.sun.tools.javac.jvm.Pool.DynamicMethod;
import com.github.api.sun.tools.javac.jvm.Pool.Method;
import com.github.api.sun.tools.javac.jvm.Pool.MethodHandle;
import com.github.api.sun.tools.javac.jvm.Pool.Variable;
import com.github.api.sun.tools.javac.util.*;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.Kinds.*;
import static com.github.api.sun.tools.javac.code.TypeTag.*;
import static com.github.api.sun.tools.javac.main.Option.*;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

public class ClassWriter extends ClassFile {
    protected static final Context.Key<ClassWriter> classWriterKey =
            new Context.Key<ClassWriter>();
    static final int DATA_BUF_SIZE = 0x0fff0;
    static final int POOL_BUF_SIZE = 0x1fff0;
    static final int SAME_FRAME_SIZE = 64;
    static final int SAME_LOCALS_1_STACK_ITEM_EXTENDED = 247;
    static final int SAME_FRAME_EXTENDED = 251;
    static final int FULL_FRAME = 255;
    static final int MAX_LOCAL_LENGTH_DIFF = 4;
    private final static String[] flagName = {
            "PUBLIC", "PRIVATE", "PROTECTED", "STATIC", "FINAL",
            "SUPER", "VOLATILE", "TRANSIENT", "NATIVE", "INTERFACE",
            "ABSTRACT", "STRICTFP"};
    private final Options options;
    private final Log log;
    private final Names names;
    private final JavaFileManager fileManager;
    private final CWSignatureGenerator signatureGen;
    private final boolean dumpClassModifiers;
    private final boolean dumpFieldModifiers;
    private final boolean dumpInnerClassModifiers;
    private final boolean dumpMethodModifiers;
    boolean debugstackmap;
    ByteBuffer databuf = new ByteBuffer(DATA_BUF_SIZE);
    ByteBuffer poolbuf = new ByteBuffer(POOL_BUF_SIZE);
    Pool pool;
    Set<ClassSymbol> innerClasses;
    ListBuffer<ClassSymbol> innerClassesQueue;
    Map<DynamicMethod, MethodHandle> bootstrapMethods;
    AttributeWriter awriter = new AttributeWriter();
    private boolean verbose;
    private boolean scramble;
    private boolean scrambleAll;
    private boolean retrofit;
    private boolean emitSourceFile;
    private boolean genCrt;
    private Target target;
    private Source source;
    private Types types;

    protected ClassWriter(Context context) {
        context.put(classWriterKey, this);
        log = Log.instance(context);
        names = Names.instance(context);
        options = Options.instance(context);
        target = Target.instance(context);
        source = Source.instance(context);
        types = Types.instance(context);
        fileManager = context.get(JavaFileManager.class);
        signatureGen = new CWSignatureGenerator(types);
        verbose = options.isSet(VERBOSE);
        scramble = options.isSet("-scramble");
        scrambleAll = options.isSet("-scrambleAll");
        retrofit = options.isSet("-retrofit");
        genCrt = options.isSet(XJCOV);
        debugstackmap = options.isSet("debugstackmap");
        emitSourceFile = options.isUnset(G_CUSTOM) ||
                options.isSet(G_CUSTOM, "source");
        String dumpModFlags = options.get("dumpmodifiers");
        dumpClassModifiers =
                (dumpModFlags != null && dumpModFlags.indexOf('c') != -1);
        dumpFieldModifiers =
                (dumpModFlags != null && dumpModFlags.indexOf('f') != -1);
        dumpInnerClassModifiers =
                (dumpModFlags != null && dumpModFlags.indexOf('i') != -1);
        dumpMethodModifiers =
                (dumpModFlags != null && dumpModFlags.indexOf('m') != -1);
    }

    public static ClassWriter instance(Context context) {
        ClassWriter instance = context.get(classWriterKey);
        if (instance == null)
            instance = new ClassWriter(context);
        return instance;
    }

    public static String flagNames(long flags) {
        StringBuilder sbuf = new StringBuilder();
        int i = 0;
        long f = flags & StandardFlags;
        while (f != 0) {
            if ((f & 1) != 0) {
                sbuf.append(" ");
                sbuf.append(flagName[i]);
            }
            f = f >> 1;
            i++;
        }
        return sbuf.toString();
    }

    void putChar(ByteBuffer buf, int op, int x) {
        buf.elems[op] = (byte) ((x >> 8) & 0xFF);
        buf.elems[op + 1] = (byte) ((x) & 0xFF);
    }

    void putInt(ByteBuffer buf, int adr, int x) {
        buf.elems[adr] = (byte) ((x >> 24) & 0xFF);
        buf.elems[adr + 1] = (byte) ((x >> 16) & 0xFF);
        buf.elems[adr + 2] = (byte) ((x >> 8) & 0xFF);
        buf.elems[adr + 3] = (byte) ((x) & 0xFF);
    }

    Name typeSig(Type type) {
        Assert.check(signatureGen.isEmpty());

        signatureGen.assembleSig(type);
        Name n = signatureGen.toName();
        signatureGen.reset();

        return n;
    }

    public Name xClassName(Type t) {
        if (t.hasTag(CLASS)) {
            return names.fromUtf(externalize(t.tsym.flatName()));
        } else if (t.hasTag(ARRAY)) {
            return typeSig(types.erasure(t));
        } else {
            throw new AssertionError("xClassName");
        }
    }

    void writePool(Pool pool) throws PoolOverflow, StringOverflow {
        int poolCountIdx = poolbuf.length;
        poolbuf.appendChar(0);
        int i = 1;
        while (i < pool.pp) {
            Object value = pool.pool[i];
            Assert.checkNonNull(value);
            if (value instanceof Method || value instanceof Variable)
                value = ((DelegatedSymbol) value).getUnderlyingSymbol();
            if (value instanceof MethodSymbol) {
                MethodSymbol m = (MethodSymbol) value;
                if (!m.isDynamic()) {
                    poolbuf.appendByte((m.owner.flags() & INTERFACE) != 0
                            ? CONSTANT_InterfaceMethodref
                            : CONSTANT_Methodref);
                    poolbuf.appendChar(pool.put(m.owner));
                    poolbuf.appendChar(pool.put(nameType(m)));
                } else {

                    DynamicMethodSymbol dynSym = (DynamicMethodSymbol) m;
                    MethodHandle handle = new MethodHandle(dynSym.bsmKind, dynSym.bsm, types);
                    DynamicMethod dynMeth = new DynamicMethod(dynSym, types);
                    bootstrapMethods.put(dynMeth, handle);

                    pool.put(names.BootstrapMethods);
                    pool.put(handle);
                    for (Object staticArg : dynSym.staticArgs) {
                        pool.put(staticArg);
                    }
                    poolbuf.appendByte(CONSTANT_InvokeDynamic);
                    poolbuf.appendChar(bootstrapMethods.size() - 1);
                    poolbuf.appendChar(pool.put(nameType(dynSym)));
                }
            } else if (value instanceof VarSymbol) {
                VarSymbol v = (VarSymbol) value;
                poolbuf.appendByte(CONSTANT_Fieldref);
                poolbuf.appendChar(pool.put(v.owner));
                poolbuf.appendChar(pool.put(nameType(v)));
            } else if (value instanceof Name) {
                poolbuf.appendByte(CONSTANT_Utf8);
                byte[] bs = ((Name) value).toUtf();
                poolbuf.appendChar(bs.length);
                poolbuf.appendBytes(bs, 0, bs.length);
                if (bs.length > Pool.MAX_STRING_LENGTH)
                    throw new StringOverflow(value.toString());
            } else if (value instanceof ClassSymbol) {
                ClassSymbol c = (ClassSymbol) value;
                if (c.owner.kind == TYP) pool.put(c.owner);
                poolbuf.appendByte(CONSTANT_Class);
                if (c.type.hasTag(ARRAY)) {
                    poolbuf.appendChar(pool.put(typeSig(c.type)));
                } else {
                    poolbuf.appendChar(pool.put(names.fromUtf(externalize(c.flatname))));
                    enterInner(c);
                }
            } else if (value instanceof NameAndType) {
                NameAndType nt = (NameAndType) value;
                poolbuf.appendByte(CONSTANT_NameandType);
                poolbuf.appendChar(pool.put(nt.name));
                poolbuf.appendChar(pool.put(typeSig(nt.uniqueType.type)));
            } else if (value instanceof Integer) {
                poolbuf.appendByte(CONSTANT_Integer);
                poolbuf.appendInt(((Integer) value).intValue());
            } else if (value instanceof Long) {
                poolbuf.appendByte(CONSTANT_Long);
                poolbuf.appendLong(((Long) value).longValue());
                i++;
            } else if (value instanceof Float) {
                poolbuf.appendByte(CONSTANT_Float);
                poolbuf.appendFloat(((Float) value).floatValue());
            } else if (value instanceof Double) {
                poolbuf.appendByte(CONSTANT_Double);
                poolbuf.appendDouble(((Double) value).doubleValue());
                i++;
            } else if (value instanceof String) {
                poolbuf.appendByte(CONSTANT_String);
                poolbuf.appendChar(pool.put(names.fromString((String) value)));
            } else if (value instanceof UniqueType) {
                Type type = ((UniqueType) value).type;
                if (type instanceof MethodType) {
                    poolbuf.appendByte(CONSTANT_MethodType);
                    poolbuf.appendChar(pool.put(typeSig(type)));
                } else {
                    if (type.hasTag(CLASS)) enterInner((ClassSymbol) type.tsym);
                    poolbuf.appendByte(CONSTANT_Class);
                    poolbuf.appendChar(pool.put(xClassName(type)));
                }
            } else if (value instanceof MethodHandle) {
                MethodHandle ref = (MethodHandle) value;
                poolbuf.appendByte(CONSTANT_MethodHandle);
                poolbuf.appendByte(ref.refKind);
                poolbuf.appendChar(pool.put(ref.refSym));
            } else {
                Assert.error("writePool " + value);
            }
            i++;
        }
        if (pool.pp > Pool.MAX_ENTRIES)
            throw new PoolOverflow();
        putChar(poolbuf, poolCountIdx, pool.pp);
    }

    Name fieldName(Symbol sym) {
        if (scramble && (sym.flags() & PRIVATE) != 0 ||
                scrambleAll && (sym.flags() & (PROTECTED | PUBLIC)) == 0)
            return names.fromString("_$" + sym.name.getIndex());
        else
            return sym.name;
    }

    NameAndType nameType(Symbol sym) {
        return new NameAndType(fieldName(sym),
                retrofit
                        ? sym.erasure(types)
                        : sym.externalType(types), types);


    }

    int writeAttr(Name attrName) {
        databuf.appendChar(pool.put(attrName));
        databuf.appendInt(0);
        return databuf.length;
    }

    void endAttr(int index) {
        putInt(databuf, index - 4, databuf.length - index);
    }

    int beginAttrs() {
        databuf.appendChar(0);
        return databuf.length;
    }

    void endAttrs(int index, int count) {
        putChar(databuf, index - 2, count);
    }

    int writeEnclosingMethodAttribute(ClassSymbol c) {
        if (!target.hasEnclosingMethodAttribute())
            return 0;
        return writeEnclosingMethodAttribute(names.EnclosingMethod, c);
    }

    protected int writeEnclosingMethodAttribute(Name attributeName, ClassSymbol c) {
        if (c.owner.kind != MTH &&
                c.name != names.empty)
            return 0;
        int alenIdx = writeAttr(attributeName);
        ClassSymbol enclClass = c.owner.enclClass();
        MethodSymbol enclMethod =
                (c.owner.type == null
                        || c.owner.kind != MTH)
                        ? null
                        : (MethodSymbol) c.owner;
        databuf.appendChar(pool.put(enclClass));
        databuf.appendChar(enclMethod == null ? 0 : pool.put(nameType(c.owner)));
        endAttr(alenIdx);
        return 1;
    }

    int writeFlagAttrs(long flags) {
        int acount = 0;
        if ((flags & DEPRECATED) != 0) {
            int alenIdx = writeAttr(names.Deprecated);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & ENUM) != 0 && !target.useEnumFlag()) {
            int alenIdx = writeAttr(names.Enum);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & SYNTHETIC) != 0 && !target.useSyntheticFlag()) {
            int alenIdx = writeAttr(names.Synthetic);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & BRIDGE) != 0 && !target.useBridgeFlag()) {
            int alenIdx = writeAttr(names.Bridge);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & VARARGS) != 0 && !target.useVarargsFlag()) {
            int alenIdx = writeAttr(names.Varargs);
            endAttr(alenIdx);
            acount++;
        }
        if ((flags & ANNOTATION) != 0 && !target.useAnnotationFlag()) {
            int alenIdx = writeAttr(names.Annotation);
            endAttr(alenIdx);
            acount++;
        }
        return acount;
    }

    int writeMemberAttrs(Symbol sym) {
        int acount = writeFlagAttrs(sym.flags());
        long flags = sym.flags();
        if (source.allowGenerics() &&
                (flags & (SYNTHETIC | BRIDGE)) != SYNTHETIC &&
                (flags & ANONCONSTR) == 0 &&
                (!types.isSameType(sym.type, sym.erasure(types)) ||
                        signatureGen.hasTypeVar(sym.type.getThrownTypes()))) {


            int alenIdx = writeAttr(names.Signature);
            databuf.appendChar(pool.put(typeSig(sym.type)));
            endAttr(alenIdx);
            acount++;
        }
        acount += writeJavaAnnotations(sym.getRawAttributes());
        acount += writeTypeAnnotations(sym.getRawTypeAttributes(), false);
        return acount;
    }

    int writeMethodParametersAttr(MethodSymbol m) {
        MethodType ty = m.externalType(types).asMethodType();
        final int allparams = ty.argtypes.size();
        if (m.params != null && allparams != 0) {
            final int attrIndex = writeAttr(names.MethodParameters);
            databuf.appendByte(allparams);

            for (VarSymbol s : m.extraParams) {
                final int flags =
                        ((int) s.flags() & (FINAL | SYNTHETIC | MANDATED)) |
                                ((int) m.flags() & SYNTHETIC);
                databuf.appendChar(pool.put(s.name));
                databuf.appendChar(flags);
            }

            for (VarSymbol s : m.params) {
                final int flags =
                        ((int) s.flags() & (FINAL | SYNTHETIC | MANDATED)) |
                                ((int) m.flags() & SYNTHETIC);
                databuf.appendChar(pool.put(s.name));
                databuf.appendChar(flags);
            }

            for (VarSymbol s : m.capturedLocals) {
                final int flags =
                        ((int) s.flags() & (FINAL | SYNTHETIC | MANDATED)) |
                                ((int) m.flags() & SYNTHETIC);
                databuf.appendChar(pool.put(s.name));
                databuf.appendChar(flags);
            }
            endAttr(attrIndex);
            return 1;
        } else
            return 0;
    }

    int writeParameterAttrs(MethodSymbol m) {
        boolean hasVisible = false;
        boolean hasInvisible = false;
        if (m.params != null) {
            for (VarSymbol s : m.params) {
                for (Attribute.Compound a : s.getRawAttributes()) {
                    switch (types.getRetention(a)) {
                        case SOURCE:
                            break;
                        case CLASS:
                            hasInvisible = true;
                            break;
                        case RUNTIME:
                            hasVisible = true;
                            break;
                        default:
                    }
                }
            }
        }
        int attrCount = 0;
        if (hasVisible) {
            int attrIndex = writeAttr(names.RuntimeVisibleParameterAnnotations);
            databuf.appendByte(m.params.length());
            for (VarSymbol s : m.params) {
                ListBuffer<Attribute.Compound> buf = new ListBuffer<Attribute.Compound>();
                for (Attribute.Compound a : s.getRawAttributes())
                    if (types.getRetention(a) == RetentionPolicy.RUNTIME)
                        buf.append(a);
                databuf.appendChar(buf.length());
                for (Attribute.Compound a : buf)
                    writeCompoundAttribute(a);
            }
            endAttr(attrIndex);
            attrCount++;
        }
        if (hasInvisible) {
            int attrIndex = writeAttr(names.RuntimeInvisibleParameterAnnotations);
            databuf.appendByte(m.params.length());
            for (VarSymbol s : m.params) {
                ListBuffer<Attribute.Compound> buf = new ListBuffer<Attribute.Compound>();
                for (Attribute.Compound a : s.getRawAttributes())
                    if (types.getRetention(a) == RetentionPolicy.CLASS)
                        buf.append(a);
                databuf.appendChar(buf.length());
                for (Attribute.Compound a : buf)
                    writeCompoundAttribute(a);
            }
            endAttr(attrIndex);
            attrCount++;
        }
        return attrCount;
    }

    int writeJavaAnnotations(List<Attribute.Compound> attrs) {
        if (attrs.isEmpty()) return 0;
        ListBuffer<Attribute.Compound> visibles = new ListBuffer<Attribute.Compound>();
        ListBuffer<Attribute.Compound> invisibles = new ListBuffer<Attribute.Compound>();
        for (Attribute.Compound a : attrs) {
            switch (types.getRetention(a)) {
                case SOURCE:
                    break;
                case CLASS:
                    invisibles.append(a);
                    break;
                case RUNTIME:
                    visibles.append(a);
                    break;
                default:
            }
        }
        int attrCount = 0;
        if (visibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeVisibleAnnotations);
            databuf.appendChar(visibles.length());
            for (Attribute.Compound a : visibles)
                writeCompoundAttribute(a);
            endAttr(attrIndex);
            attrCount++;
        }
        if (invisibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeInvisibleAnnotations);
            databuf.appendChar(invisibles.length());
            for (Attribute.Compound a : invisibles)
                writeCompoundAttribute(a);
            endAttr(attrIndex);
            attrCount++;
        }
        return attrCount;
    }

    int writeTypeAnnotations(List<Attribute.TypeCompound> typeAnnos, boolean inCode) {
        if (typeAnnos.isEmpty()) return 0;
        ListBuffer<Attribute.TypeCompound> visibles = new ListBuffer<>();
        ListBuffer<Attribute.TypeCompound> invisibles = new ListBuffer<>();
        for (Attribute.TypeCompound tc : typeAnnos) {
            if (tc.hasUnknownPosition()) {
                boolean fixed = tc.tryFixPosition();

                if (!fixed) {


                    PrintWriter pw = log.getWriter(Log.WriterKind.ERROR);
                    pw.println("ClassWriter: Position UNKNOWN in type annotation: " + tc);
                    continue;
                }
            }
            if (tc.position.type.isLocal() != inCode)
                continue;
            if (!tc.position.emitToClassfile())
                continue;
            switch (types.getRetention(tc)) {
                case SOURCE:
                    break;
                case CLASS:
                    invisibles.append(tc);
                    break;
                case RUNTIME:
                    visibles.append(tc);
                    break;
                default:
            }
        }
        int attrCount = 0;
        if (visibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeVisibleTypeAnnotations);
            databuf.appendChar(visibles.length());
            for (Attribute.TypeCompound p : visibles)
                writeTypeAnnotation(p);
            endAttr(attrIndex);
            attrCount++;
        }
        if (invisibles.length() != 0) {
            int attrIndex = writeAttr(names.RuntimeInvisibleTypeAnnotations);
            databuf.appendChar(invisibles.length());
            for (Attribute.TypeCompound p : invisibles)
                writeTypeAnnotation(p);
            endAttr(attrIndex);
            attrCount++;
        }
        return attrCount;
    }

    void writeCompoundAttribute(Attribute.Compound c) {
        databuf.appendChar(pool.put(typeSig(c.type)));
        databuf.appendChar(c.values.length());
        for (Pair<MethodSymbol, Attribute> p : c.values) {
            databuf.appendChar(pool.put(p.fst.name));
            p.snd.accept(awriter);
        }
    }

    void writeTypeAnnotation(Attribute.TypeCompound c) {
        writePosition(c.position);
        writeCompoundAttribute(c);
    }

    void writePosition(TypeAnnotationPosition p) {
        databuf.appendByte(p.type.targetTypeValue());
        switch (p.type) {

            case INSTANCEOF:

            case NEW:

            case CONSTRUCTOR_REFERENCE:
            case METHOD_REFERENCE:
                databuf.appendChar(p.offset);
                break;

            case LOCAL_VARIABLE:

            case RESOURCE_VARIABLE:
                databuf.appendChar(p.lvarOffset.length);
                for (int i = 0; i < p.lvarOffset.length; ++i) {
                    databuf.appendChar(p.lvarOffset[i]);
                    databuf.appendChar(p.lvarLength[i]);
                    databuf.appendChar(p.lvarIndex[i]);
                }
                break;

            case EXCEPTION_PARAMETER:
                databuf.appendChar(p.exception_index);
                break;

            case METHOD_RECEIVER:

                break;

            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
                databuf.appendByte(p.parameter_index);
                break;

            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND:
                databuf.appendByte(p.parameter_index);
                databuf.appendByte(p.bound_index);
                break;

            case CLASS_EXTENDS:
                databuf.appendChar(p.type_index);
                break;

            case THROWS:
                databuf.appendChar(p.type_index);
                break;

            case METHOD_FORMAL_PARAMETER:
                databuf.appendByte(p.parameter_index);
                break;

            case CAST:

            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case METHOD_INVOCATION_TYPE_ARGUMENT:
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case METHOD_REFERENCE_TYPE_ARGUMENT:
                databuf.appendChar(p.offset);
                databuf.appendByte(p.type_index);
                break;

            case METHOD_RETURN:
            case FIELD:
                break;
            case UNKNOWN:
                throw new AssertionError("jvm.ClassWriter: UNKNOWN target type should never occur!");
            default:
                throw new AssertionError("jvm.ClassWriter: Unknown target type for position: " + p);
        }
        {
            databuf.appendByte(p.location.size());
            java.util.List<Integer> loc = TypeAnnotationPosition.getBinaryFromTypePath(p.location);
            for (int i : loc)
                databuf.appendByte((byte) i);
        }
    }

    void enterInner(ClassSymbol c) {
        if (c.type.isCompound()) {
            throw new AssertionError("Unexpected intersection type: " + c.type);
        }
        try {
            c.complete();
        } catch (CompletionFailure ex) {
            System.err.println("error: " + c + ": " + ex.getMessage());
            throw ex;
        }
        if (!c.type.hasTag(CLASS)) return;
        if (pool != null &&
                c.owner.enclClass() != null &&
                (innerClasses == null || !innerClasses.contains(c))) {
            enterInner(c.owner.enclClass());
            pool.put(c);
            if (c.name != names.empty)
                pool.put(c.name);
            if (innerClasses == null) {
                innerClasses = new HashSet<ClassSymbol>();
                innerClassesQueue = new ListBuffer<ClassSymbol>();
                pool.put(names.InnerClasses);
            }
            innerClasses.add(c);
            innerClassesQueue.append(c);
        }
    }

    void writeInnerClasses() {
        int alenIdx = writeAttr(names.InnerClasses);
        databuf.appendChar(innerClassesQueue.length());
        for (List<ClassSymbol> l = innerClassesQueue.toList();
             l.nonEmpty();
             l = l.tail) {
            ClassSymbol inner = l.head;
            char flags = (char) adjustFlags(inner.flags_field);
            if ((flags & INTERFACE) != 0) flags |= ABSTRACT;
            if (inner.name.isEmpty()) flags &= ~FINAL;
            flags &= ~STRICTFP;
            if (dumpInnerClassModifiers) {
                PrintWriter pw = log.getWriter(Log.WriterKind.ERROR);
                pw.println("INNERCLASS  " + inner.name);
                pw.println("---" + flagNames(flags));
            }
            databuf.appendChar(pool.get(inner));
            databuf.appendChar(
                    inner.owner.kind == TYP ? pool.get(inner.owner) : 0);
            databuf.appendChar(
                    !inner.name.isEmpty() ? pool.get(inner.name) : 0);
            databuf.appendChar(flags);
        }
        endAttr(alenIdx);
    }

    void writeBootstrapMethods() {
        int alenIdx = writeAttr(names.BootstrapMethods);
        databuf.appendChar(bootstrapMethods.size());
        for (Map.Entry<DynamicMethod, MethodHandle> entry : bootstrapMethods.entrySet()) {
            DynamicMethod dmeth = entry.getKey();
            DynamicMethodSymbol dsym = (DynamicMethodSymbol) dmeth.baseSymbol();

            databuf.appendChar(pool.get(entry.getValue()));

            databuf.appendChar(dsym.staticArgs.length);

            Object[] uniqueArgs = dmeth.uniqueStaticArgs;
            for (Object o : uniqueArgs) {
                databuf.appendChar(pool.get(o));
            }
        }
        endAttr(alenIdx);
    }

    void writeField(VarSymbol v) {
        int flags = adjustFlags(v.flags());
        databuf.appendChar(flags);
        if (dumpFieldModifiers) {
            PrintWriter pw = log.getWriter(Log.WriterKind.ERROR);
            pw.println("FIELD  " + fieldName(v));
            pw.println("---" + flagNames(v.flags()));
        }
        databuf.appendChar(pool.put(fieldName(v)));
        databuf.appendChar(pool.put(typeSig(v.erasure(types))));
        int acountIdx = beginAttrs();
        int acount = 0;
        if (v.getConstValue() != null) {
            int alenIdx = writeAttr(names.ConstantValue);
            databuf.appendChar(pool.put(v.getConstValue()));
            endAttr(alenIdx);
            acount++;
        }
        acount += writeMemberAttrs(v);
        endAttrs(acountIdx, acount);
    }

    void writeMethod(MethodSymbol m) {
        int flags = adjustFlags(m.flags());
        databuf.appendChar(flags);
        if (dumpMethodModifiers) {
            PrintWriter pw = log.getWriter(Log.WriterKind.ERROR);
            pw.println("METHOD  " + fieldName(m));
            pw.println("---" + flagNames(m.flags()));
        }
        databuf.appendChar(pool.put(fieldName(m)));
        databuf.appendChar(pool.put(typeSig(m.externalType(types))));
        int acountIdx = beginAttrs();
        int acount = 0;
        if (m.code != null) {
            int alenIdx = writeAttr(names.Code);
            writeCode(m.code);
            m.code = null;
            endAttr(alenIdx);
            acount++;
        }
        List<Type> thrown = m.erasure(types).getThrownTypes();
        if (thrown.nonEmpty()) {
            int alenIdx = writeAttr(names.Exceptions);
            databuf.appendChar(thrown.length());
            for (List<Type> l = thrown; l.nonEmpty(); l = l.tail)
                databuf.appendChar(pool.put(l.head.tsym));
            endAttr(alenIdx);
            acount++;
        }
        if (m.defaultValue != null) {
            int alenIdx = writeAttr(names.AnnotationDefault);
            m.defaultValue.accept(awriter);
            endAttr(alenIdx);
            acount++;
        }
        if (options.isSet(PARAMETERS))
            acount += writeMethodParametersAttr(m);
        acount += writeMemberAttrs(m);
        acount += writeParameterAttrs(m);
        endAttrs(acountIdx, acount);
    }

    void writeCode(Code code) {
        databuf.appendChar(code.max_stack);
        databuf.appendChar(code.max_locals);
        databuf.appendInt(code.cp);
        databuf.appendBytes(code.code, 0, code.cp);
        databuf.appendChar(code.catchInfo.length());
        for (List<char[]> l = code.catchInfo.toList();
             l.nonEmpty();
             l = l.tail) {
            for (int i = 0; i < l.head.length; i++)
                databuf.appendChar(l.head[i]);
        }
        int acountIdx = beginAttrs();
        int acount = 0;
        if (code.lineInfo.nonEmpty()) {
            int alenIdx = writeAttr(names.LineNumberTable);
            databuf.appendChar(code.lineInfo.length());
            for (List<char[]> l = code.lineInfo.reverse();
                 l.nonEmpty();
                 l = l.tail)
                for (int i = 0; i < l.head.length; i++)
                    databuf.appendChar(l.head[i]);
            endAttr(alenIdx);
            acount++;
        }
        if (genCrt && (code.crt != null)) {
            CRTable crt = code.crt;
            int alenIdx = writeAttr(names.CharacterRangeTable);
            int crtIdx = beginAttrs();
            int crtEntries = crt.writeCRT(databuf, code.lineMap, log);
            endAttrs(crtIdx, crtEntries);
            endAttr(alenIdx);
            acount++;
        }

        if (code.varDebugInfo && code.varBufferSize > 0) {
            int nGenericVars = 0;
            int alenIdx = writeAttr(names.LocalVariableTable);
            databuf.appendChar(code.getLVTSize());
            for (int i = 0; i < code.varBufferSize; i++) {
                Code.LocalVar var = code.varBuffer[i];
                for (Code.LocalVar.Range r : var.aliveRanges) {

                    Assert.check(r.start_pc >= 0
                            && r.start_pc <= code.cp);
                    databuf.appendChar(r.start_pc);
                    Assert.check(r.length >= 0
                            && (r.start_pc + r.length) <= code.cp);
                    databuf.appendChar(r.length);
                    VarSymbol sym = var.sym;
                    databuf.appendChar(pool.put(sym.name));
                    Type vartype = sym.erasure(types);
                    databuf.appendChar(pool.put(typeSig(vartype)));
                    databuf.appendChar(var.reg);
                    if (needsLocalVariableTypeEntry(var.sym.type)) {
                        nGenericVars++;
                    }
                }
            }
            endAttr(alenIdx);
            acount++;
            if (nGenericVars > 0) {
                alenIdx = writeAttr(names.LocalVariableTypeTable);
                databuf.appendChar(nGenericVars);
                int count = 0;
                for (int i = 0; i < code.varBufferSize; i++) {
                    Code.LocalVar var = code.varBuffer[i];
                    VarSymbol sym = var.sym;
                    if (!needsLocalVariableTypeEntry(sym.type))
                        continue;
                    for (Code.LocalVar.Range r : var.aliveRanges) {

                        databuf.appendChar(r.start_pc);
                        databuf.appendChar(r.length);
                        databuf.appendChar(pool.put(sym.name));
                        databuf.appendChar(pool.put(typeSig(sym.type)));
                        databuf.appendChar(var.reg);
                        count++;
                    }
                }
                Assert.check(count == nGenericVars);
                endAttr(alenIdx);
                acount++;
            }
        }
        if (code.stackMapBufferSize > 0) {
            if (debugstackmap) System.out.println("Stack map for " + code.meth);
            int alenIdx = writeAttr(code.stackMap.getAttributeName(names));
            writeStackMap(code);
            endAttr(alenIdx);
            acount++;
        }
        acount += writeTypeAnnotations(code.meth.getRawTypeAttributes(), true);
        endAttrs(acountIdx, acount);
    }

    private boolean needsLocalVariableTypeEntry(Type t) {


        return (!types.isSameType(t, types.erasure(t)) &&
                !t.isCompound());
    }

    void writeStackMap(Code code) {
        int nframes = code.stackMapBufferSize;
        if (debugstackmap) System.out.println(" nframes = " + nframes);
        databuf.appendChar(nframes);
        switch (code.stackMap) {
            case CLDC:
                for (int i = 0; i < nframes; i++) {
                    if (debugstackmap) System.out.print("  " + i + ":");
                    Code.StackMapFrame frame = code.stackMapBuffer[i];

                    if (debugstackmap) System.out.print(" pc=" + frame.pc);
                    databuf.appendChar(frame.pc);

                    int localCount = 0;
                    for (int j = 0; j < frame.locals.length;
                         j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.locals[j]))) {
                        localCount++;
                    }
                    if (debugstackmap) System.out.print(" nlocals=" +
                            localCount);
                    databuf.appendChar(localCount);
                    for (int j = 0; j < frame.locals.length;
                         j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.locals[j]))) {
                        if (debugstackmap) System.out.print(" local[" + j + "]=");
                        writeStackMapType(frame.locals[j]);
                    }

                    int stackCount = 0;
                    for (int j = 0; j < frame.stack.length;
                         j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.stack[j]))) {
                        stackCount++;
                    }
                    if (debugstackmap) System.out.print(" nstack=" +
                            stackCount);
                    databuf.appendChar(stackCount);
                    for (int j = 0; j < frame.stack.length;
                         j += (target.generateEmptyAfterBig() ? 1 : Code.width(frame.stack[j]))) {
                        if (debugstackmap) System.out.print(" stack[" + j + "]=");
                        writeStackMapType(frame.stack[j]);
                    }
                    if (debugstackmap) System.out.println();
                }
                break;
            case JSR202: {
                Assert.checkNull(code.stackMapBuffer);
                for (int i = 0; i < nframes; i++) {
                    if (debugstackmap) System.out.print("  " + i + ":");
                    StackMapTableFrame frame = code.stackMapTableBuffer[i];
                    frame.write(this);
                    if (debugstackmap) System.out.println();
                }
                break;
            }
            default:
                throw new AssertionError("Unexpected stackmap format value");
        }
    }

    void writeStackMapType(Type t) {
        if (t == null) {
            if (debugstackmap) System.out.print("empty");
            databuf.appendByte(0);
        } else switch (t.getTag()) {
            case BYTE:
            case CHAR:
            case SHORT:
            case INT:
            case BOOLEAN:
                if (debugstackmap) System.out.print("int");
                databuf.appendByte(1);
                break;
            case FLOAT:
                if (debugstackmap) System.out.print("float");
                databuf.appendByte(2);
                break;
            case DOUBLE:
                if (debugstackmap) System.out.print("double");
                databuf.appendByte(3);
                break;
            case LONG:
                if (debugstackmap) System.out.print("long");
                databuf.appendByte(4);
                break;
            case BOT:
                if (debugstackmap) System.out.print("null");
                databuf.appendByte(5);
                break;
            case CLASS:
            case ARRAY:
                if (debugstackmap) System.out.print("object(" + t + ")");
                databuf.appendByte(7);
                databuf.appendChar(pool.put(t));
                break;
            case TYPEVAR:
                if (debugstackmap) System.out.print("object(" + types.erasure(t).tsym + ")");
                databuf.appendByte(7);
                databuf.appendChar(pool.put(types.erasure(t).tsym));
                break;
            case UNINITIALIZED_THIS:
                if (debugstackmap) System.out.print("uninit_this");
                databuf.appendByte(6);
                break;
            case UNINITIALIZED_OBJECT: {
                UninitializedType uninitType = (UninitializedType) t;
                databuf.appendByte(8);
                if (debugstackmap) System.out.print("uninit_object@" + uninitType.offset);
                databuf.appendChar(uninitType.offset);
            }
            break;
            default:
                throw new AssertionError();
        }
    }

    void writeFields(Scope.Entry e) {


        List<VarSymbol> vars = List.nil();
        for (Scope.Entry i = e; i != null; i = i.sibling) {
            if (i.sym.kind == VAR) vars = vars.prepend((VarSymbol) i.sym);
        }
        while (vars.nonEmpty()) {
            writeField(vars.head);
            vars = vars.tail;
        }
    }

    void writeMethods(Scope.Entry e) {
        List<MethodSymbol> methods = List.nil();
        for (Scope.Entry i = e; i != null; i = i.sibling) {
            if (i.sym.kind == MTH && (i.sym.flags() & HYPOTHETICAL) == 0)
                methods = methods.prepend((MethodSymbol) i.sym);
        }
        while (methods.nonEmpty()) {
            writeMethod(methods.head);
            methods = methods.tail;
        }
    }

    public JavaFileObject writeClass(ClassSymbol c)
            throws IOException, PoolOverflow, StringOverflow {
        JavaFileObject outFile
                = fileManager.getJavaFileForOutput(CLASS_OUTPUT,
                c.flatname.toString(),
                JavaFileObject.Kind.CLASS,
                c.sourcefile);
        OutputStream out = outFile.openOutputStream();
        try {
            writeClassFile(out, c);
            if (verbose)
                log.printVerbose("wrote.file", outFile);
            out.close();
            out = null;
        } finally {
            if (out != null) {

                out.close();
                outFile.delete();
                outFile = null;
            }
        }
        return outFile;
    }

    public void writeClassFile(OutputStream out, ClassSymbol c)
            throws IOException, PoolOverflow, StringOverflow {
        Assert.check((c.flags() & COMPOUND) == 0);
        databuf.reset();
        poolbuf.reset();
        signatureGen.reset();
        pool = c.pool;
        innerClasses = null;
        innerClassesQueue = null;
        bootstrapMethods = new LinkedHashMap<DynamicMethod, MethodHandle>();
        Type supertype = types.supertype(c.type);
        List<Type> interfaces = types.interfaces(c.type);
        List<Type> typarams = c.type.getTypeArguments();
        int flags = adjustFlags(c.flags() & ~DEFAULT);
        if ((flags & PROTECTED) != 0) flags |= PUBLIC;
        flags = flags & ClassFlags & ~STRICTFP;
        if ((flags & INTERFACE) == 0) flags |= ACC_SUPER;
        if (c.isInner() && c.name.isEmpty()) flags &= ~FINAL;
        if (dumpClassModifiers) {
            PrintWriter pw = log.getWriter(Log.WriterKind.ERROR);
            pw.println();
            pw.println("CLASSFILE  " + c.getQualifiedName());
            pw.println("---" + flagNames(flags));
        }
        databuf.appendChar(flags);
        databuf.appendChar(pool.put(c));
        databuf.appendChar(supertype.hasTag(CLASS) ? pool.put(supertype.tsym) : 0);
        databuf.appendChar(interfaces.length());
        for (List<Type> l = interfaces; l.nonEmpty(); l = l.tail)
            databuf.appendChar(pool.put(l.head.tsym));
        int fieldsCount = 0;
        int methodsCount = 0;
        for (Scope.Entry e = c.members().elems; e != null; e = e.sibling) {
            switch (e.sym.kind) {
                case VAR:
                    fieldsCount++;
                    break;
                case MTH:
                    if ((e.sym.flags() & HYPOTHETICAL) == 0) methodsCount++;
                    break;
                case TYP:
                    enterInner((ClassSymbol) e.sym);
                    break;
                default:
                    Assert.error();
            }
        }
        if (c.trans_local != null) {
            for (ClassSymbol local : c.trans_local) {
                enterInner(local);
            }
        }
        databuf.appendChar(fieldsCount);
        writeFields(c.members().elems);
        databuf.appendChar(methodsCount);
        writeMethods(c.members().elems);
        int acountIdx = beginAttrs();
        int acount = 0;
        boolean sigReq =
                typarams.length() != 0 || supertype.allparams().length() != 0;
        for (List<Type> l = interfaces; !sigReq && l.nonEmpty(); l = l.tail)
            sigReq = l.head.allparams().length() != 0;
        if (sigReq) {
            Assert.check(source.allowGenerics());
            int alenIdx = writeAttr(names.Signature);
            if (typarams.length() != 0) signatureGen.assembleParamsSig(typarams);
            signatureGen.assembleSig(supertype);
            for (List<Type> l = interfaces; l.nonEmpty(); l = l.tail)
                signatureGen.assembleSig(l.head);
            databuf.appendChar(pool.put(signatureGen.toName()));
            signatureGen.reset();
            endAttr(alenIdx);
            acount++;
        }
        if (c.sourcefile != null && emitSourceFile) {
            int alenIdx = writeAttr(names.SourceFile);


            String simpleName = BaseFileObject.getSimpleName(c.sourcefile);
            databuf.appendChar(c.pool.put(names.fromString(simpleName)));
            endAttr(alenIdx);
            acount++;
        }
        if (genCrt) {

            int alenIdx = writeAttr(names.SourceID);
            databuf.appendChar(c.pool.put(names.fromString(Long.toString(getLastModified(c.sourcefile)))));
            endAttr(alenIdx);
            acount++;

            alenIdx = writeAttr(names.CompilationID);
            databuf.appendChar(c.pool.put(names.fromString(Long.toString(System.currentTimeMillis()))));
            endAttr(alenIdx);
            acount++;
        }
        acount += writeFlagAttrs(c.flags());
        acount += writeJavaAnnotations(c.getRawAttributes());
        acount += writeTypeAnnotations(c.getRawTypeAttributes(), false);
        acount += writeEnclosingMethodAttribute(c);
        acount += writeExtraClassAttributes(c);
        poolbuf.appendInt(JAVA_MAGIC);
        poolbuf.appendChar(target.minorVersion);
        poolbuf.appendChar(target.majorVersion);
        writePool(c.pool);
        if (innerClasses != null) {
            writeInnerClasses();
            acount++;
        }
        if (!bootstrapMethods.isEmpty()) {
            writeBootstrapMethods();
            acount++;
        }
        endAttrs(acountIdx, acount);
        poolbuf.appendBytes(databuf.elems, 0, databuf.length);
        out.write(poolbuf.elems, 0, poolbuf.length);
        pool = c.pool = null;
    }

    protected int writeExtraClassAttributes(ClassSymbol c) {
        return 0;
    }

    int adjustFlags(final long flags) {
        int result = (int) flags;
        if ((flags & SYNTHETIC) != 0 && !target.useSyntheticFlag())
            result &= ~SYNTHETIC;
        if ((flags & ENUM) != 0 && !target.useEnumFlag())
            result &= ~ENUM;
        if ((flags & ANNOTATION) != 0 && !target.useAnnotationFlag())
            result &= ~ANNOTATION;
        if ((flags & BRIDGE) != 0 && target.useBridgeFlag())
            result |= ACC_BRIDGE;
        if ((flags & VARARGS) != 0 && target.useVarargsFlag())
            result |= ACC_VARARGS;
        if ((flags & DEFAULT) != 0)
            result &= ~ABSTRACT;
        return result;
    }

    long getLastModified(FileObject filename) {
        long mod = 0;
        try {
            mod = filename.getLastModified();
        } catch (SecurityException e) {
            throw new AssertionError("CRT: couldn't get source file modification date: " + e.getMessage());
        }
        return mod;
    }

    public static class PoolOverflow extends Exception {
        private static final long serialVersionUID = 0;

        public PoolOverflow() {
        }
    }

    public static class StringOverflow extends Exception {
        private static final long serialVersionUID = 0;
        public final String value;

        public StringOverflow(String s) {
            value = s;
        }
    }

    abstract static class StackMapTableFrame {
        static StackMapTableFrame getInstance(Code.StackMapFrame this_frame,
                                              int prev_pc,
                                              Type[] prev_locals,
                                              Types types) {
            Type[] locals = this_frame.locals;
            Type[] stack = this_frame.stack;
            int offset_delta = this_frame.pc - prev_pc - 1;
            if (stack.length == 1) {
                if (locals.length == prev_locals.length
                        && compare(prev_locals, locals, types) == 0) {
                    return new SameLocals1StackItemFrame(offset_delta, stack[0]);
                }
            } else if (stack.length == 0) {
                int diff_length = compare(prev_locals, locals, types);
                if (diff_length == 0) {
                    return new SameFrame(offset_delta);
                } else if (-MAX_LOCAL_LENGTH_DIFF < diff_length && diff_length < 0) {

                    Type[] local_diff = new Type[-diff_length];
                    for (int i = prev_locals.length, j = 0; i < locals.length; i++, j++) {
                        local_diff[j] = locals[i];
                    }
                    return new AppendFrame(SAME_FRAME_EXTENDED - diff_length,
                            offset_delta,
                            local_diff);
                } else if (0 < diff_length && diff_length < MAX_LOCAL_LENGTH_DIFF) {

                    return new ChopFrame(SAME_FRAME_EXTENDED - diff_length,
                            offset_delta);
                }
            }

            return new FullFrame(offset_delta, locals, stack);
        }

        static boolean isInt(Type t) {
            return (t.getTag().isStrictSubRangeOf(INT) || t.hasTag(BOOLEAN));
        }

        static boolean isSameType(Type t1, Type t2, Types types) {
            if (t1 == null) {
                return t2 == null;
            }
            if (t2 == null) {
                return false;
            }
            if (isInt(t1) && isInt(t2)) {
                return true;
            }
            if (t1.hasTag(UNINITIALIZED_THIS)) {
                return t2.hasTag(UNINITIALIZED_THIS);
            } else if (t1.hasTag(UNINITIALIZED_OBJECT)) {
                if (t2.hasTag(UNINITIALIZED_OBJECT)) {
                    return ((UninitializedType) t1).offset == ((UninitializedType) t2).offset;
                } else {
                    return false;
                }
            } else if (t2.hasTag(UNINITIALIZED_THIS) || t2.hasTag(UNINITIALIZED_OBJECT)) {
                return false;
            }
            return types.isSameType(t1, t2);
        }

        static int compare(Type[] arr1, Type[] arr2, Types types) {
            int diff_length = arr1.length - arr2.length;
            if (diff_length > MAX_LOCAL_LENGTH_DIFF || diff_length < -MAX_LOCAL_LENGTH_DIFF) {
                return Integer.MAX_VALUE;
            }
            int len = (diff_length > 0) ? arr2.length : arr1.length;
            for (int i = 0; i < len; i++) {
                if (!isSameType(arr1[i], arr2[i], types)) {
                    return Integer.MAX_VALUE;
                }
            }
            return diff_length;
        }

        abstract int getFrameType();

        void write(ClassWriter writer) {
            int frameType = getFrameType();
            writer.databuf.appendByte(frameType);
            if (writer.debugstackmap) System.out.print(" frame_type=" + frameType);
        }

        static class SameFrame extends StackMapTableFrame {
            final int offsetDelta;

            SameFrame(int offsetDelta) {
                this.offsetDelta = offsetDelta;
            }

            int getFrameType() {
                return (offsetDelta < SAME_FRAME_SIZE) ? offsetDelta : SAME_FRAME_EXTENDED;
            }

            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                if (getFrameType() == SAME_FRAME_EXTENDED) {
                    writer.databuf.appendChar(offsetDelta);
                    if (writer.debugstackmap) {
                        System.out.print(" offset_delta=" + offsetDelta);
                    }
                }
            }
        }

        static class SameLocals1StackItemFrame extends StackMapTableFrame {
            final int offsetDelta;
            final Type stack;

            SameLocals1StackItemFrame(int offsetDelta, Type stack) {
                this.offsetDelta = offsetDelta;
                this.stack = stack;
            }

            int getFrameType() {
                return (offsetDelta < SAME_FRAME_SIZE) ?
                        (SAME_FRAME_SIZE + offsetDelta) :
                        SAME_LOCALS_1_STACK_ITEM_EXTENDED;
            }

            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                if (getFrameType() == SAME_LOCALS_1_STACK_ITEM_EXTENDED) {
                    writer.databuf.appendChar(offsetDelta);
                    if (writer.debugstackmap) {
                        System.out.print(" offset_delta=" + offsetDelta);
                    }
                }
                if (writer.debugstackmap) {
                    System.out.print(" stack[" + 0 + "]=");
                }
                writer.writeStackMapType(stack);
            }
        }

        static class ChopFrame extends StackMapTableFrame {
            final int frameType;
            final int offsetDelta;

            ChopFrame(int frameType, int offsetDelta) {
                this.frameType = frameType;
                this.offsetDelta = offsetDelta;
            }

            int getFrameType() {
                return frameType;
            }

            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                writer.databuf.appendChar(offsetDelta);
                if (writer.debugstackmap) {
                    System.out.print(" offset_delta=" + offsetDelta);
                }
            }
        }

        static class AppendFrame extends StackMapTableFrame {
            final int frameType;
            final int offsetDelta;
            final Type[] locals;

            AppendFrame(int frameType, int offsetDelta, Type[] locals) {
                this.frameType = frameType;
                this.offsetDelta = offsetDelta;
                this.locals = locals;
            }

            int getFrameType() {
                return frameType;
            }

            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                writer.databuf.appendChar(offsetDelta);
                if (writer.debugstackmap) {
                    System.out.print(" offset_delta=" + offsetDelta);
                }
                for (int i = 0; i < locals.length; i++) {
                    if (writer.debugstackmap) System.out.print(" locals[" + i + "]=");
                    writer.writeStackMapType(locals[i]);
                }
            }
        }

        static class FullFrame extends StackMapTableFrame {
            final int offsetDelta;
            final Type[] locals;
            final Type[] stack;

            FullFrame(int offsetDelta, Type[] locals, Type[] stack) {
                this.offsetDelta = offsetDelta;
                this.locals = locals;
                this.stack = stack;
            }

            int getFrameType() {
                return FULL_FRAME;
            }

            @Override
            void write(ClassWriter writer) {
                super.write(writer);
                writer.databuf.appendChar(offsetDelta);
                writer.databuf.appendChar(locals.length);
                if (writer.debugstackmap) {
                    System.out.print(" offset_delta=" + offsetDelta);
                    System.out.print(" nlocals=" + locals.length);
                }
                for (int i = 0; i < locals.length; i++) {
                    if (writer.debugstackmap) System.out.print(" locals[" + i + "]=");
                    writer.writeStackMapType(locals[i]);
                }
                writer.databuf.appendChar(stack.length);
                if (writer.debugstackmap) {
                    System.out.print(" nstack=" + stack.length);
                }
                for (int i = 0; i < stack.length; i++) {
                    if (writer.debugstackmap) System.out.print(" stack[" + i + "]=");
                    writer.writeStackMapType(stack[i]);
                }
            }
        }
    }

    private class CWSignatureGenerator extends Types.SignatureGenerator {

        ByteBuffer sigbuf = new ByteBuffer();

        CWSignatureGenerator(Types types) {
            super(types);
        }

        @Override
        public void assembleSig(Type type) {
            type = type.unannotatedType();
            switch (type.getTag()) {
                case UNINITIALIZED_THIS:
                case UNINITIALIZED_OBJECT:


                    assembleSig(types.erasure(((UninitializedType) type).qtype));
                    break;
                default:
                    super.assembleSig(type);
            }
        }

        @Override
        protected void append(char ch) {
            sigbuf.appendByte(ch);
        }

        @Override
        protected void append(byte[] ba) {
            sigbuf.appendBytes(ba);
        }

        @Override
        protected void append(Name name) {
            sigbuf.appendName(name);
        }

        @Override
        protected void classReference(ClassSymbol c) {
            enterInner(c);
        }

        private void reset() {
            sigbuf.reset();
        }

        private Name toName() {
            return sigbuf.toName(names);
        }

        private boolean isEmpty() {
            return sigbuf.length == 0;
        }
    }

    class AttributeWriter implements Attribute.Visitor {
        public void visitConstant(Attribute.Constant _value) {
            Object value = _value.value;
            switch (_value.type.getTag()) {
                case BYTE:
                    databuf.appendByte('B');
                    break;
                case CHAR:
                    databuf.appendByte('C');
                    break;
                case SHORT:
                    databuf.appendByte('S');
                    break;
                case INT:
                    databuf.appendByte('I');
                    break;
                case LONG:
                    databuf.appendByte('J');
                    break;
                case FLOAT:
                    databuf.appendByte('F');
                    break;
                case DOUBLE:
                    databuf.appendByte('D');
                    break;
                case BOOLEAN:
                    databuf.appendByte('Z');
                    break;
                case CLASS:
                    Assert.check(value instanceof String);
                    databuf.appendByte('s');
                    value = names.fromString(value.toString());
                    break;
                default:
                    throw new AssertionError(_value.type);
            }
            databuf.appendChar(pool.put(value));
        }

        public void visitEnum(Attribute.Enum e) {
            databuf.appendByte('e');
            databuf.appendChar(pool.put(typeSig(e.value.type)));
            databuf.appendChar(pool.put(e.value.name));
        }

        public void visitClass(Attribute.Class clazz) {
            databuf.appendByte('c');
            databuf.appendChar(pool.put(typeSig(clazz.classType)));
        }

        public void visitCompound(Attribute.Compound compound) {
            databuf.appendByte('@');
            writeCompoundAttribute(compound);
        }

        public void visitError(Attribute.Error x) {
            throw new AssertionError(x);
        }

        public void visitArray(Attribute.Array array) {
            databuf.appendByte('[');
            databuf.appendChar(array.values.length);
            for (Attribute a : array.values) {
                a.accept(this);
            }
        }
    }
}
