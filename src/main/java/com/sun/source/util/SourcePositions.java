package com.sun.source.util;

import com.sun.source.tree.*;
@jdk.Exported
public interface SourcePositions {
     long getStartPosition(CompilationUnitTree file, Tree tree);
     long getEndPosition(CompilationUnitTree file, Tree tree);
}
