package com.sun.source.doctree;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
@jdk.Exported
public interface ErroneousTree extends TextTree {
    Diagnostic<JavaFileObject> getDiagnostic();
}
