package com.github.api.sun.tools.doclint;

import com.github.api.sun.source.doctree.DocTree;
import com.github.api.sun.source.tree.Tree;
import com.github.api.sun.tools.doclint.Env.AccessKind;

import javax.tools.Diagnostic;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.*;

public class Messages {
    private final Options options;
    private final Stats stats;
    ResourceBundle bundle;
    Env env;

    Messages(Env env) {
        this.env = env;
        String name = getClass().getPackage().getName() + ".resources.doclint";
        bundle = ResourceBundle.getBundle(name, Locale.ENGLISH);
        stats = new Stats(bundle);
        options = new Options(stats);
    }

    void error(Group group, DocTree tree, String code, Object... args) {
        report(group, Diagnostic.Kind.ERROR, tree, code, args);
    }

    void warning(Group group, DocTree tree, String code, Object... args) {
        report(group, Diagnostic.Kind.WARNING, tree, code, args);
    }

    void setOptions(String opts) {
        options.setOptions(opts);
    }

    void setStatsEnabled(boolean b) {
        stats.setEnabled(b);
    }

    void reportStats(PrintWriter out) {
        stats.report(out);
    }

    protected void report(Group group, Diagnostic.Kind dkind, DocTree tree, String code, Object... args) {
        if (options.isEnabled(group, env.currAccess)) {
            String msg = (code == null) ? (String) args[0] : localize(code, args);
            env.trees.printMessage(dkind, msg, tree,
                    env.currDocComment, env.currPath.getCompilationUnit());
            stats.record(group, dkind, code);
        }
    }

    protected void report(Group group, Diagnostic.Kind dkind, Tree tree, String code, Object... args) {
        if (options.isEnabled(group, env.currAccess)) {
            String msg = localize(code, args);
            env.trees.printMessage(dkind, msg, tree, env.currPath.getCompilationUnit());
            stats.record(group, dkind, code);
        }
    }

    String localize(String code, Object... args) {
        String msg = bundle.getString(code);
        if (msg == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("message file broken: code=").append(code);
            if (args.length > 0) {
                sb.append(" arguments={0}");
                for (int i = 1; i < args.length; i++) {
                    sb.append(", {").append(i).append("}");
                }
            }
            msg = sb.toString();
        }
        return MessageFormat.format(msg, args);
    }

    public enum Group {
        ACCESSIBILITY,
        HTML,
        MISSING,
        SYNTAX,
        REFERENCE;

        static boolean accepts(String opt) {
            for (Group g : values())
                if (opt.equals(g.optName())) return true;
            return false;
        }

        String optName() {
            return name().toLowerCase();
        }

        String notOptName() {
            return "-" + optName();
        }
    }

    static class Options {
        private static final String ALL = "all";
        private final Stats stats;
        Map<String, AccessKind> map = new HashMap<String, AccessKind>();

        Options(Stats stats) {
            this.stats = stats;
        }

        static boolean isValidOptions(String opts) {
            for (String opt : opts.split(",")) {
                if (!isValidOption(opt.trim().toLowerCase()))
                    return false;
            }
            return true;
        }

        private static boolean isValidOption(String opt) {
            if (opt.equals("none") || opt.equals(Stats.OPT))
                return true;
            int begin = opt.startsWith("-") ? 1 : 0;
            int sep = opt.indexOf("/");
            String grp = opt.substring(begin, (sep != -1) ? sep : opt.length());
            return ((begin == 0 && grp.equals("all")) || Group.accepts(grp))
                    && ((sep == -1) || AccessKind.accepts(opt.substring(sep + 1)));
        }

        boolean isEnabled(Group g, AccessKind access) {
            if (map.isEmpty())
                map.put("all", AccessKind.PROTECTED);
            AccessKind ak = map.get(g.optName());
            if (ak != null && access.compareTo(ak) >= 0)
                return true;
            ak = map.get(ALL);
            if (ak != null && access.compareTo(ak) >= 0) {
                ak = map.get(g.notOptName());
                return ak == null || access.compareTo(ak) > 0;
            }
            return false;
        }

        void setOptions(String opts) {
            if (opts == null)
                setOption(ALL, AccessKind.PRIVATE);
            else {
                for (String opt : opts.split(","))
                    setOption(opt.trim().toLowerCase());
            }
        }

        private void setOption(String arg) throws IllegalArgumentException {
            if (arg.equals(Stats.OPT)) {
                stats.setEnabled(true);
                return;
            }
            int sep = arg.indexOf("/");
            if (sep > 0) {
                AccessKind ak = AccessKind.valueOf(arg.substring(sep + 1).toUpperCase());
                setOption(arg.substring(0, sep), ak);
            } else {
                setOption(arg, null);
            }
        }

        private void setOption(String opt, AccessKind ak) {
            map.put(opt, (ak != null) ? ak
                    : opt.startsWith("-") ? AccessKind.PUBLIC : AccessKind.PRIVATE);
        }
    }

    static class Stats {
        public static final String OPT = "stats";
        public static final String NO_CODE = "";
        final ResourceBundle bundle;
        int[] groupCounts;
        int[] dkindCounts;
        Map<String, Integer> codeCounts;

        Stats(ResourceBundle bundle) {
            this.bundle = bundle;
        }

        void setEnabled(boolean b) {
            if (b) {
                groupCounts = new int[Group.values().length];
                dkindCounts = new int[Diagnostic.Kind.values().length];
                codeCounts = new HashMap<String, Integer>();
            } else {
                groupCounts = null;
                dkindCounts = null;
                codeCounts = null;
            }
        }

        void record(Group g, Diagnostic.Kind dkind, String code) {
            if (codeCounts == null) {
                return;
            }
            groupCounts[g.ordinal()]++;
            dkindCounts[dkind.ordinal()]++;
            if (code == null) {
                code = NO_CODE;
            }
            Integer i = codeCounts.get(code);
            codeCounts.put(code, (i == null) ? 1 : i + 1);
        }

        void report(PrintWriter out) {
            if (codeCounts == null) {
                return;
            }
            out.println("By group...");
            Table groupTable = new Table();
            for (Group g : Group.values()) {
                groupTable.put(g.optName(), groupCounts[g.ordinal()]);
            }
            groupTable.print(out);
            out.println();
            out.println("By diagnostic kind...");
            Table dkindTable = new Table();
            for (Diagnostic.Kind k : Diagnostic.Kind.values()) {
                dkindTable.put(k.toString().toLowerCase(), dkindCounts[k.ordinal()]);
            }
            dkindTable.print(out);
            out.println();
            out.println("By message kind...");
            Table codeTable = new Table();
            for (Map.Entry<String, Integer> e : codeCounts.entrySet()) {
                String code = e.getKey();
                String msg;
                try {
                    msg = code.equals(NO_CODE) ? "OTHER" : bundle.getString(code);
                } catch (MissingResourceException ex) {
                    msg = code;
                }
                codeTable.put(msg, e.getValue());
            }
            codeTable.print(out);
        }

        private static class Table {
            private static final Comparator<Integer> DECREASING = new Comparator<Integer>() {
                public int compare(Integer o1, Integer o2) {
                    return o2.compareTo(o1);
                }
            };
            private final TreeMap<Integer, Set<String>> map = new TreeMap<Integer, Set<String>>(DECREASING);

            void put(String label, int n) {
                if (n == 0) {
                    return;
                }
                Set<String> labels = map.get(n);
                if (labels == null) {
                    map.put(n, labels = new TreeSet<String>());
                }
                labels.add(label);
            }

            void print(PrintWriter out) {
                for (Map.Entry<Integer, Set<String>> e : map.entrySet()) {
                    int count = e.getKey();
                    Set<String> labels = e.getValue();
                    for (String label : labels) {
                        out.println(String.format("%6d: %s", count, label));
                    }
                }
            }
        }
    }
}
