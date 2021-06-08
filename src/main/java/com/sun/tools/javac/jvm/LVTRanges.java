package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import java.util.Map;
import java.util.Map.Entry;
import java.util.WeakHashMap;

public class LVTRanges {

    protected static final Context.Key<LVTRanges> lvtRangesKey = new Context.Key<>();
    private static final long serialVersionUID = 1812267524140424433L;
    protected Context context;
    protected Map<MethodSymbol, Map<JCTree, List<VarSymbol>>>
            aliveRangeClosingTrees = new WeakHashMap<>();
    public LVTRanges(Context context) {
        this.context = context;
        context.put(lvtRangesKey, this);
    }

    public static LVTRanges instance(Context context) {
        LVTRanges instance = context.get(lvtRangesKey);
        if (instance == null) {
            instance = new LVTRanges(context);
        }
        return instance;
    }

    public List<VarSymbol> getVars(MethodSymbol method, JCTree tree) {
        Map<JCTree, List<VarSymbol>> varMap = aliveRangeClosingTrees.get(method);
        return (varMap != null) ? varMap.get(tree) : null;
    }

    public boolean containsKey(MethodSymbol method, JCTree tree) {
        Map<JCTree, List<VarSymbol>> varMap = aliveRangeClosingTrees.get(method);
        if (varMap == null) {
            return false;
        }
        return varMap.containsKey(tree);
    }

    public void setEntry(MethodSymbol method, JCTree tree, List<VarSymbol> vars) {
        Map<JCTree, List<VarSymbol>> varMap = aliveRangeClosingTrees.get(method);
        if (varMap != null) {
            varMap.put(tree, vars);
        } else {
            varMap = new WeakHashMap<>();
            varMap.put(tree, vars);
            aliveRangeClosingTrees.put(method, varMap);
        }
    }

    public List<VarSymbol> removeEntry(MethodSymbol method, JCTree tree) {
        Map<JCTree, List<VarSymbol>> varMap = aliveRangeClosingTrees.get(method);
        if (varMap != null) {
            List<VarSymbol> result = varMap.remove(tree);
            if (varMap.isEmpty()) {
                aliveRangeClosingTrees.remove(method);
            }
            return result;
        }
        return null;
    }

    @Override
    public String toString() {
        String result = "";
        for (Entry<MethodSymbol, Map<JCTree, List<VarSymbol>>> mainEntry : aliveRangeClosingTrees.entrySet()) {
            result += "Method: \n" + mainEntry.getKey().flatName() + "\n";
            int i = 1;
            for (Entry<JCTree, List<VarSymbol>> treeEntry : mainEntry.getValue().entrySet()) {
                result += "    Tree " + i + ": \n" + treeEntry.getKey().toString() + "\n";
                result += "        Variables closed:\n";
                for (VarSymbol var : treeEntry.getValue()) {
                    result += "            " + var.toString();
                }
                result += "\n";
                i++;
            }
        }
        return result;
    }
}
