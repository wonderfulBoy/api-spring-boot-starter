package com.sun.source.tree;

import javax.tools.JavaFileObject;
import java.util.List;

@jdk.Exported
public interface CompilationUnitTree extends Tree {
    List<? extends AnnotationTree> getPackageAnnotations();

    ExpressionTree getPackageName();

    List<? extends ImportTree> getImports();

    List<? extends Tree> getTypeDecls();

    JavaFileObject getSourceFile();

    LineMap getLineMap();
}
