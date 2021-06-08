package com.sun.source.util;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.tree.CompilationUnitTree;
@jdk.Exported
public interface DocSourcePositions extends SourcePositions {

    long getStartPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree);

    long getEndPosition(CompilationUnitTree file, DocCommentTree comment, DocTree tree);

}
