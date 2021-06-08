package com.sun.javadoc;

public interface RootDoc extends Doc, DocErrorReporter {
    String[][] options();

    PackageDoc[] specifiedPackages();

    ClassDoc[] specifiedClasses();

    ClassDoc[] classes();

    PackageDoc packageNamed(String name);

    ClassDoc classNamed(String qualifiedName);
}
