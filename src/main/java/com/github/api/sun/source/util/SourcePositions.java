package com.github.api.sun.source.util;

import com.github.api.sun.source.tree.CompilationUnitTree;
import com.github.api.sun.source.tree.Tree;

@jdk.Exported
public interface SourcePositions {
    long getStartPosition(CompilationUnitTree file, Tree tree);

    long getEndPosition(CompilationUnitTree file, Tree tree);
}
