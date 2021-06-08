package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.DelegatedSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.code.Types.UniqueType;
import com.sun.tools.javac.util.ArrayUtils;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.Name;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Pool {
    public static final int MAX_ENTRIES = 0xFFFF;
    public static final int MAX_STRING_LENGTH = 0xFFFF;

    int pp;

    Object[] pool;

    Map<Object, Integer> indices;
    Types types;

    public Pool(int pp, Object[] pool, Types types) {
        this.pp = pp;
        this.pool = pool;
        this.types = types;
        this.indices = new HashMap<Object, Integer>(pool.length);
        for (int i = 1; i < pp; i++) {
            if (pool[i] != null) indices.put(pool[i], i);
        }
    }

    public Pool(Types types) {
        this(1, new Object[64], types);
    }

    public int numEntries() {
        return pp;
    }

    public void reset() {
        pp = 1;
        indices.clear();
    }

    public int put(Object value) {
        value = makePoolValue(value);
        Integer index = indices.get(value);
        if (index == null) {
            index = pp;
            indices.put(value, index);
            pool = ArrayUtils.ensureCapacity(pool, pp);
            pool[pp++] = value;
            if (value instanceof Long || value instanceof Double) {
                pool = ArrayUtils.ensureCapacity(pool, pp);
                pool[pp++] = null;
            }
        }
        return index.intValue();
    }

    Object makePoolValue(Object o) {
        if (o instanceof DynamicMethodSymbol) {
            return new DynamicMethod((DynamicMethodSymbol) o, types);
        } else if (o instanceof MethodSymbol) {
            return new Method((MethodSymbol) o, types);
        } else if (o instanceof VarSymbol) {
            return new Variable((VarSymbol) o, types);
        } else if (o instanceof Type) {
            return new UniqueType((Type) o, types);
        } else {
            return o;
        }
    }

    public int get(Object o) {
        Integer n = indices.get(o);
        return n == null ? -1 : n.intValue();
    }

    static class Method extends DelegatedSymbol<MethodSymbol> {
        UniqueType uniqueType;

        Method(MethodSymbol m, Types types) {
            super(m);
            this.uniqueType = new UniqueType(m.type, types);
        }

        public boolean equals(Object any) {
            if (!(any instanceof Method)) return false;
            MethodSymbol o = ((Method) any).other;
            MethodSymbol m = this.other;
            return
                    o.name == m.name &&
                            o.owner == m.owner &&
                            ((Method) any).uniqueType.equals(uniqueType);
        }

        public int hashCode() {
            MethodSymbol m = this.other;
            return
                    m.name.hashCode() * 33 +
                            m.owner.hashCode() * 9 +
                            uniqueType.hashCode();
        }
    }

    static class DynamicMethod extends Method {
        public Object[] uniqueStaticArgs;

        DynamicMethod(DynamicMethodSymbol m, Types types) {
            super(m, types);
            uniqueStaticArgs = getUniqueTypeArray(m.staticArgs, types);
        }

        @Override
        public boolean equals(Object any) {
            if (!super.equals(any)) return false;
            if (!(any instanceof DynamicMethod)) return false;
            DynamicMethodSymbol dm1 = (DynamicMethodSymbol) other;
            DynamicMethodSymbol dm2 = (DynamicMethodSymbol) ((DynamicMethod) any).other;
            return dm1.bsm == dm2.bsm &&
                    dm1.bsmKind == dm2.bsmKind &&
                    Arrays.equals(uniqueStaticArgs,
                            ((DynamicMethod) any).uniqueStaticArgs);
        }

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            DynamicMethodSymbol dm = (DynamicMethodSymbol) other;
            hash += dm.bsmKind * 7 +
                    dm.bsm.hashCode() * 11;
            for (int i = 0; i < dm.staticArgs.length; i++) {
                hash += (uniqueStaticArgs[i].hashCode() * 23);
            }
            return hash;
        }

        private Object[] getUniqueTypeArray(Object[] objects, Types types) {
            Object[] result = new Object[objects.length];
            for (int i = 0; i < objects.length; i++) {
                if (objects[i] instanceof Type) {
                    result[i] = new UniqueType((Type) objects[i], types);
                } else {
                    result[i] = objects[i];
                }
            }
            return result;
        }
    }

    static class Variable extends DelegatedSymbol<VarSymbol> {
        UniqueType uniqueType;

        Variable(VarSymbol v, Types types) {
            super(v);
            this.uniqueType = new UniqueType(v.type, types);
        }

        public boolean equals(Object any) {
            if (!(any instanceof Variable)) return false;
            VarSymbol o = ((Variable) any).other;
            VarSymbol v = other;
            return
                    o.name == v.name &&
                            o.owner == v.owner &&
                            ((Variable) any).uniqueType.equals(uniqueType);
        }

        public int hashCode() {
            VarSymbol v = other;
            return
                    v.name.hashCode() * 33 +
                            v.owner.hashCode() * 9 +
                            uniqueType.hashCode();
        }
    }

    public static class MethodHandle {

        int refKind;

        Symbol refSym;
        UniqueType uniqueType;
        Filter<Name> nonInitFilter = new Filter<Name>() {
            public boolean accepts(Name n) {
                return n != n.table.names.init && n != n.table.names.clinit;
            }
        };
        Filter<Name> initFilter = new Filter<Name>() {
            public boolean accepts(Name n) {
                return n == n.table.names.init;
            }
        };

        public MethodHandle(int refKind, Symbol refSym, Types types) {
            this.refKind = refKind;
            this.refSym = refSym;
            this.uniqueType = new UniqueType(this.refSym.type, types);
            checkConsistent();
        }

        public boolean equals(Object other) {
            if (!(other instanceof MethodHandle)) return false;
            MethodHandle mr = (MethodHandle) other;
            if (mr.refKind != refKind) return false;
            Symbol o = mr.refSym;
            return
                    o.name == refSym.name &&
                            o.owner == refSym.owner &&
                            ((MethodHandle) other).uniqueType.equals(uniqueType);
        }

        public int hashCode() {
            return
                    refKind * 65 +
                            refSym.name.hashCode() * 33 +
                            refSym.owner.hashCode() * 9 +
                            uniqueType.hashCode();
        }

        @SuppressWarnings("fallthrough")
        private void checkConsistent() {
            boolean staticOk = false;
            int expectedKind = -1;
            Filter<Name> nameFilter = nonInitFilter;
            boolean interfaceOwner = false;
            switch (refKind) {
                case ClassFile.REF_getStatic:
                case ClassFile.REF_putStatic:
                    staticOk = true;
                case ClassFile.REF_getField:
                case ClassFile.REF_putField:
                    expectedKind = Kinds.VAR;
                    break;
                case ClassFile.REF_newInvokeSpecial:
                    nameFilter = initFilter;
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeInterface:
                    interfaceOwner = true;
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeStatic:
                    interfaceOwner = true;
                    staticOk = true;
                case ClassFile.REF_invokeVirtual:
                    expectedKind = Kinds.MTH;
                    break;
                case ClassFile.REF_invokeSpecial:
                    interfaceOwner = true;
                    expectedKind = Kinds.MTH;
                    break;
            }
            Assert.check(!refSym.isStatic() || staticOk);
            Assert.check(refSym.kind == expectedKind);
            Assert.check(nameFilter.accepts(refSym.name));
            Assert.check(!refSym.owner.isInterface() || interfaceOwner);
        }
    }
}
