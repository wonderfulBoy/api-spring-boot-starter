package com.github.api.sun.tools.classfile;

public interface Dependency {

    Location getOrigin();

    Location getTarget();

    interface Filter {

        boolean accepts(Dependency dependency);
    }

    interface Finder {

        Iterable<? extends Dependency> findDependencies(ClassFile classfile);
    }

    interface Location {

        String getName();

        String getClassName();

        String getPackageName();
    }
}
