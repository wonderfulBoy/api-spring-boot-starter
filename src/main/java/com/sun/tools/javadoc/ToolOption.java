package com.sun.tools.javadoc;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Options;

import java.util.StringTokenizer;

public enum ToolOption {

    BOOTCLASSPATH("-bootclasspath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    CLASSPATH("-classpath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    CP("-cp", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    EXTDIRS("-extdirs", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    SOURCEPATH("-sourcepath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    SYSCLASSPATH("-sysclasspath", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt("-bootclasspath", arg);
        }
    },

    ENCODING("-encoding", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.encoding = arg;
            helper.setCompilerOpt(opt, arg);
        }
    },

    SOURCE("-source", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    XMAXERRS("-Xmaxerrs", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },

    XMAXWARNS("-Xmaxwarns", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.setCompilerOpt(opt, arg);
        }
    },


    DOCLET("-doclet", true),

    DOCLETPATH("-docletpath", true),

    SUBPACKAGES("-subpackages", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(helper.subPackages, arg);
        }
    },

    EXCLUDE("-exclude", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.addToList(helper.excludedPackages, arg);
        }
    },

    PACKAGE("-package") {
        @Override
        public void process(Helper helper) {
            helper.setFilter(
                    Flags.PUBLIC | Flags.PROTECTED | ModifierFilter.PACKAGE);
        }
    },

    PRIVATE("-private") {
        @Override
        public void process(Helper helper) {
            helper.setFilter(ModifierFilter.ALL_ACCESS);
        }
    },

    PROTECTED("-protected") {
        @Override
        public void process(Helper helper) {
            helper.setFilter(Flags.PUBLIC | Flags.PROTECTED);
        }
    },

    PUBLIC("-public") {
        @Override
        public void process(Helper helper) {
            helper.setFilter(Flags.PUBLIC);
        }
    },

    PROMPT("-prompt") {
        @Override
        public void process(Helper helper) {
            helper.compOpts.put("-prompt", "-prompt");
            helper.promptOnError = true;
        }
    },

    QUIET("-quiet") {
        @Override
        public void process(Helper helper) {
            helper.quiet = true;
        }
    },

    VERBOSE("-verbose") {
        @Override
        public void process(Helper helper) {
            helper.compOpts.put("-verbose", "");
        }
    },

    XWERROR("-Xwerror") {
        @Override
        public void process(Helper helper) {
            helper.rejectWarnings = true;

        }
    },

    BREAKITERATOR("-breakiterator") {
        @Override
        public void process(Helper helper) {
            helper.breakiterator = true;
        }
    },

    LOCALE("-locale", true) {
        @Override
        public void process(Helper helper, String arg) {
            helper.docLocale = arg;
        }
    },

    OVERVIEW("-overview", true),

    XCLASSES("-Xclasses") {
        @Override
        public void process(Helper helper) {
            helper.docClasses = true;

        }
    },

    HELP("-help") {
        @Override
        public void process(Helper helper) {
            helper.usage();
        }
    },

    X("-X") {
        @Override
        public void process(Helper helper) {
            helper.Xusage();
        }
    };

    public final String opt;
    public final boolean hasArg;

    ToolOption(String opt) {
        this(opt, false);
    }

    ToolOption(String opt, boolean hasArg) {
        this.opt = opt;
        this.hasArg = hasArg;
    }

    static ToolOption get(String name) {
        for (ToolOption o : values()) {
            if (name.equals(o.opt))
                return o;
        }
        return null;
    }

    void process(Helper helper, String arg) {
    }

    void process(Helper helper) {
    }

    static abstract class Helper {
        final ListBuffer<String[]> options = new ListBuffer<String[]>();

        final ListBuffer<String> subPackages = new ListBuffer<String>();

        final ListBuffer<String> excludedPackages = new ListBuffer<String>();

        Options compOpts;

        String encoding = null;

        boolean breakiterator = false;

        boolean quiet = false;

        boolean docClasses = false;

        boolean rejectWarnings = false;

        boolean promptOnError;

        String docLocale = "";

        ModifierFilter showAccess = null;

        abstract void usage();

        abstract void Xusage();

        abstract void usageError(String msg, Object... args);

        protected void addToList(ListBuffer<String> list, String str) {
            StringTokenizer st = new StringTokenizer(str, ":");
            String current;
            while (st.hasMoreTokens()) {
                current = st.nextToken();
                list.append(current);
            }
        }

        protected void setFilter(long filterBits) {
            if (showAccess != null) {
                usageError("main.incompatible.access.flags");
            }
            showAccess = new ModifierFilter(filterBits);
        }

        private void setCompilerOpt(String opt, String arg) {
            if (compOpts.get(opt) != null) {
                usageError("main.option.already.seen", opt);
            }
            compOpts.put(opt, arg);
        }
    }
}
