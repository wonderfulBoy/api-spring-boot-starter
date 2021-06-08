package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.internal.toolkit.Configuration;

import java.util.*;

public class ClassTree {

    private List<ClassDoc> baseclasses = new ArrayList<ClassDoc>();

    private Map<ClassDoc, List<ClassDoc>> subclasses = new HashMap<ClassDoc, List<ClassDoc>>();

    private List<ClassDoc> baseinterfaces = new ArrayList<ClassDoc>();

    private Map<ClassDoc, List<ClassDoc>> subinterfaces = new HashMap<ClassDoc, List<ClassDoc>>();
    private List<ClassDoc> baseEnums = new ArrayList<ClassDoc>();
    private Map<ClassDoc, List<ClassDoc>> subEnums = new HashMap<ClassDoc, List<ClassDoc>>();
    private List<ClassDoc> baseAnnotationTypes = new ArrayList<ClassDoc>();
    private Map<ClassDoc, List<ClassDoc>> subAnnotationTypes = new HashMap<ClassDoc, List<ClassDoc>>();

    private Map<ClassDoc, List<ClassDoc>> implementingclasses = new HashMap<ClassDoc, List<ClassDoc>>();

    public ClassTree(Configuration configuration, boolean noDeprecated) {
        configuration.message.notice("doclet.Building_Tree");
        buildTree(configuration.root.classes(), configuration);
    }

    public ClassTree(RootDoc root, Configuration configuration) {
        buildTree(root.classes(), configuration);
    }

    public ClassTree(ClassDoc[] classes, Configuration configuration) {
        buildTree(classes, configuration);
    }

    private void buildTree(ClassDoc[] classes, Configuration configuration) {
        for (int i = 0; i < classes.length; i++) {


            if (configuration.nodeprecated &&
                    (Util.isDeprecated(classes[i]) ||
                            Util.isDeprecated(classes[i].containingPackage()))) {
                continue;
            }
            if (configuration.javafx
                    && classes[i].tags("treatAsPrivate").length > 0) {
                continue;
            }
            if (classes[i].isEnum()) {
                processType(classes[i], configuration, baseEnums, subEnums);
            } else if (classes[i].isClass()) {
                processType(classes[i], configuration, baseclasses, subclasses);
            } else if (classes[i].isInterface()) {
                processInterface(classes[i]);
                List<ClassDoc> list = implementingclasses.get(classes[i]);
                if (list != null) {
                    Collections.sort(list);
                }
            } else if (classes[i].isAnnotationType()) {
                processType(classes[i], configuration, baseAnnotationTypes,
                        subAnnotationTypes);
            }
        }
        Collections.sort(baseinterfaces);
        for (Iterator<List<ClassDoc>> it = subinterfaces.values().iterator(); it.hasNext(); ) {
            Collections.sort(it.next());
        }
        for (Iterator<List<ClassDoc>> it = subclasses.values().iterator(); it.hasNext(); ) {
            Collections.sort(it.next());
        }
    }

    private void processType(ClassDoc cd, Configuration configuration,
                             List<ClassDoc> bases, Map<ClassDoc, List<ClassDoc>> subs) {
        ClassDoc superclass = Util.getFirstVisibleSuperClassCD(cd, configuration);
        if (superclass != null) {
            if (!add(subs, superclass, cd)) {
                return;
            } else {
                processType(superclass, configuration, bases, subs);
            }
        } else {
            if (!bases.contains(cd)) {
                bases.add(cd);
            }
        }
        List<Type> intfacs = Util.getAllInterfaces(cd, configuration);
        for (Iterator<Type> iter = intfacs.iterator(); iter.hasNext(); ) {
            add(implementingclasses, iter.next().asClassDoc(), cd);
        }
    }

    private void processInterface(ClassDoc cd) {
        ClassDoc[] intfacs = cd.interfaces();
        if (intfacs.length > 0) {
            for (int i = 0; i < intfacs.length; i++) {
                if (!add(subinterfaces, intfacs[i], cd)) {
                    return;
                } else {
                    processInterface(intfacs[i]);
                }
            }
        } else {


            if (!baseinterfaces.contains(cd)) {
                baseinterfaces.add(cd);
            }
        }
    }

    private boolean add(Map<ClassDoc, List<ClassDoc>> map, ClassDoc superclass, ClassDoc cd) {
        List<ClassDoc> list = map.get(superclass);
        if (list == null) {
            list = new ArrayList<ClassDoc>();
            map.put(superclass, list);
        }
        if (list.contains(cd)) {
            return false;
        } else {
            list.add(cd);
        }
        return true;
    }

    private List<ClassDoc> get(Map<ClassDoc, List<ClassDoc>> map, ClassDoc cd) {
        List<ClassDoc> list = map.get(cd);
        if (list == null) {
            return new ArrayList<ClassDoc>();
        }
        return list;
    }

    public List<ClassDoc> subclasses(ClassDoc cd) {
        return get(subclasses, cd);
    }

    public List<ClassDoc> subinterfaces(ClassDoc cd) {
        return get(subinterfaces, cd);
    }

    public List<ClassDoc> implementingclasses(ClassDoc cd) {
        List<ClassDoc> result = get(implementingclasses, cd);
        List<ClassDoc> subinterfaces = allSubs(cd, false);


        Iterator<ClassDoc> implementingClassesIter, subInterfacesIter = subinterfaces.listIterator();
        ClassDoc c;
        while (subInterfacesIter.hasNext()) {
            implementingClassesIter = implementingclasses(
                    subInterfacesIter.next()).listIterator();
            while (implementingClassesIter.hasNext()) {
                c = implementingClassesIter.next();
                if (!result.contains(c)) {
                    result.add(c);
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    public List<ClassDoc> subs(ClassDoc cd, boolean isEnum) {
        if (isEnum) {
            return get(subEnums, cd);
        } else if (cd.isAnnotationType()) {
            return get(subAnnotationTypes, cd);
        } else if (cd.isInterface()) {
            return get(subinterfaces, cd);
        } else if (cd.isClass()) {
            return get(subclasses, cd);
        } else {
            return null;
        }
    }

    public List<ClassDoc> allSubs(ClassDoc cd, boolean isEnum) {
        List<ClassDoc> list = subs(cd, isEnum);
        for (int i = 0; i < list.size(); i++) {
            cd = list.get(i);
            List<ClassDoc> tlist = subs(cd, isEnum);
            for (int j = 0; j < tlist.size(); j++) {
                ClassDoc tcd = tlist.get(j);
                if (!list.contains(tcd)) {
                    list.add(tcd);
                }
            }
        }
        Collections.sort(list);
        return list;
    }

    public List<ClassDoc> baseclasses() {
        return baseclasses;
    }

    public List<ClassDoc> baseinterfaces() {
        return baseinterfaces;
    }

    public List<ClassDoc> baseEnums() {
        return baseEnums;
    }

    public List<ClassDoc> baseAnnotationTypes() {
        return baseAnnotationTypes;
    }
}
