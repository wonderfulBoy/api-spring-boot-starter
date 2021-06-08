package com.sun.tools.javac.main;

import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.PrefixKind;
import com.sun.tools.javac.util.Log.WriterKind;
import com.sun.tools.javac.util.Options;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

import static com.sun.tools.javac.main.Option.ChoiceKind.ANYOF;
import static com.sun.tools.javac.main.Option.ChoiceKind.ONEOF;
import static com.sun.tools.javac.main.Option.OptionGroup.*;
import static com.sun.tools.javac.main.Option.OptionKind.*;

public enum Option {
    G("-g", "opt.g", STANDARD, BASIC),
    G_NONE("-g:none", "opt.g.none", STANDARD, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            helper.put("-g:", "none");
            return false;
        }
    },
    G_CUSTOM("-g:", "opt.g.lines.vars.source",
            STANDARD, BASIC, ANYOF, "lines", "vars", "source"),
    XLINT("-Xlint", "opt.Xlint", EXTENDED, BASIC),
    XLINT_CUSTOM("-Xlint:", "opt.Xlint.suboptlist",
            EXTENDED, BASIC, ANYOF, getXLintChoices()),
    XDOCLINT("-Xdoclint", "opt.Xdoclint", EXTENDED, BASIC),
    XDOCLINT_CUSTOM("-Xdoclint:", "opt.Xdoclint.subopts", "opt.Xdoclint.custom", EXTENDED, BASIC) {
        @Override
        public boolean matches(String option) {
            return DocLint.isValidOption(
                    option.replace(XDOCLINT_CUSTOM.text, DocLint.XMSGS_CUSTOM_PREFIX));
        }

        @Override
        public boolean process(OptionHelper helper, String option) {
            String prev = helper.get(XDOCLINT_CUSTOM);
            String next = (prev == null) ? option : (prev + " " + option);
            helper.put(XDOCLINT_CUSTOM.text, next);
            return false;
        }
    },

    NOWARN("-nowarn", "opt.nowarn", STANDARD, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            helper.put("-Xlint:none", option);
            return false;
        }
    },
    VERBOSE("-verbose", "opt.verbose", STANDARD, BASIC),

    DEPRECATION("-deprecation", "opt.deprecation", STANDARD, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            helper.put("-Xlint:deprecation", option);
            return false;
        }
    },
    CLASSPATH("-classpath", "opt.arg.path", "opt.classpath", STANDARD, FILEMANAGER),
    CP("-cp", "opt.arg.path", "opt.classpath", STANDARD, FILEMANAGER) {
        @Override
        public boolean process(OptionHelper helper, String option, String arg) {
            return super.process(helper, "-classpath", arg);
        }
    },
    SOURCEPATH("-sourcepath", "opt.arg.path", "opt.sourcepath", STANDARD, FILEMANAGER),
    BOOTCLASSPATH("-bootclasspath", "opt.arg.path", "opt.bootclasspath", STANDARD, FILEMANAGER) {
        @Override
        public boolean process(OptionHelper helper, String option, String arg) {
            helper.remove("-Xbootclasspath/p:");
            helper.remove("-Xbootclasspath/a:");
            return super.process(helper, option, arg);
        }
    },
    XBOOTCLASSPATH_PREPEND("-Xbootclasspath/p:", "opt.arg.path", "opt.Xbootclasspath.p", EXTENDED, FILEMANAGER),
    XBOOTCLASSPATH_APPEND("-Xbootclasspath/a:", "opt.arg.path", "opt.Xbootclasspath.a", EXTENDED, FILEMANAGER),
    XBOOTCLASSPATH("-Xbootclasspath:", "opt.arg.path", "opt.bootclasspath", EXTENDED, FILEMANAGER) {
        @Override
        public boolean process(OptionHelper helper, String option, String arg) {
            helper.remove("-Xbootclasspath/p:");
            helper.remove("-Xbootclasspath/a:");
            return super.process(helper, "-bootclasspath", arg);
        }
    },
    EXTDIRS("-extdirs", "opt.arg.dirs", "opt.extdirs", STANDARD, FILEMANAGER),
    DJAVA_EXT_DIRS("-Djava.ext.dirs=", "opt.arg.dirs", "opt.extdirs", EXTENDED, FILEMANAGER) {
        @Override
        public boolean process(OptionHelper helper, String option, String arg) {
            return super.process(helper, "-extdirs", arg);
        }
    },
    ENDORSEDDIRS("-endorseddirs", "opt.arg.dirs", "opt.endorseddirs", STANDARD, FILEMANAGER),
    DJAVA_ENDORSED_DIRS("-Djava.endorsed.dirs=", "opt.arg.dirs", "opt.endorseddirs", EXTENDED, FILEMANAGER) {
        @Override
        public boolean process(OptionHelper helper, String option, String arg) {
            return super.process(helper, "-endorseddirs", arg);
        }
    },
    PROC("-proc:", "opt.proc.none.only", STANDARD, BASIC, ONEOF, "none", "only"),
    PROCESSOR("-processor", "opt.arg.class.list", "opt.processor", STANDARD, BASIC),
    PROCESSORPATH("-processorpath", "opt.arg.path", "opt.processorpath", STANDARD, FILEMANAGER),
    PARAMETERS("-parameters", "opt.parameters", STANDARD, BASIC),
    D("-d", "opt.arg.directory", "opt.d", STANDARD, FILEMANAGER),
    S("-s", "opt.arg.directory", "opt.sourceDest", STANDARD, FILEMANAGER),
    H("-h", "opt.arg.directory", "opt.headerDest", STANDARD, FILEMANAGER),
    IMPLICIT("-implicit:", "opt.implicit", STANDARD, BASIC, ONEOF, "none", "class"),
    ENCODING("-encoding", "opt.arg.encoding", "opt.encoding", STANDARD, FILEMANAGER) {
        @Override
        public boolean process(OptionHelper helper, String option, String operand) {
            return super.process(helper, option, operand);
        }
    },
    SOURCE("-source", "opt.arg.release", "opt.source", STANDARD, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option, String operand) {
            Source source = Source.lookup(operand);
            if (source == null) {
                helper.error("err.invalid.source", operand);
                return true;
            }
            return super.process(helper, option, operand);
        }
    },
    TARGET("-target", "opt.arg.release", "opt.target", STANDARD, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option, String operand) {
            Target target = Target.lookup(operand);
            if (target == null) {
                helper.error("err.invalid.target", operand);
                return true;
            }
            return super.process(helper, option, operand);
        }
    },
    PROFILE("-profile", "opt.arg.profile", "opt.profile", STANDARD, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option, String operand) {
            Profile profile = Profile.lookup(operand);
            if (profile == null) {
                helper.error("err.invalid.profile", operand);
                return true;
            }
            return super.process(helper, option, operand);
        }
    },
    VERSION("-version", "opt.version", STANDARD, INFO) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            Log log = helper.getLog();
            String ownName = helper.getOwnName();
            log.printLines(PrefixKind.JAVAC, "version", ownName, JavaCompiler.version());
            return super.process(helper, option);
        }
    },
    FULLVERSION("-fullversion", null, HIDDEN, INFO) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            Log log = helper.getLog();
            String ownName = helper.getOwnName();
            log.printLines(PrefixKind.JAVAC, "fullVersion", ownName, JavaCompiler.fullVersion());
            return super.process(helper, option);
        }
    },
    DIAGS("-XDdiags=", null, HIDDEN, INFO) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            option = option.substring(option.indexOf('=') + 1);
            String diagsOption = option.contains("%") ?
                    "-XDdiagsFormat=" :
                    "-XDdiags=";
            diagsOption += option;
            if (XD.matches(diagsOption))
                return XD.process(helper, diagsOption);
            else
                return false;
        }
    },
    HELP("-help", "opt.help", STANDARD, INFO) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            Log log = helper.getLog();
            String ownName = helper.getOwnName();
            log.printLines(PrefixKind.JAVAC, "msg.usage.header", ownName);
            for (Option o : getJavaCompilerOptions()) {
                o.help(log, OptionKind.STANDARD);
            }
            log.printNewline();
            return super.process(helper, option);
        }
    },
    A("-A", "opt.arg.key.equals.value", "opt.A", STANDARD, BASIC, true) {
        @Override
        public boolean matches(String arg) {
            return arg.startsWith("-A");
        }

        @Override
        public boolean hasArg() {
            return false;
        }


        @Override
        public boolean process(OptionHelper helper, String option) {
            int argLength = option.length();
            if (argLength == 2) {
                helper.error("err.empty.A.argument");
                return true;
            }
            int sepIndex = option.indexOf('=');
            String key = option.substring(2, (sepIndex != -1 ? sepIndex : argLength));
            if (!JavacProcessingEnvironment.isValidOptionName(key)) {
                helper.error("err.invalid.A.key", option);
                return true;
            }
            return process(helper, option, option);
        }
    },
    X("-X", "opt.X", STANDARD, INFO) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            Log log = helper.getLog();
            for (Option o : getJavaCompilerOptions()) {
                o.help(log, OptionKind.EXTENDED);
            }
            log.printNewline();
            log.printLines(PrefixKind.JAVAC, "msg.usage.nonstandard.footer");
            return super.process(helper, option);
        }
    },


    J("-J", "opt.arg.flag", "opt.J", STANDARD, INFO, true) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            throw new AssertionError
                    ("the -J flag should be caught by the launcher.");
        }
    },
    MOREINFO("-moreinfo", null, HIDDEN, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            Type.moreInfo = true;
            return super.process(helper, option);
        }
    },

    WERROR("-Werror", "opt.Werror", STANDARD, BASIC),


    PROMPT("-prompt", null, HIDDEN, BASIC),

    DOE("-doe", null, HIDDEN, BASIC),

    PRINTSOURCE("-printsource", null, HIDDEN, BASIC),

    WARNUNCHECKED("-warnunchecked", null, HIDDEN, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            helper.put("-Xlint:unchecked", option);
            return false;
        }
    },
    XMAXERRS("-Xmaxerrs", "opt.arg.number", "opt.maxerrs", EXTENDED, BASIC),
    XMAXWARNS("-Xmaxwarns", "opt.arg.number", "opt.maxwarns", EXTENDED, BASIC),
    XSTDOUT("-Xstdout", "opt.arg.file", "opt.Xstdout", EXTENDED, INFO) {
        @Override
        public boolean process(OptionHelper helper, String option, String arg) {
            try {
                Log log = helper.getLog();

                log.setWriters(new PrintWriter(new FileWriter(arg), true));
            } catch (java.io.IOException e) {
                helper.error("err.error.writing.file", arg, e);
                return true;
            }
            return super.process(helper, option, arg);
        }
    },
    XPRINT("-Xprint", "opt.print", EXTENDED, BASIC),
    XPRINTROUNDS("-XprintRounds", "opt.printRounds", EXTENDED, BASIC),
    XPRINTPROCESSORINFO("-XprintProcessorInfo", "opt.printProcessorInfo", EXTENDED, BASIC),
    XPREFER("-Xprefer:", "opt.prefer", EXTENDED, BASIC, ONEOF, "source", "newer"),

    XPKGINFO("-Xpkginfo:", "opt.pkginfo", EXTENDED, BASIC, ONEOF, "always", "legacy", "nonempty"),

    O("-O", null, HIDDEN, BASIC),

    XJCOV("-Xjcov", null, HIDDEN, BASIC),
    PLUGIN("-Xplugin:", "opt.arg.plugin", "opt.plugin", EXTENDED, BASIC) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            String p = option.substring(option.indexOf(':') + 1);
            String prev = helper.get(PLUGIN);
            helper.put(PLUGIN.text, (prev == null) ? p : prev + '\0' + p.trim());
            return false;
        }
    },
    XDIAGS("-Xdiags:", "opt.diags", EXTENDED, BASIC, ONEOF, "compact", "verbose"),

    XD("-XD", null, HIDDEN, BASIC) {
        @Override
        public boolean matches(String s) {
            return s.startsWith(text);
        }

        @Override
        public boolean process(OptionHelper helper, String option) {
            option = option.substring(text.length());
            int eq = option.indexOf('=');
            String key = (eq < 0) ? option : option.substring(0, eq);
            String value = (eq < 0) ? option : option.substring(eq + 1);
            helper.put(key, value);
            return false;
        }
    },


    AT("@", "opt.arg.file", "opt.AT", STANDARD, INFO, true) {
        @Override
        public boolean process(OptionHelper helper, String option) {
            throw new AssertionError("the @ flag should be caught by CommandLine.");
        }
    },

    SOURCEFILE("sourcefile", null, HIDDEN, INFO) {
        @Override
        public boolean matches(String s) {
            return s.endsWith(".java")
                    || SourceVersion.isName(s);
        }

        @Override
        public boolean process(OptionHelper helper, String option) {
            if (option.endsWith(".java")) {
                File f = new File(option);
                if (!f.exists()) {
                    helper.error("err.file.not.found", f);
                    return true;
                }
                if (!f.isFile()) {
                    helper.error("err.file.not.file", f);
                    return true;
                }
                helper.addFile(f);
            } else {
                helper.addClassName(option);
            }
            return false;
        }
    };

    public final String text;
    final OptionKind kind;
    final OptionGroup group;
    final String argsNameKey;
    final String descrKey;
    final boolean hasSuffix;
    final ChoiceKind choiceKind;
    final Map<String, Boolean> choices;

    Option(String text, String descrKey,
           OptionKind kind, OptionGroup group) {
        this(text, null, descrKey, kind, group, null, null, false);
    }

    Option(String text, String argsNameKey, String descrKey,
           OptionKind kind, OptionGroup group) {
        this(text, argsNameKey, descrKey, kind, group, null, null, false);
    }

    Option(String text, String argsNameKey, String descrKey,
           OptionKind kind, OptionGroup group, boolean doHasSuffix) {
        this(text, argsNameKey, descrKey, kind, group, null, null, doHasSuffix);
    }

    Option(String text, String descrKey,
           OptionKind kind, OptionGroup group,
           ChoiceKind choiceKind, Map<String, Boolean> choices) {
        this(text, null, descrKey, kind, group, choiceKind, choices, false);
    }

    Option(String text, String descrKey,
           OptionKind kind, OptionGroup group,
           ChoiceKind choiceKind, String... choices) {
        this(text, null, descrKey, kind, group, choiceKind,
                createChoices(choices), false);
    }

    Option(String text, String argsNameKey, String descrKey,
           OptionKind kind, OptionGroup group,
           ChoiceKind choiceKind, Map<String, Boolean> choices,
           boolean doHasSuffix) {
        this.text = text;
        this.argsNameKey = argsNameKey;
        this.descrKey = descrKey;
        this.kind = kind;
        this.group = group;
        this.choiceKind = choiceKind;
        this.choices = choices;
        char lastChar = text.charAt(text.length() - 1);
        this.hasSuffix = doHasSuffix || lastChar == ':' || lastChar == '=';
    }

    private static Map<String, Boolean> createChoices(String... choices) {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();
        for (String c : choices)
            map.put(c, false);
        return map;
    }

    private static Map<String, Boolean> getXLintChoices() {
        Map<String, Boolean> choices = new LinkedHashMap<String, Boolean>();
        choices.put("all", false);
        for (Lint.LintCategory c : Lint.LintCategory.values())
            choices.put(c.option, c.hidden);
        for (Lint.LintCategory c : Lint.LintCategory.values())
            choices.put("-" + c.option, c.hidden);
        choices.put("none", false);
        return choices;
    }

    static Set<Option> getJavaCompilerOptions() {
        return EnumSet.allOf(Option.class);
    }

    public static Set<Option> getJavacFileManagerOptions() {
        return getOptions(EnumSet.of(FILEMANAGER));
    }

    public static Set<Option> getJavacToolOptions() {
        return getOptions(EnumSet.of(BASIC));
    }

    static Set<Option> getOptions(Set<OptionGroup> desired) {
        Set<Option> options = EnumSet.noneOf(Option.class);
        for (Option option : Option.values())
            if (desired.contains(option.group))
                options.add(option);
        return Collections.unmodifiableSet(options);
    }

    public String getText() {
        return text;
    }

    public OptionKind getKind() {
        return kind;
    }

    public boolean hasArg() {
        return argsNameKey != null && !hasSuffix;
    }

    public boolean matches(String option) {
        if (!hasSuffix)
            return option.equals(text);
        if (!option.startsWith(text))
            return false;
        if (choices != null) {
            String arg = option.substring(text.length());
            if (choiceKind == ChoiceKind.ONEOF)
                return choices.containsKey(arg);
            else {
                for (String a : arg.split(",+")) {
                    if (!choices.containsKey(a))
                        return false;
                }
            }
        }
        return true;
    }

    public boolean process(OptionHelper helper, String option, String arg) {
        if (choices != null) {
            if (choiceKind == ChoiceKind.ONEOF) {

                for (String s : choices.keySet())
                    helper.remove(option + s);
                String opt = option + arg;
                helper.put(opt, opt);


                String nm = option.substring(0, option.length() - 1);
                helper.put(nm, arg);
            } else {

                for (String a : arg.split(",+")) {
                    String opt = option + a;
                    helper.put(opt, opt);
                }
            }
        }
        helper.put(option, arg);
        return false;
    }

    public boolean process(OptionHelper helper, String option) {
        if (hasSuffix)
            return process(helper, text, option.substring(text.length()));
        else
            return process(helper, option, option);
    }

    void help(Log log, OptionKind kind) {
        if (this.kind != kind)
            return;
        log.printRawLines(WriterKind.NOTICE,
                String.format("  %-26s %s",
                        helpSynopsis(log),
                        log.localize(PrefixKind.JAVAC, descrKey)));
    }

    private String helpSynopsis(Log log) {
        StringBuilder sb = new StringBuilder();
        sb.append(text);
        if (argsNameKey == null) {
            if (choices != null) {
                String sep = "{";
                for (Map.Entry<String, Boolean> e : choices.entrySet()) {
                    if (!e.getValue()) {
                        sb.append(sep);
                        sb.append(e.getKey());
                        sep = ",";
                    }
                }
                sb.append("}");
            }
        } else {
            if (!hasSuffix)
                sb.append(" ");
            sb.append(log.localize(PrefixKind.JAVAC, argsNameKey));
        }
        return sb.toString();
    }

    public enum OptionKind {

        STANDARD,

        EXTENDED,

        HIDDEN,
    }

    enum OptionGroup {

        BASIC,

        FILEMANAGER,

        INFO,

        OPERAND
    }

    enum ChoiceKind {

        ONEOF,

        ANYOF
    }

    public enum PkgInfo {

        ALWAYS,

        LEGACY,

        NONEMPTY;

        public static PkgInfo get(Options options) {
            String v = options.get(XPKGINFO);
            return (v == null
                    ? PkgInfo.LEGACY
                    : PkgInfo.valueOf(v.toUpperCase()));
        }
    }
}
