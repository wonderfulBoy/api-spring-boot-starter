package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.builders.BuilderFactory;
import com.sun.tools.doclets.internal.toolkit.taglets.TagletManager;
import com.sun.tools.doclets.internal.toolkit.util.*;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.javac.sym.Profiles;

import javax.tools.JavaFileManager;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class Configuration {

    private static final String DEFAULT_BUILDER_XML = "resources/doclet.xml";
    public final MetaKeywords metakeywords = new MetaKeywords(this);
    public final Group group = new Group(this);
    public final Extern extern = new Extern(this);
    public TagletManager tagletManager;
    public String builderXMLPath;
    public String tagletpath = "";
    public boolean serialwarn = false;
    public int sourcetab;
    public String tabSpaces;
    public boolean linksource = false;
    public boolean nosince = false;
    public boolean copydocfilesubdirs = false;
    public String charset = "";
    public boolean keywords = false;
    public RootDoc root;
    public String destDirName = "";
    public String docFileDestDirName = "";
    public String docencoding = null;
    public boolean nocomment = false;
    public String encoding = null;
    public boolean showauthor = false;
    public boolean javafx = false;
    public boolean showversion = false;
    public String sourcepath = "";
    public String profilespath = "";
    public boolean showProfiles = false;
    public boolean nodeprecated = false;
    public ClassDocCatalog classDocCatalog;
    public MessageRetriever message = null;
    public boolean notimestamp = false;
    public Profiles profiles;
    public Map<String, PackageDoc[]> profilePackages;
    public PackageDoc[] packages;
    protected BuilderFactory builderFactory;
    protected Set<String> excludedDocFileDirs;
    protected Set<String> excludedQualifiers;

    public Configuration() {
        message =
                new MessageRetriever(this,
                        "com.sun.tools.doclets.internal.toolkit.resources.doclets");
        excludedDocFileDirs = new HashSet<String>();
        excludedQualifiers = new HashSet<String>();
        setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
    }

    public static String addTrailingFileSep(String path) {
        String fs = System.getProperty("file.separator");
        String dblfs = fs + fs;
        int indexDblfs;
        while ((indexDblfs = path.indexOf(dblfs, 1)) >= 0) {
            path = path.substring(0, indexDblfs) +
                    path.substring(indexDblfs + fs.length());
        }
        if (!path.endsWith(fs))
            path += fs;
        return path;
    }

    public abstract String getDocletSpecificBuildDate();

    public abstract void setSpecificDocletOptions(String[][] options) throws Fault;

    public abstract MessageRetriever getDocletSpecificMsg();

    public BuilderFactory getBuilderFactory() {
        if (builderFactory == null) {
            builderFactory = new BuilderFactory(this);
        }
        return builderFactory;
    }

    public int optionLength(String option) {
        option = option.toLowerCase();
        if (option.equals("-author") ||
                option.equals("-docfilessubdirs") ||
                option.equals("-javafx") ||
                option.equals("-keywords") ||
                option.equals("-linksource") ||
                option.equals("-nocomment") ||
                option.equals("-nodeprecated") ||
                option.equals("-nosince") ||
                option.equals("-notimestamp") ||
                option.equals("-quiet") ||
                option.equals("-xnodate") ||
                option.equals("-version")) {
            return 1;
        } else if (option.equals("-d") ||
                option.equals("-docencoding") ||
                option.equals("-encoding") ||
                option.equals("-excludedocfilessubdir") ||
                option.equals("-link") ||
                option.equals("-sourcetab") ||
                option.equals("-noqualifier") ||
                option.equals("-output") ||
                option.equals("-sourcepath") ||
                option.equals("-tag") ||
                option.equals("-taglet") ||
                option.equals("-tagletpath") ||
                option.equals("-xprofilespath")) {
            return 2;
        } else if (option.equals("-group") ||
                option.equals("-linkoffline")) {
            return 3;
        } else {
            return -1;
        }
    }

    public abstract boolean validOptions(String[][] options,
                                         DocErrorReporter reporter);

    private void initProfiles() throws IOException {
        if (profilespath.isEmpty())
            return;
        profiles = Profiles.read(new File(profilespath));


        Map<Profile, List<PackageDoc>> interimResults =
                new EnumMap<Profile, List<PackageDoc>>(Profile.class);
        for (Profile p : Profile.values())
            interimResults.put(p, new ArrayList<PackageDoc>());
        for (PackageDoc pkg : packages) {
            if (nodeprecated && Util.isDeprecated(pkg)) {
                continue;
            }


            int i = profiles.getProfile(pkg.name().replace(".", "/") + "/*");
            Profile p = Profile.lookup(i);
            if (p != null) {
                List<PackageDoc> pkgs = interimResults.get(p);
                pkgs.add(pkg);
            }
        }

        profilePackages = new HashMap<String, PackageDoc[]>();
        List<PackageDoc> prev = Collections.emptyList();
        int size;
        for (Map.Entry<Profile, List<PackageDoc>> e : interimResults.entrySet()) {
            Profile p = e.getKey();
            List<PackageDoc> pkgs = e.getValue();
            pkgs.addAll(prev);
            Collections.sort(pkgs);
            size = pkgs.size();


            if (size > 0)
                profilePackages.put(p.name, pkgs.toArray(new PackageDoc[pkgs.size()]));
            prev = pkgs;
        }


        showProfiles = !prev.isEmpty();
    }

    private void initPackageArray() {
        Set<PackageDoc> set = new HashSet<PackageDoc>(Arrays.asList(root.specifiedPackages()));
        ClassDoc[] classes = root.specifiedClasses();
        for (int i = 0; i < classes.length; i++) {
            set.add(classes[i].containingPackage());
        }
        ArrayList<PackageDoc> results = new ArrayList<PackageDoc>(set);
        Collections.sort(results);
        packages = results.toArray(new PackageDoc[]{});
    }

    public void setOptions(String[][] options) throws Fault {
        LinkedHashSet<String[]> customTagStrs = new LinkedHashSet<String[]>();


        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-d")) {
                destDirName = addTrailingFileSep(os[1]);
                docFileDestDirName = destDirName;
                ensureOutputDirExists();
                break;
            }
        }
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-docfilessubdirs")) {
                copydocfilesubdirs = true;
            } else if (opt.equals("-docencoding")) {
                docencoding = os[1];
            } else if (opt.equals("-encoding")) {
                encoding = os[1];
            } else if (opt.equals("-author")) {
                showauthor = true;
            } else if (opt.equals("-javafx")) {
                javafx = true;
            } else if (opt.equals("-nosince")) {
                nosince = true;
            } else if (opt.equals("-version")) {
                showversion = true;
            } else if (opt.equals("-nodeprecated")) {
                nodeprecated = true;
            } else if (opt.equals("-sourcepath")) {
                sourcepath = os[1];
            } else if ((opt.equals("-classpath") || opt.equals("-cp")) &&
                    sourcepath.length() == 0) {
                sourcepath = os[1];
            } else if (opt.equals("-excludedocfilessubdir")) {
                addToSet(excludedDocFileDirs, os[1]);
            } else if (opt.equals("-noqualifier")) {
                addToSet(excludedQualifiers, os[1]);
            } else if (opt.equals("-linksource")) {
                linksource = true;
            } else if (opt.equals("-sourcetab")) {
                linksource = true;
                try {
                    setTabWidth(Integer.parseInt(os[1]));
                } catch (NumberFormatException e) {


                    sourcetab = -1;
                }
                if (sourcetab <= 0) {
                    message.warning("doclet.sourcetab_warning");
                    setTabWidth(DocletConstants.DEFAULT_TAB_STOP_LENGTH);
                }
            } else if (opt.equals("-notimestamp")) {
                notimestamp = true;
            } else if (opt.equals("-nocomment")) {
                nocomment = true;
            } else if (opt.equals("-tag") || opt.equals("-taglet")) {
                customTagStrs.add(os);
            } else if (opt.equals("-tagletpath")) {
                tagletpath = os[1];
            } else if (opt.equals("-xprofilespath")) {
                profilespath = os[1];
            } else if (opt.equals("-keywords")) {
                keywords = true;
            } else if (opt.equals("-serialwarn")) {
                serialwarn = true;
            } else if (opt.equals("-group")) {
                group.checkPackageGroups(os[1], os[2]);
            } else if (opt.equals("-link")) {
                String url = os[1];
                extern.link(url, url, root, false);
            } else if (opt.equals("-linkoffline")) {
                String url = os[1];
                String pkglisturl = os[2];
                extern.link(url, pkglisturl, root, true);
            }
        }
        if (sourcepath.length() == 0) {
            sourcepath = System.getProperty("env.class.path") == null ? "" :
                    System.getProperty("env.class.path");
        }
        if (docencoding == null) {
            docencoding = encoding;
        }
        classDocCatalog = new ClassDocCatalog(root.specifiedClasses(), this);
        initTagletManager(customTagStrs);
    }

    public void setOptions() throws Fault {
        initPackageArray();
        setOptions(root.options());
        try {
            initProfiles();
        } catch (Exception e) {
            throw new DocletAbortException(e);
        }
        setSpecificDocletOptions(root.options());
    }

    private void ensureOutputDirExists() throws Fault {
        DocFile destDir = DocFile.createFileForDirectory(this, destDirName);
        if (!destDir.exists()) {

            root.printNotice(getText("doclet.dest_dir_create", destDirName));
            destDir.mkdirs();
        } else if (!destDir.isDirectory()) {
            throw new Fault(getText(
                    "doclet.destination_directory_not_directory_0",
                    destDir.getPath()));
        } else if (!destDir.canWrite()) {
            throw new Fault(getText(
                    "doclet.destination_directory_not_writable_0",
                    destDir.getPath()));
        }
    }

    private void initTagletManager(Set<String[]> customTagStrs) {
        tagletManager = tagletManager == null ?
                new TagletManager(nosince, showversion, showauthor, javafx, message) :
                tagletManager;
        String[] args;
        for (Iterator<String[]> it = customTagStrs.iterator(); it.hasNext(); ) {
            args = it.next();
            if (args[0].equals("-taglet")) {
                tagletManager.addCustomTag(args[1], getFileManager(), tagletpath);
                continue;
            }
            String[] tokens = tokenize(args[1],
                    TagletManager.SIMPLE_TAGLET_OPT_SEPARATOR, 3);
            if (tokens.length == 1) {
                String tagName = args[1];
                if (tagletManager.isKnownCustomTag(tagName)) {

                    tagletManager.addNewSimpleCustomTag(tagName, null, "");
                } else {

                    StringBuilder heading = new StringBuilder(tagName + ":");
                    heading.setCharAt(0, Character.toUpperCase(tagName.charAt(0)));
                    tagletManager.addNewSimpleCustomTag(tagName, heading.toString(), "a");
                }
            } else if (tokens.length == 2) {

                tagletManager.addNewSimpleCustomTag(tokens[0], tokens[1], "");
            } else if (tokens.length >= 3) {
                tagletManager.addNewSimpleCustomTag(tokens[0], tokens[2], tokens[1]);
            } else {
                message.error("doclet.Error_invalid_custom_tag_argument", args[1]);
            }
        }
    }

    private String[] tokenize(String s, char separator, int maxTokens) {
        List<String> tokens = new ArrayList<String>();
        StringBuilder token = new StringBuilder();
        boolean prevIsEscapeChar = false;
        for (int i = 0; i < s.length(); i += Character.charCount(i)) {
            int currentChar = s.codePointAt(i);
            if (prevIsEscapeChar) {

                token.appendCodePoint(currentChar);
                prevIsEscapeChar = false;
            } else if (currentChar == separator && tokens.size() < maxTokens - 1) {

                tokens.add(token.toString());
                token = new StringBuilder();
            } else if (currentChar == '\\') {

                prevIsEscapeChar = true;
            } else {

                token.appendCodePoint(currentChar);
            }
        }
        if (token.length() > 0) {
            tokens.add(token.toString());
        }
        return tokens.toArray(new String[]{});
    }

    private void addToSet(Set<String> s, String str) {
        StringTokenizer st = new StringTokenizer(str, ":");
        String current;
        while (st.hasMoreTokens()) {
            current = st.nextToken();
            s.add(current);
        }
    }

    public boolean generalValidOptions(String[][] options,
                                       DocErrorReporter reporter) {
        boolean docencodingfound = false;
        String encoding = "";
        for (int oi = 0; oi < options.length; oi++) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-docencoding")) {
                docencodingfound = true;
                if (!checkOutputFileEncoding(os[1], reporter)) {
                    return false;
                }
            } else if (opt.equals("-encoding")) {
                encoding = os[1];
            }
        }
        if (!docencodingfound && encoding.length() > 0) {
            return checkOutputFileEncoding(encoding, reporter);
        }
        return true;
    }

    public boolean shouldDocumentProfile(String profileName) {
        return profilePackages.containsKey(profileName);
    }

    private boolean checkOutputFileEncoding(String docencoding,
                                            DocErrorReporter reporter) {
        OutputStream ost = new ByteArrayOutputStream();
        OutputStreamWriter osw = null;
        try {
            osw = new OutputStreamWriter(ost, docencoding);
        } catch (UnsupportedEncodingException exc) {
            reporter.printError(getText("doclet.Encoding_not_supported",
                    docencoding));
            return false;
        } finally {
            try {
                if (osw != null) {
                    osw.close();
                }
            } catch (IOException exc) {
            }
        }
        return true;
    }

    public boolean shouldExcludeDocFileDir(String docfilesubdir) {
        return excludedDocFileDirs.contains(docfilesubdir);
    }

    public boolean shouldExcludeQualifier(String qualifier) {
        if (excludedQualifiers.contains("all") ||
                excludedQualifiers.contains(qualifier) ||
                excludedQualifiers.contains(qualifier + ".*")) {
            return true;
        } else {
            int index = -1;
            while ((index = qualifier.indexOf(".", index + 1)) != -1) {
                if (excludedQualifiers.contains(qualifier.substring(0, index + 1) + "*")) {
                    return true;
                }
            }
            return false;
        }
    }

    public String getClassName(ClassDoc cd) {
        PackageDoc pd = cd.containingPackage();
        if (pd != null && shouldExcludeQualifier(cd.containingPackage().name())) {
            return cd.name();
        } else {
            return cd.qualifiedName();
        }
    }

    public String getText(String key) {
        try {

            return getDocletSpecificMsg().getText(key);
        } catch (Exception e) {

            return message.getText(key);
        }
    }

    public String getText(String key, String a1) {
        try {

            return getDocletSpecificMsg().getText(key, a1);
        } catch (Exception e) {

            return message.getText(key, a1);
        }
    }

    public String getText(String key, String a1, String a2) {
        try {

            return getDocletSpecificMsg().getText(key, a1, a2);
        } catch (Exception e) {

            return message.getText(key, a1, a2);
        }
    }

    public String getText(String key, String a1, String a2, String a3) {
        try {

            return getDocletSpecificMsg().getText(key, a1, a2, a3);
        } catch (Exception e) {

            return message.getText(key, a1, a2, a3);
        }
    }

    public abstract Content newContent();

    public Content getResource(String key) {
        Content c = newContent();
        c.addContent(getText(key));
        return c;
    }

    public Content getResource(String key, Object o) {
        return getResource(key, o, null, null);
    }

    public Content getResource(String key, Object o1, Object o2) {
        return getResource(key, o1, o2, null);
    }

    public Content getResource(String key, Object o0, Object o1, Object o2) {
        Content c = newContent();
        Pattern p = Pattern.compile("\\{([012])\\}");
        String text = getText(key);
        Matcher m = p.matcher(text);
        int start = 0;
        while (m.find(start)) {
            c.addContent(text.substring(start, m.start()));
            Object o = null;
            switch (m.group(1).charAt(0)) {
                case '0':
                    o = o0;
                    break;
                case '1':
                    o = o1;
                    break;
                case '2':
                    o = o2;
                    break;
            }
            if (o == null) {
                c.addContent("{" + m.group(1) + "}");
            } else if (o instanceof String) {
                c.addContent((String) o);
            } else if (o instanceof Content) {
                c.addContent((Content) o);
            }
            start = m.end();
        }
        c.addContent(text.substring(start));
        return c;
    }

    public boolean isGeneratedDoc(ClassDoc cd) {
        if (!nodeprecated) {
            return true;
        }
        return !(Util.isDeprecated(cd) || Util.isDeprecated(cd.containingPackage()));
    }

    public abstract WriterFactory getWriterFactory();

    public InputStream getBuilderXML() throws IOException {
        return builderXMLPath == null ?
                Configuration.class.getResourceAsStream(DEFAULT_BUILDER_XML) :
                DocFile.createFileForInput(this, builderXMLPath).openInputStream();
    }

    public abstract Locale getLocale();

    public abstract JavaFileManager getFileManager();

    public abstract Comparator<ProgramElementDoc> getMemberComparator();

    private void setTabWidth(int n) {
        sourcetab = n;
        tabSpaces = String.format("%" + n + "s", "");
    }

    public abstract boolean showMessage(SourcePosition pos, String key);

    public static class Fault extends Exception {
        private static final long serialVersionUID = 0;

        Fault(String msg) {
            super(msg);
        }

        Fault(String msg, Exception cause) {
            super(msg, cause);
        }
    }
}
