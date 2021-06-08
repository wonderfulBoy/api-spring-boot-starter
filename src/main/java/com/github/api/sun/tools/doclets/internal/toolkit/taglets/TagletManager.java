package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.internal.toolkit.util.MessageRetriever;

import javax.tools.DocumentationTool;
import javax.tools.JavaFileManager;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class TagletManager {

    public static final char SIMPLE_TAGLET_OPT_SEPARATOR = ':';

    public static final String ALT_SIMPLE_TAGLET_OPT_SEPARATOR = "-";

    private LinkedHashMap<String, Taglet> customTags;

    private Taglet[] packageTags;

    private Taglet[] typeTags;

    private Taglet[] fieldTags;

    private Taglet[] constructorTags;

    private Taglet[] methodTags;

    private Taglet[] overviewTags;

    private Taglet[] inlineTags;

    private Taglet[] serializedFormTags;

    private MessageRetriever message;

    private Set<String> standardTags;

    private Set<String> standardTagsLowercase;

    private Set<String> overridenStandardTags;

    private Set<String> potentiallyConflictingTags;

    private Set<String> unseenCustomTags;

    private boolean nosince;

    private boolean showversion;

    private boolean showauthor;

    private boolean javafx;

    public TagletManager(boolean nosince, boolean showversion,
                         boolean showauthor, boolean javafx,
                         MessageRetriever message) {
        overridenStandardTags = new HashSet<String>();
        potentiallyConflictingTags = new HashSet<String>();
        standardTags = new HashSet<String>();
        standardTagsLowercase = new HashSet<String>();
        unseenCustomTags = new HashSet<String>();
        customTags = new LinkedHashMap<String, Taglet>();
        this.nosince = nosince;
        this.showversion = showversion;
        this.showauthor = showauthor;
        this.javafx = javafx;
        this.message = message;
        initStandardTaglets();
        initStandardTagsLowercase();
    }

    public void addCustomTag(Taglet customTag) {
        if (customTag != null) {
            String name = customTag.getName();
            customTags.remove(name);
            customTags.put(name, customTag);
            checkTagName(name);
        }
    }

    public Set<String> getCustomTagNames() {
        return customTags.keySet();
    }

    public void addCustomTag(String classname, JavaFileManager fileManager, String tagletPath) {
        try {
            Class<?> customTagClass = null;

            String cpString = null;
            ClassLoader tagClassLoader;
            if (fileManager != null && fileManager.hasLocation(DocumentationTool.Location.TAGLET_PATH)) {
                tagClassLoader = fileManager.getClassLoader(DocumentationTool.Location.TAGLET_PATH);
            } else {

                cpString = appendPath(System.getProperty("env.class.path"), cpString);
                cpString = appendPath(System.getProperty("java.class.path"), cpString);
                cpString = appendPath(tagletPath, cpString);
                tagClassLoader = new URLClassLoader(pathToURLs(cpString));
            }
            customTagClass = tagClassLoader.loadClass(classname);
            Method meth = customTagClass.getMethod("register",
                    Map.class);
            Object[] list = customTags.values().toArray();
            Taglet lastTag = (list != null && list.length > 0)
                    ? (Taglet) list[list.length - 1] : null;
            meth.invoke(null, customTags);
            list = customTags.values().toArray();
            Object newLastTag = (list != null && list.length > 0)
                    ? list[list.length - 1] : null;
            if (lastTag != newLastTag) {


                message.notice("doclet.Notice_taglet_registered", classname);
                if (newLastTag != null) {
                    checkTaglet(newLastTag);
                }
            }
        } catch (Exception exc) {
            message.error("doclet.Error_taglet_not_registered", exc.getClass().getName(), classname);
        }
    }

    private String appendPath(String path1, String path2) {
        if (path1 == null || path1.length() == 0) {
            return path2 == null ? "." : path2;
        } else if (path2 == null || path2.length() == 0) {
            return path1;
        } else {
            return path1 + File.pathSeparator + path2;
        }
    }

    private URL[] pathToURLs(String path) {
        Set<URL> urls = new LinkedHashSet<URL>();
        for (String s : path.split(File.pathSeparator)) {
            if (s.isEmpty()) continue;
            try {
                urls.add(new File(s).getAbsoluteFile().toURI().toURL());
            } catch (MalformedURLException e) {
                message.error("doclet.MalformedURL", s);
            }
        }
        return urls.toArray(new URL[urls.size()]);
    }

    public void addNewSimpleCustomTag(String tagName, String header, String locations) {
        if (tagName == null || locations == null) {
            return;
        }
        Taglet tag = customTags.get(tagName);
        locations = locations.toLowerCase();
        if (tag == null || header != null) {
            customTags.remove(tagName);
            customTags.put(tagName, new SimpleTaglet(tagName, header, locations));
            if (locations != null && locations.indexOf('x') == -1) {
                checkTagName(tagName);
            }
        } else {

            customTags.remove(tagName);
            customTags.put(tagName, tag);
        }
    }

    private void checkTagName(String name) {
        if (standardTags.contains(name)) {
            overridenStandardTags.add(name);
        } else {
            if (name.indexOf('.') == -1) {
                potentiallyConflictingTags.add(name);
            }
            unseenCustomTags.add(name);
        }
    }

    private void checkTaglet(Object taglet) {
        if (taglet instanceof Taglet) {
            checkTagName(((Taglet) taglet).getName());
        } else if (taglet instanceof com.github.api.sun.tools.doclets.Taglet) {
            com.github.api.sun.tools.doclets.Taglet legacyTaglet = (com.github.api.sun.tools.doclets.Taglet) taglet;
            customTags.remove(legacyTaglet.getName());
            customTags.put(legacyTaglet.getName(), new LegacyTaglet(legacyTaglet));
            checkTagName(legacyTaglet.getName());
        } else {
            throw new IllegalArgumentException("Given object is not a taglet.");
        }
    }

    public void seenCustomTag(String name) {
        unseenCustomTags.remove(name);
    }

    public void checkTags(Doc doc, Tag[] tags, boolean areInlineTags) {
        if (tags == null) {
            return;
        }
        Taglet taglet;
        for (int i = 0; i < tags.length; i++) {
            String name = tags[i].name();
            if (name.length() > 0 && name.charAt(0) == '@') {
                name = name.substring(1);
            }
            if (!(standardTags.contains(name) || customTags.containsKey(name))) {
                if (standardTagsLowercase.contains(name.toLowerCase())) {
                    message.warning(tags[i].position(), "doclet.UnknownTagLowercase", tags[i].name());
                    continue;
                } else {
                    message.warning(tags[i].position(), "doclet.UnknownTag", tags[i].name());
                    continue;
                }
            }

            if ((taglet = customTags.get(name)) != null) {
                if (areInlineTags && !taglet.isInlineTag()) {
                    printTagMisuseWarn(taglet, tags[i], "inline");
                }
                if ((doc instanceof RootDoc) && !taglet.inOverview()) {
                    printTagMisuseWarn(taglet, tags[i], "overview");
                } else if ((doc instanceof PackageDoc) && !taglet.inPackage()) {
                    printTagMisuseWarn(taglet, tags[i], "package");
                } else if ((doc instanceof ClassDoc) && !taglet.inType()) {
                    printTagMisuseWarn(taglet, tags[i], "class");
                } else if ((doc instanceof ConstructorDoc) && !taglet.inConstructor()) {
                    printTagMisuseWarn(taglet, tags[i], "constructor");
                } else if ((doc instanceof FieldDoc) && !taglet.inField()) {
                    printTagMisuseWarn(taglet, tags[i], "field");
                } else if ((doc instanceof MethodDoc) && !taglet.inMethod()) {
                    printTagMisuseWarn(taglet, tags[i], "method");
                }
            }
        }
    }

    private void printTagMisuseWarn(Taglet taglet, Tag tag, String holderType) {
        Set<String> locationsSet = new LinkedHashSet<String>();
        if (taglet.inOverview()) {
            locationsSet.add("overview");
        }
        if (taglet.inPackage()) {
            locationsSet.add("package");
        }
        if (taglet.inType()) {
            locationsSet.add("class/interface");
        }
        if (taglet.inConstructor()) {
            locationsSet.add("constructor");
        }
        if (taglet.inField()) {
            locationsSet.add("field");
        }
        if (taglet.inMethod()) {
            locationsSet.add("method");
        }
        if (taglet.isInlineTag()) {
            locationsSet.add("inline text");
        }
        String[] locations = locationsSet.toArray(new String[]{});
        if (locations == null || locations.length == 0) {

            return;
        }
        StringBuilder combined_locations = new StringBuilder();
        for (int i = 0; i < locations.length; i++) {
            if (i > 0) {
                combined_locations.append(", ");
            }
            combined_locations.append(locations[i]);
        }
        message.warning(tag.position(), "doclet.tag_misuse",
                "@" + taglet.getName(), holderType, combined_locations.toString());
    }

    public Taglet[] getPackageCustomTaglets() {
        if (packageTags == null) {
            initCustomTagletArrays();
        }
        return packageTags;
    }

    public Taglet[] getTypeCustomTaglets() {
        if (typeTags == null) {
            initCustomTagletArrays();
        }
        return typeTags;
    }

    public Taglet[] getInlineCustomTaglets() {
        if (inlineTags == null) {
            initCustomTagletArrays();
        }
        return inlineTags;
    }

    public Taglet[] getFieldCustomTaglets() {
        if (fieldTags == null) {
            initCustomTagletArrays();
        }
        return fieldTags;
    }

    public Taglet[] getSerializedFormTaglets() {
        if (serializedFormTags == null) {
            initCustomTagletArrays();
        }
        return serializedFormTags;
    }

    public Taglet[] getCustomTaglets(Doc doc) {
        if (doc instanceof ConstructorDoc) {
            return getConstructorCustomTaglets();
        } else if (doc instanceof MethodDoc) {
            return getMethodCustomTaglets();
        } else if (doc instanceof FieldDoc) {
            return getFieldCustomTaglets();
        } else if (doc instanceof ClassDoc) {
            return getTypeCustomTaglets();
        } else if (doc instanceof PackageDoc) {
            return getPackageCustomTaglets();
        } else if (doc instanceof RootDoc) {
            return getOverviewCustomTaglets();
        }
        return null;
    }

    public Taglet[] getConstructorCustomTaglets() {
        if (constructorTags == null) {
            initCustomTagletArrays();
        }
        return constructorTags;
    }

    public Taglet[] getMethodCustomTaglets() {
        if (methodTags == null) {
            initCustomTagletArrays();
        }
        return methodTags;
    }

    public Taglet[] getOverviewCustomTaglets() {
        if (overviewTags == null) {
            initCustomTagletArrays();
        }
        return overviewTags;
    }

    private void initCustomTagletArrays() {
        Iterator<Taglet> it = customTags.values().iterator();
        ArrayList<Taglet> pTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> tTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> fTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> cTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> mTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> iTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> oTags = new ArrayList<Taglet>(customTags.size());
        ArrayList<Taglet> sTags = new ArrayList<Taglet>();
        Taglet current;
        while (it.hasNext()) {
            current = it.next();
            if (current.inPackage() && !current.isInlineTag()) {
                pTags.add(current);
            }
            if (current.inType() && !current.isInlineTag()) {
                tTags.add(current);
            }
            if (current.inField() && !current.isInlineTag()) {
                fTags.add(current);
            }
            if (current.inConstructor() && !current.isInlineTag()) {
                cTags.add(current);
            }
            if (current.inMethod() && !current.isInlineTag()) {
                mTags.add(current);
            }
            if (current.isInlineTag()) {
                iTags.add(current);
            }
            if (current.inOverview() && !current.isInlineTag()) {
                oTags.add(current);
            }
        }
        packageTags = pTags.toArray(new Taglet[]{});
        typeTags = tTags.toArray(new Taglet[]{});
        fieldTags = fTags.toArray(new Taglet[]{});
        constructorTags = cTags.toArray(new Taglet[]{});
        methodTags = mTags.toArray(new Taglet[]{});
        overviewTags = oTags.toArray(new Taglet[]{});
        inlineTags = iTags.toArray(new Taglet[]{});

        sTags.add(customTags.get("serialData"));
        sTags.add(customTags.get("throws"));
        if (!nosince)
            sTags.add(customTags.get("since"));
        sTags.add(customTags.get("see"));
        serializedFormTags = sTags.toArray(new Taglet[]{});
    }

    private void initStandardTaglets() {
        if (javafx) {
            initJavaFXTaglets();
        }
        Taglet temp;
        addStandardTaglet(new ParamTaglet());
        addStandardTaglet(new ReturnTaglet());
        addStandardTaglet(new ThrowsTaglet());
        addStandardTaglet(new SimpleTaglet("exception", null,
                SimpleTaglet.METHOD + SimpleTaglet.CONSTRUCTOR));
        addStandardTaglet(!nosince, new SimpleTaglet("since", message.getText("doclet.Since"),
                SimpleTaglet.ALL));
        addStandardTaglet(showversion, new SimpleTaglet("version", message.getText("doclet.Version"),
                SimpleTaglet.PACKAGE + SimpleTaglet.TYPE + SimpleTaglet.OVERVIEW));
        addStandardTaglet(showauthor, new SimpleTaglet("author", message.getText("doclet.Author"),
                SimpleTaglet.PACKAGE + SimpleTaglet.TYPE + SimpleTaglet.OVERVIEW));
        addStandardTaglet(new SimpleTaglet("serialData", message.getText("doclet.SerialData"),
                SimpleTaglet.EXCLUDED));
        customTags.put((temp = new SimpleTaglet("factory", message.getText("doclet.Factory"),
                SimpleTaglet.METHOD)).getName(), temp);
        addStandardTaglet(new SeeTaglet());

        addStandardTaglet(new DocRootTaglet());
        addStandardTaglet(new InheritDocTaglet());
        addStandardTaglet(new ValueTaglet());
        addStandardTaglet(new LiteralTaglet());
        addStandardTaglet(new CodeTaglet());


        standardTags.add("deprecated");
        standardTags.add("link");
        standardTags.add("linkplain");
        standardTags.add("serial");
        standardTags.add("serialField");
        standardTags.add("Text");
    }

    private void initJavaFXTaglets() {
        addStandardTaglet(new PropertyGetterTaglet());
        addStandardTaglet(new PropertySetterTaglet());
        addStandardTaglet(new SimpleTaglet("propertyDescription",
                message.getText("doclet.PropertyDescription"),
                SimpleTaglet.FIELD + SimpleTaglet.METHOD));
        addStandardTaglet(new SimpleTaglet("defaultValue", message.getText("doclet.DefaultValue"),
                SimpleTaglet.FIELD + SimpleTaglet.METHOD));
        addStandardTaglet(new SimpleTaglet("treatAsPrivate", null,
                SimpleTaglet.FIELD + SimpleTaglet.METHOD + SimpleTaglet.TYPE));
    }

    void addStandardTaglet(Taglet taglet) {
        String name = taglet.getName();
        customTags.put(name, taglet);
        standardTags.add(name);
    }

    void addStandardTaglet(boolean enable, Taglet taglet) {
        String name = taglet.getName();
        if (enable)
            customTags.put(name, taglet);
        standardTags.add(name);
    }

    private void initStandardTagsLowercase() {
        Iterator<String> it = standardTags.iterator();
        while (it.hasNext()) {
            standardTagsLowercase.add(it.next().toLowerCase());
        }
    }

    public boolean isKnownCustomTag(String tagName) {
        return customTags.containsKey(tagName);
    }

    public void printReport() {
        printReportHelper("doclet.Notice_taglet_conflict_warn", potentiallyConflictingTags);
        printReportHelper("doclet.Notice_taglet_overriden", overridenStandardTags);
        printReportHelper("doclet.Notice_taglet_unseen", unseenCustomTags);
    }

    private void printReportHelper(String noticeKey, Set<String> names) {
        if (names.size() > 0) {
            String[] namesArray = names.toArray(new String[]{});
            String result = " ";
            for (int i = 0; i < namesArray.length; i++) {
                result += "@" + namesArray[i];
                if (i + 1 < namesArray.length) {
                    result += ", ";
                }
            }
            message.notice(noticeKey, result);
        }
    }

    public Taglet getTaglet(String name) {
        if (name.indexOf("@") == 0) {
            return customTags.get(name.substring(1));
        } else {
            return customTags.get(name);
        }
    }
}
