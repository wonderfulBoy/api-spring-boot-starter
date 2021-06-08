package com.github.api.sun.source.util;

import com.github.api.sun.source.doctree.DocCommentTree;
import com.github.api.sun.source.doctree.DocTree;
import com.github.api.sun.source.tree.CompilationUnitTree;

@jdk.Exported
public interface DocSourcePositions extends SourcePositions {
    long getStartPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree);

    long getEndPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree);
}
