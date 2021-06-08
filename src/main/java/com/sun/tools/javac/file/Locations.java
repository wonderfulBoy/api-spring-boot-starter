package com.sun.tools.javac.file;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipFile;

import static com.sun.tools.javac.main.Option.*;
import static javax.tools.StandardLocation.*;

public class Locations {

    Map<Location, LocationHandler> handlersForLocation;
    Map<Option, LocationHandler> handlersForOption;
    private Log log;
    private Options options;
    private Lint lint;
    private FSInfo fsInfo;
    private boolean warn;
    private boolean inited = false;

    public Locations() {
        initHandlers();
    }

    private static Iterable<File> getPathEntries(String path) {
        return getPathEntries(path, null);
    }

    private static Iterable<File> getPathEntries(String path, File emptyPathDefault) {
        ListBuffer<File> entries = new ListBuffer<File>();
        int start = 0;
        while (start <= path.length()) {
            int sep = path.indexOf(File.pathSeparatorChar, start);
            if (sep == -1)
                sep = path.length();
            if (start < sep)
                entries.add(new File(path.substring(start, sep)));
            else if (emptyPathDefault != null)
                entries.add(emptyPathDefault);
            start = sep + 1;
        }
        return entries;
    }

    public static URL[] pathToURLs(String path) {
        StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
        URL[] urls = new URL[st.countTokens()];
        int count = 0;
        while (st.hasMoreTokens()) {
            URL url = fileToURL(new File(st.nextToken()));
            if (url != null) {
                urls[count++] = url;
            }
        }
        urls = Arrays.copyOf(urls, count);
        return urls;
    }

    private static URL fileToURL(File file) {
        String name;
        try {
            name = file.getCanonicalPath();
        } catch (IOException e) {
            name = file.getAbsolutePath();
        }
        name = name.replace(File.separatorChar, '/');
        if (!name.startsWith("/")) {
            name = "/" + name;
        }

        if (!file.isFile()) {
            name = name + "/";
        }
        try {
            return new URL("file", "", name);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(file.toString());
        }
    }

    public void update(Log log, Options options, Lint lint, FSInfo fsInfo) {
        this.log = log;
        this.options = options;
        this.lint = lint;
        this.fsInfo = fsInfo;
    }

    public Collection<File> bootClassPath() {
        return getLocation(PLATFORM_CLASS_PATH);
    }

    public boolean isDefaultBootClassPath() {
        BootClassPathLocationHandler h =
                (BootClassPathLocationHandler) getHandler(PLATFORM_CLASS_PATH);
        return h.isDefault();
    }

    boolean isDefaultBootClassPathRtJar(File file) {
        BootClassPathLocationHandler h =
                (BootClassPathLocationHandler) getHandler(PLATFORM_CLASS_PATH);
        return h.isDefaultRtJar(file);
    }

    public Collection<File> userClassPath() {
        return getLocation(CLASS_PATH);
    }

    public Collection<File> sourcePath() {
        Collection<File> p = getLocation(SOURCE_PATH);

        return p == null || p.isEmpty() ? null : p;
    }

    void initHandlers() {
        handlersForLocation = new HashMap<Location, LocationHandler>();
        handlersForOption = new EnumMap<Option, LocationHandler>(Option.class);
        LocationHandler[] handlers = {
                new BootClassPathLocationHandler(),
                new ClassPathLocationHandler(),
                new SimpleLocationHandler(StandardLocation.SOURCE_PATH, Option.SOURCEPATH),
                new SimpleLocationHandler(StandardLocation.ANNOTATION_PROCESSOR_PATH, Option.PROCESSORPATH),
                new OutputLocationHandler((StandardLocation.CLASS_OUTPUT), Option.D),
                new OutputLocationHandler((StandardLocation.SOURCE_OUTPUT), Option.S),
                new OutputLocationHandler((StandardLocation.NATIVE_HEADER_OUTPUT), Option.H)
        };
        for (LocationHandler h : handlers) {
            handlersForLocation.put(h.location, h);
            for (Option o : h.options)
                handlersForOption.put(o, h);
        }
    }

    boolean handleOption(Option option, String value) {
        LocationHandler h = handlersForOption.get(option);
        return (h != null && h.handleOption(option, value));
    }

    Collection<File> getLocation(Location location) {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.getLocation());
    }

    File getOutputLocation(Location location) {
        if (!location.isOutputLocation())
            throw new IllegalArgumentException();
        LocationHandler h = getHandler(location);
        return ((OutputLocationHandler) h).outputDir;
    }

    void setLocation(Location location, Iterable<? extends File> files) throws IOException {
        LocationHandler h = getHandler(location);
        if (h == null) {
            if (location.isOutputLocation())
                h = new OutputLocationHandler(location);
            else
                h = new SimpleLocationHandler(location);
            handlersForLocation.put(location, h);
        }
        h.setLocation(files);
    }

    protected LocationHandler getHandler(Location location) {
        location.getClass();
        lazy();
        return handlersForLocation.get(location);
    }

    protected void lazy() {
        if (!inited) {
            warn = lint.isEnabled(Lint.LintCategory.PATH);
            for (LocationHandler h : handlersForLocation.values()) {
                h.update(options);
            }
            inited = true;
        }
    }

    private boolean isArchive(File file) {
        String n = file.getName().toLowerCase();
        return fsInfo.isFile(file)
                && (n.endsWith(".jar") || n.endsWith(".zip"));
    }

    private class Path extends LinkedHashSet<File> {
        private static final long serialVersionUID = 0;
        private boolean expandJarClassPaths = false;
        private Set<File> canonicalValues = new HashSet<File>();
        private File emptyPathDefault = null;

        public Path() {
            super();
        }

        public Path expandJarClassPaths(boolean x) {
            expandJarClassPaths = x;
            return this;
        }

        public Path emptyPathDefault(File x) {
            emptyPathDefault = x;
            return this;
        }

        public Path addDirectories(String dirs, boolean warn) {
            boolean prev = expandJarClassPaths;
            expandJarClassPaths = true;
            try {
                if (dirs != null)
                    for (File dir : getPathEntries(dirs))
                        addDirectory(dir, warn);
                return this;
            } finally {
                expandJarClassPaths = prev;
            }
        }

        public Path addDirectories(String dirs) {
            return addDirectories(dirs, warn);
        }

        private void addDirectory(File dir, boolean warn) {
            if (!dir.isDirectory()) {
                if (warn)
                    log.warning(Lint.LintCategory.PATH,
                            "dir.path.element.not.found", dir);
                return;
            }
            File[] files = dir.listFiles();
            if (files == null)
                return;
            for (File direntry : files) {
                if (isArchive(direntry))
                    addFile(direntry, warn);
            }
        }

        public Path addFiles(String files, boolean warn) {
            if (files != null) {
                addFiles(getPathEntries(files, emptyPathDefault), warn);
            }
            return this;
        }

        public Path addFiles(String files) {
            return addFiles(files, warn);
        }

        public Path addFiles(Iterable<? extends File> files, boolean warn) {
            if (files != null) {
                for (File file : files)
                    addFile(file, warn);
            }
            return this;
        }

        public Path addFiles(Iterable<? extends File> files) {
            return addFiles(files, warn);
        }

        public void addFile(File file, boolean warn) {
            if (contains(file)) {

                return;
            }
            if (!fsInfo.exists(file)) {

                if (warn) {
                    log.warning(Lint.LintCategory.PATH,
                            "path.element.not.found", file);
                }
                super.add(file);
                return;
            }
            File canonFile = fsInfo.getCanonicalFile(file);
            if (canonicalValues.contains(canonFile)) {

                return;
            }
            if (fsInfo.isFile(file)) {

                if (!isArchive(file)) {

                    try {
                        ZipFile z = new ZipFile(file);
                        z.close();
                        if (warn) {
                            log.warning(Lint.LintCategory.PATH,
                                    "unexpected.archive.file", file);
                        }
                    } catch (IOException e) {

                        if (warn) {
                            log.warning(Lint.LintCategory.PATH,
                                    "invalid.archive.file", file);
                        }
                        return;
                    }
                }
            }

            super.add(file);
            canonicalValues.add(canonFile);
            if (expandJarClassPaths && fsInfo.isFile(file))
                addJarClassPath(file, warn);
        }


        private void addJarClassPath(File jarFile, boolean warn) {
            try {
                for (File f : fsInfo.getJarClassPath(jarFile)) {
                    addFile(f, warn);
                }
            } catch (IOException e) {
                log.error("error.reading.file", jarFile, JavacFileManager.getMessage(e));
            }
        }
    }

    protected abstract class LocationHandler {
        final Location location;
        final Set<Option> options;

        protected LocationHandler(Location location, Option... options) {
            this.location = location;
            this.options = options.length == 0 ?
                    EnumSet.noneOf(Option.class) :
                    EnumSet.copyOf(Arrays.asList(options));
        }

        void update(Options optionTable) {
            for (Option o : options) {
                String v = optionTable.get(o);
                if (v != null) {
                    handleOption(o, v);
                }
            }
        }

        abstract boolean handleOption(Option option, String value);

        abstract Collection<File> getLocation();

        abstract void setLocation(Iterable<? extends File> files) throws IOException;
    }

    private class OutputLocationHandler extends LocationHandler {
        private File outputDir;

        OutputLocationHandler(Location location, Option... options) {
            super(location, options);
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option))
                return false;


            outputDir = new File(value);
            return true;
        }

        @Override
        Collection<File> getLocation() {
            return (outputDir == null) ? null : Collections.singleton(outputDir);
        }

        @Override
        void setLocation(Iterable<? extends File> files) throws IOException {
            if (files == null) {
                outputDir = null;
            } else {
                Iterator<? extends File> pathIter = files.iterator();
                if (!pathIter.hasNext())
                    throw new IllegalArgumentException("empty path for directory");
                File dir = pathIter.next();
                if (pathIter.hasNext())
                    throw new IllegalArgumentException("path too long for directory");
                if (!dir.exists())
                    throw new FileNotFoundException(dir + ": does not exist");
                else if (!dir.isDirectory())
                    throw new IOException(dir + ": not a directory");
                outputDir = dir;
            }
        }
    }

    private class SimpleLocationHandler extends LocationHandler {
        protected Collection<File> searchPath;

        SimpleLocationHandler(Location location, Option... options) {
            super(location, options);
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option))
                return false;
            searchPath = value == null ? null :
                    Collections.unmodifiableCollection(createPath().addFiles(value));
            return true;
        }

        @Override
        Collection<File> getLocation() {
            return searchPath;
        }

        @Override
        void setLocation(Iterable<? extends File> files) {
            Path p;
            if (files == null) {
                p = computePath(null);
            } else {
                p = createPath().addFiles(files);
            }
            searchPath = Collections.unmodifiableCollection(p);
        }

        protected Path computePath(String value) {
            return createPath().addFiles(value);
        }

        protected Path createPath() {
            return new Path();
        }
    }

    private class ClassPathLocationHandler extends SimpleLocationHandler {
        ClassPathLocationHandler() {
            super(StandardLocation.CLASS_PATH,
                    Option.CLASSPATH, Option.CP);
        }

        @Override
        Collection<File> getLocation() {
            lazy();
            return searchPath;
        }

        @Override
        protected Path computePath(String value) {
            String cp = value;

            if (cp == null) cp = System.getProperty("env.class.path");


            if (cp == null && System.getProperty("application.home") == null)
                cp = System.getProperty("java.class.path");

            if (cp == null) cp = ".";
            return createPath().addFiles(cp);
        }

        @Override
        protected Path createPath() {
            return new Path()
                    .expandJarClassPaths(true)
                    .emptyPathDefault(new File("."));
        }

        private void lazy() {
            if (searchPath == null)
                setLocation(null);
        }
    }

    private class BootClassPathLocationHandler extends LocationHandler {
        final Map<Option, String> optionValues = new EnumMap<Option, String>(Option.class);
        private Collection<File> searchPath;
        private File defaultBootClassPathRtJar = null;

        private boolean isDefaultBootClassPath;

        BootClassPathLocationHandler() {
            super(StandardLocation.PLATFORM_CLASS_PATH,
                    Option.BOOTCLASSPATH, Option.XBOOTCLASSPATH,
                    Option.XBOOTCLASSPATH_PREPEND,
                    Option.XBOOTCLASSPATH_APPEND,
                    Option.ENDORSEDDIRS, Option.DJAVA_ENDORSED_DIRS,
                    Option.EXTDIRS, Option.DJAVA_EXT_DIRS);
        }

        boolean isDefault() {
            lazy();
            return isDefaultBootClassPath;
        }

        boolean isDefaultRtJar(File file) {
            lazy();
            return file.equals(defaultBootClassPathRtJar);
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option))
                return false;
            option = canonicalize(option);
            optionValues.put(option, value);
            if (option == BOOTCLASSPATH) {
                optionValues.remove(XBOOTCLASSPATH_PREPEND);
                optionValues.remove(XBOOTCLASSPATH_APPEND);
            }
            searchPath = null;
            return true;
        }


        private Option canonicalize(Option option) {
            switch (option) {
                case XBOOTCLASSPATH:
                    return Option.BOOTCLASSPATH;
                case DJAVA_ENDORSED_DIRS:
                    return Option.ENDORSEDDIRS;
                case DJAVA_EXT_DIRS:
                    return Option.EXTDIRS;
                default:
                    return option;
            }
        }

        @Override
        Collection<File> getLocation() {
            lazy();
            return searchPath;
        }

        @Override
        void setLocation(Iterable<? extends File> files) {
            if (files == null) {
                searchPath = null;
            } else {
                defaultBootClassPathRtJar = null;
                isDefaultBootClassPath = false;
                Path p = new Path().addFiles(files, false);
                searchPath = Collections.unmodifiableCollection(p);
                optionValues.clear();
            }
        }

        Path computePath() {
            defaultBootClassPathRtJar = null;
            Path path = new Path();
            String bootclasspathOpt = optionValues.get(BOOTCLASSPATH);
            String endorseddirsOpt = optionValues.get(ENDORSEDDIRS);
            String extdirsOpt = optionValues.get(EXTDIRS);
            String xbootclasspathPrependOpt = optionValues.get(XBOOTCLASSPATH_PREPEND);
            String xbootclasspathAppendOpt = optionValues.get(XBOOTCLASSPATH_APPEND);
            path.addFiles(xbootclasspathPrependOpt);
            if (endorseddirsOpt != null)
                path.addDirectories(endorseddirsOpt);
            else
                path.addDirectories(System.getProperty("java.endorsed.dirs"), false);
            if (bootclasspathOpt != null) {
                path.addFiles(bootclasspathOpt);
            } else {

                String files = System.getProperty("sun.boot.class.path");
                path.addFiles(files, false);
                File rt_jar = new File("rt.jar");
                for (File file : getPathEntries(files)) {
                    if (new File(file.getName()).equals(rt_jar))
                        defaultBootClassPathRtJar = file;
                }
            }
            path.addFiles(xbootclasspathAppendOpt);


            if (extdirsOpt != null)
                path.addDirectories(extdirsOpt);
            else
                path.addDirectories(System.getProperty("java.ext.dirs"), false);
            isDefaultBootClassPath =
                    (xbootclasspathPrependOpt == null) &&
                            (bootclasspathOpt == null) &&
                            (xbootclasspathAppendOpt == null);
            return path;
        }

        private void lazy() {
            if (searchPath == null)
                searchPath = Collections.unmodifiableCollection(computePath());
        }
    }
}
