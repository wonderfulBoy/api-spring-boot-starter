package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.WriterFactory;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocFile;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.github.api.sun.tools.doclets.internal.toolkit.util.MessageRetriever;
import com.github.api.sun.tools.doclint.DocLint;
import com.github.api.sun.tools.javac.file.JavacFileManager;
import com.github.api.sun.tools.javac.util.Context;
import com.github.api.sun.tools.javadoc.RootDocImpl;

import javax.tools.JavaFileManager;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class ConfigurationImpl extends Configuration {

    public static final String BUILD_DATE = System.getProperty("java.version");
    public final MessageRetriever standardmessage;
    private final String versionRBName = "com.github.api.sun.tools.javadoc.resources.version";
    public String header = "";
    public String packagesheader = "";
    public String footer = "";
    public String doctitle = "";
    public String windowtitle = "";
    public String top = "";
    public String bottom = "";
    public String helpfile = "";
    public String stylesheetfile = "";
    public String docrootparent = "";
    public boolean nohelp = false;
    public boolean splitindex = false;
    public boolean createindex = true;
    public boolean classuse = false;
    public boolean createtree = true;
    public boolean nodeprecatedlist = false;
    public boolean nonavbar = false;
    public boolean overview = false;
    public boolean createoverview = false;
    public Set<String> doclintOpts = new LinkedHashSet<String>();
    public DocPath topFile = DocPath.empty;

    public ClassDoc currentcd = null;
    private boolean nooverview = false;
    private ResourceBundle versionRB;
    private JavaFileManager fileManager;

    public ConfigurationImpl() {
        standardmessage = new MessageRetriever(this,
                "com.github.api.sun.tools.doclets.formats.html.resources.standard");
    }

    @Override
    public String getDocletSpecificBuildDate() {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return BUILD_DATE;
            }
        }
        try {
            return versionRB.getString("release");
        } catch (MissingResourceException e) {
            return BUILD_DATE;
        }
    }

    @Override
    public void setSpecificDocletOptions(String[][] options) {
        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-footer")) {
                footer = os[1];
            } else if (opt.equals("-header")) {
                header = os[1];
            } else if (opt.equals("-packagesheader")) {
                packagesheader = os[1];
            } else if (opt.equals("-doctitle")) {
                doctitle = os[1];
            } else if (opt.equals("-windowtitle")) {
                windowtitle = os[1];
            } else if (opt.equals("-top")) {
                top = os[1];
            } else if (opt.equals("-bottom")) {
                bottom = os[1];
            } else if (opt.equals("-helpfile")) {
                helpfile = os[1];
            } else if (opt.equals("-stylesheetfile")) {
                stylesheetfile = os[1];
            } else if (opt.equals("-charset")) {
                charset = os[1];
            } else if (opt.equals("-xdocrootparent")) {
                docrootparent = os[1];
            } else if (opt.equals("-nohelp")) {
                nohelp = true;
            } else if (opt.equals("-splitindex")) {
                splitindex = true;
            } else if (opt.equals("-noindex")) {
                createindex = false;
            } else if (opt.equals("-use")) {
                classuse = true;
            } else if (opt.equals("-notree")) {
                createtree = false;
            } else if (opt.equals("-nodeprecatedlist")) {
                nodeprecatedlist = true;
            } else if (opt.equals("-nonavbar")) {
                nonavbar = true;
            } else if (opt.equals("-nooverview")) {
                nooverview = true;
            } else if (opt.equals("-overview")) {
                overview = true;
            } else if (opt.equals("-xdoclint")) {
                doclintOpts.add(null);
            } else if (opt.startsWith("-xdoclint:")) {
                doclintOpts.add(opt.substring(opt.indexOf(":") + 1));
            }
        }
        if (root.specifiedClasses().length > 0) {
            Map<String, PackageDoc> map = new HashMap<String, PackageDoc>();
            PackageDoc pd;
            ClassDoc[] classes = root.classes();
            for (int i = 0; i < classes.length; i++) {
                pd = classes[i].containingPackage();
                if (!map.containsKey(pd.name())) {
                    map.put(pd.name(), pd);
                }
            }
        }
        setCreateOverview();
        setTopFile(root);
        if (root instanceof RootDocImpl) {
            ((RootDocImpl) root).initDocLint(doclintOpts, tagletManager.getCustomTagNames());
        }
    }

    public int optionLength(String option) {
        int result = -1;
        if ((result = super.optionLength(option)) > 0) {
            return result;
        }

        option = option.toLowerCase();
        if (option.equals("-nodeprecatedlist") ||
                option.equals("-noindex") ||
                option.equals("-notree") ||
                option.equals("-nohelp") ||
                option.equals("-splitindex") ||
                option.equals("-serialwarn") ||
                option.equals("-use") ||
                option.equals("-nonavbar") ||
                option.equals("-nooverview") ||
                option.equals("-xdoclint") ||
                option.startsWith("-xdoclint:")) {
            return 1;
        } else if (option.equals("-help")) {


            System.out.println(getText("doclet.usage"));
            return 1;
        } else if (option.equals("-x")) {


            System.out.println(getText("doclet.X.usage"));
            return 1;
        } else if (option.equals("-footer") ||
                option.equals("-header") ||
                option.equals("-packagesheader") ||
                option.equals("-doctitle") ||
                option.equals("-windowtitle") ||
                option.equals("-top") ||
                option.equals("-bottom") ||
                option.equals("-helpfile") ||
                option.equals("-stylesheetfile") ||
                option.equals("-charset") ||
                option.equals("-overview") ||
                option.equals("-xdocrootparent")) {
            return 2;
        } else {
            return 0;
        }
    }

    @Override
    public boolean validOptions(String[][] options,
                                DocErrorReporter reporter) {
        boolean helpfile = false;
        boolean nohelp = false;
        boolean overview = false;
        boolean nooverview = false;
        boolean splitindex = false;
        boolean noindex = false;

        if (!generalValidOptions(options, reporter)) {
            return false;
        }

        for (int oi = 0; oi < options.length; ++oi) {
            String[] os = options[oi];
            String opt = os[0].toLowerCase();
            if (opt.equals("-helpfile")) {
                if (nohelp == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                            "-helpfile", "-nohelp"));
                    return false;
                }
                if (helpfile == true) {
                    reporter.printError(getText("doclet.Option_reuse",
                            "-helpfile"));
                    return false;
                }
                DocFile help = DocFile.createFileForInput(this, os[1]);
                if (!help.exists()) {
                    reporter.printError(getText("doclet.File_not_found", os[1]));
                    return false;
                }
                helpfile = true;
            } else if (opt.equals("-nohelp")) {
                if (helpfile == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                            "-nohelp", "-helpfile"));
                    return false;
                }
                nohelp = true;
            } else if (opt.equals("-xdocrootparent")) {
                try {
                    new URL(os[1]);
                } catch (MalformedURLException e) {
                    reporter.printError(getText("doclet.MalformedURL", os[1]));
                    return false;
                }
            } else if (opt.equals("-overview")) {
                if (nooverview == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                            "-overview", "-nooverview"));
                    return false;
                }
                if (overview == true) {
                    reporter.printError(getText("doclet.Option_reuse",
                            "-overview"));
                    return false;
                }
                overview = true;
            } else if (opt.equals("-nooverview")) {
                if (overview == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                            "-nooverview", "-overview"));
                    return false;
                }
                nooverview = true;
            } else if (opt.equals("-splitindex")) {
                if (noindex == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                            "-splitindex", "-noindex"));
                    return false;
                }
                splitindex = true;
            } else if (opt.equals("-noindex")) {
                if (splitindex == true) {
                    reporter.printError(getText("doclet.Option_conflict",
                            "-noindex", "-splitindex"));
                    return false;
                }
                noindex = true;
            } else if (opt.startsWith("-xdoclint:")) {
                if (opt.contains("/")) {
                    reporter.printError(getText("doclet.Option_doclint_no_qualifiers"));
                    return false;
                }
                if (!DocLint.isValidOption(
                        opt.replace("-xdoclint:", DocLint.XMSGS_CUSTOM_PREFIX))) {
                    reporter.printError(getText("doclet.Option_doclint_invalid_arg"));
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public MessageRetriever getDocletSpecificMsg() {
        return standardmessage;
    }

    protected void setTopFile(RootDoc root) {
        if (!checkForDeprecation(root)) {
            return;
        }
        if (createoverview) {
            topFile = DocPaths.OVERVIEW_SUMMARY;
        } else {
            if (packages.length == 1 && packages[0].name().equals("")) {
                if (root.classes().length > 0) {
                    ClassDoc[] classarr = root.classes();
                    Arrays.sort(classarr);
                    ClassDoc cd = getValidClass(classarr);
                    topFile = DocPath.forClass(cd);
                }
            } else {
                topFile = DocPath.forPackage(packages[0]).resolve(DocPaths.PACKAGE_SUMMARY);
            }
        }
    }

    protected ClassDoc getValidClass(ClassDoc[] classarr) {
        if (!nodeprecated) {
            return classarr[0];
        }
        for (int i = 0; i < classarr.length; i++) {
            if (classarr[i].tags("deprecated").length == 0) {
                return classarr[i];
            }
        }
        return null;
    }

    protected boolean checkForDeprecation(RootDoc root) {
        ClassDoc[] classarr = root.classes();
        for (int i = 0; i < classarr.length; i++) {
            if (isGeneratedDoc(classarr[i])) {
                return true;
            }
        }
        return false;
    }

    protected void setCreateOverview() {
        if ((overview || packages.length > 1) && !nooverview) {
            createoverview = true;
        }
    }

    @Override
    public WriterFactory getWriterFactory() {
        return new WriterFactoryImpl(this);
    }

    @Override
    public Comparator<ProgramElementDoc> getMemberComparator() {
        return null;
    }

    @Override
    public Locale getLocale() {
        if (root instanceof RootDocImpl)
            return ((RootDocImpl) root).getLocale();
        else
            return Locale.getDefault();
    }

    @Override
    public JavaFileManager getFileManager() {
        if (fileManager == null) {
            if (root instanceof RootDocImpl)
                fileManager = ((RootDocImpl) root).getFileManager();
            else
                fileManager = new JavacFileManager(new Context(), false, null);
        }
        return fileManager;
    }

    @Override
    public boolean showMessage(SourcePosition pos, String key) {
        if (root instanceof RootDocImpl) {
            return pos == null || ((RootDocImpl) root).showTagMessages();
        }
        return true;
    }

    @Override
    public Content newContent() {
        return new ContentBuilder();
    }
}
