package com.github.api.sun.tools.classfile;

import com.github.api.sun.tools.classfile.Instruction.TypeKind;

import java.util.*;

import static com.github.api.sun.tools.classfile.ConstantPool.*;

public final class ReferenceFinder {

    private final Filter filter;
    private final Visitor visitor;
    private ConstantPool.Visitor<Boolean, ConstantPool> cpVisitor =
            new ConstantPool.Visitor<Boolean, ConstantPool>() {
                public Boolean visitClass(CONSTANT_Class_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitInterfaceMethodref(CONSTANT_InterfaceMethodref_info info, ConstantPool cpool) {
                    return filter.accept(cpool, info);
                }

                public Boolean visitMethodref(CONSTANT_Methodref_info info, ConstantPool cpool) {
                    return filter.accept(cpool, info);
                }

                public Boolean visitFieldref(CONSTANT_Fieldref_info info, ConstantPool cpool) {
                    return filter.accept(cpool, info);
                }

                public Boolean visitDouble(CONSTANT_Double_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitFloat(CONSTANT_Float_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitInteger(CONSTANT_Integer_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitInvokeDynamic(CONSTANT_InvokeDynamic_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitLong(CONSTANT_Long_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitNameAndType(CONSTANT_NameAndType_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitMethodHandle(CONSTANT_MethodHandle_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitMethodType(CONSTANT_MethodType_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitString(CONSTANT_String_info info, ConstantPool cpool) {
                    return false;
                }

                public Boolean visitUtf8(CONSTANT_Utf8_info info, ConstantPool cpool) {
                    return false;
                }
            };
    private Instruction.KindVisitor<Integer, List<Integer>> codeVisitor =
            new Instruction.KindVisitor<Integer, List<Integer>>() {
                public Integer visitNoOperands(Instruction instr, List<Integer> p) {
                    return 0;
                }

                public Integer visitArrayType(Instruction instr, TypeKind kind, List<Integer> p) {
                    return 0;
                }

                public Integer visitBranch(Instruction instr, int offset, List<Integer> p) {
                    return 0;
                }

                public Integer visitConstantPoolRef(Instruction instr, int index, List<Integer> p) {
                    return p.contains(index) ? index : 0;
                }

                public Integer visitConstantPoolRefAndValue(Instruction instr, int index, int value, List<Integer> p) {
                    return p.contains(index) ? index : 0;
                }

                public Integer visitLocal(Instruction instr, int index, List<Integer> p) {
                    return 0;
                }

                public Integer visitLocalAndValue(Instruction instr, int index, int value, List<Integer> p) {
                    return 0;
                }

                public Integer visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, List<Integer> p) {
                    return 0;
                }

                public Integer visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, List<Integer> p) {
                    return 0;
                }

                public Integer visitValue(Instruction instr, int value, List<Integer> p) {
                    return 0;
                }

                public Integer visitUnknown(Instruction instr, List<Integer> p) {
                    return 0;
                }
            };

    public ReferenceFinder(Filter filter, Visitor visitor) {
        this.filter = Objects.requireNonNull(filter);
        this.visitor = Objects.requireNonNull(visitor);
    }

    public boolean parse(ClassFile cf) throws ConstantPoolException {
        List<Integer> cprefs = new ArrayList<Integer>();
        int index = 1;
        for (CPInfo cpInfo : cf.constant_pool.entries()) {
            if (cpInfo.accept(cpVisitor, cf.constant_pool)) {
                cprefs.add(index);
            }
            index += cpInfo.size();
        }
        if (cprefs.isEmpty()) {
            return false;
        }
        for (Method m : cf.methods) {
            Set<Integer> ids = new HashSet<Integer>();
            Code_attribute c_attr = (Code_attribute) m.attributes.get(Attribute.Code);
            if (c_attr != null) {
                for (Instruction instr : c_attr.getInstructions()) {
                    int idx = instr.accept(codeVisitor, cprefs);
                    if (idx > 0) {
                        ids.add(idx);
                    }
                }
            }
            if (ids.size() > 0) {
                List<CPRefInfo> refInfos = new ArrayList<CPRefInfo>(ids.size());
                for (int id : ids) {
                    refInfos.add((CPRefInfo) cf.constant_pool.get(id));
                }
                visitor.visit(cf, m, refInfos);
            }
        }
        return true;
    }

    public interface Filter {

        boolean accept(ConstantPool cpool, CPRefInfo cpref);
    }
    public interface Visitor {

        void visit(ClassFile cf, Method method, List<CPRefInfo> refConstantPool);
    }
}
