package com.sun.tools.javadoc;

import com.sun.javadoc.*;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;

import javax.tools.FileObject;
import java.io.IOException;
import java.io.InputStream;

public class PackageDocImpl extends DocImpl implements PackageDoc {

    public FileObject docPath = null;
    public boolean setDocPath = false;
    protected PackageSymbol sym;
    boolean isIncluded = false;
    private JCCompilationUnit tree;
    private boolean foundDoc;
    private List<ClassDocImpl> allClassesFiltered = null;
    private List<ClassDocImpl> allClasses = null;
    private String qualifiedName;
    private boolean checkDocWarningEmitted = false;

    public PackageDocImpl(DocEnv env, PackageSymbol sym) {
        this(env, sym, null);
    }

    public PackageDocImpl(DocEnv env, PackageSymbol sym, TreePath treePath) {
        super(env, treePath);
        this.sym = sym;
        this.tree = (treePath == null) ? null : (JCCompilationUnit) treePath.getCompilationUnit();
        foundDoc = (documentation != null);
    }

    void setTree(JCTree tree) {
        this.tree = (JCCompilationUnit) tree;
    }

    public void setTreePath(TreePath treePath) {
        super.setTreePath(treePath);
        checkDoc();
    }

    protected String documentation() {
        if (documentation != null)
            return documentation;
        if (docPath != null) {
            // read from file
            try {
                InputStream s = docPath.openInputStream();
                documentation = readHTMLDocumentation(s, docPath);
            } catch (IOException exc) {
                documentation = "";
                env.error(null, "javadoc.File_Read_Error", docPath.getName());
            }
        } else {
            // no doc file to be had
            documentation = "";
        }
        return documentation;
    }

    private List<ClassDocImpl> getClasses(boolean filtered) {
        if (allClasses != null && !filtered) {
            return allClasses;
        }
        if (allClassesFiltered != null && filtered) {
            return allClassesFiltered;
        }
        ListBuffer<ClassDocImpl> classes = new ListBuffer<ClassDocImpl>();
        for (Scope.Entry e = sym.members().elems; e != null; e = e.sibling) {
            if (e.sym != null) {
                ClassSymbol s = (ClassSymbol) e.sym;
                ClassDocImpl c = env.getClassDoc(s);
                if (c != null && !c.isSynthetic())
                    c.addAllClasses(classes, filtered);
            }
        }
        if (filtered)
            return allClassesFiltered = classes.toList();
        else
            return allClasses = classes.toList();
    }

    public void addAllClassesTo(ListBuffer<ClassDocImpl> list) {
        list.appendList(getClasses(true));
    }

    public ClassDoc[] allClasses(boolean filter) {
        List<ClassDocImpl> classes = getClasses(filter);
        return classes.toArray(new ClassDocImpl[classes.length()]);
    }

    public ClassDoc[] allClasses() {
        return allClasses(true);
    }

    public ClassDoc[] ordinaryClasses() {
        ListBuffer<ClassDocImpl> ret = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl c : getClasses(true)) {
            if (c.isOrdinaryClass()) {
                ret.append(c);
            }
        }
        return ret.toArray(new ClassDocImpl[ret.length()]);
    }

    public ClassDoc[] exceptions() {
        ListBuffer<ClassDocImpl> ret = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl c : getClasses(true)) {
            if (c.isException()) {
                ret.append(c);
            }
        }
        return ret.toArray(new ClassDocImpl[ret.length()]);
    }

    public ClassDoc[] errors() {
        ListBuffer<ClassDocImpl> ret = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl c : getClasses(true)) {
            if (c.isError()) {
                ret.append(c);
            }
        }
        return ret.toArray(new ClassDocImpl[ret.length()]);
    }

    public ClassDoc[] enums() {
        ListBuffer<ClassDocImpl> ret = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl c : getClasses(true)) {
            if (c.isEnum()) {
                ret.append(c);
            }
        }
        return ret.toArray(new ClassDocImpl[ret.length()]);
    }

    public ClassDoc[] interfaces() {
        ListBuffer<ClassDocImpl> ret = new ListBuffer<ClassDocImpl>();
        for (ClassDocImpl c : getClasses(true)) {
            if (c.isInterface()) {
                ret.append(c);
            }
        }
        return ret.toArray(new ClassDocImpl[ret.length()]);
    }

    public AnnotationTypeDoc[] annotationTypes() {
        ListBuffer<AnnotationTypeDocImpl> ret =
                new ListBuffer<AnnotationTypeDocImpl>();
        for (ClassDocImpl c : getClasses(true)) {
            if (c.isAnnotationType()) {
                ret.append((AnnotationTypeDocImpl) c);
            }
        }
        return ret.toArray(new AnnotationTypeDocImpl[ret.length()]);
    }

    public AnnotationDesc[] annotations() {
        AnnotationDesc res[] = new AnnotationDesc[sym.getRawAttributes().length()];
        int i = 0;
        for (Attribute.Compound a : sym.getRawAttributes()) {
            res[i++] = new AnnotationDescImpl(env, a);
        }
        return res;
    }

    public ClassDoc findClass(String className) {
        final boolean filtered = true;
        for (ClassDocImpl c : getClasses(filtered)) {
            if (c.name().equals(className)) {
                return c;
            }
        }
        return null;
    }

    public boolean isIncluded() {
        return isIncluded;
    }

    public String name() {
        return qualifiedName();
    }

    public String qualifiedName() {
        if (qualifiedName == null) {
            Name fullname = sym.getQualifiedName();
            qualifiedName = fullname.isEmpty() ? "" : fullname.toString();
        }
        return qualifiedName;
    }

    public void setDocPath(FileObject path) {
        setDocPath = true;
        if (path == null)
            return;
        if (!path.equals(docPath)) {
            docPath = path;
            checkDoc();
        }
    }

    private void checkDoc() {
        if (foundDoc) {
            if (!checkDocWarningEmitted) {
                env.warning(null, "javadoc.Multiple_package_comments", name());
                checkDocWarningEmitted = true;
            }
        } else {
            foundDoc = true;
        }
    }

    public SourcePosition position() {
        return (tree != null)
                ? SourcePositionImpl.make(tree.sourcefile, tree.pos, tree.lineMap)
                : SourcePositionImpl.make(docPath, Position.NOPOS, null);
    }
}
