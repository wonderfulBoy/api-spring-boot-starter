package com.sun.tools.javac.util;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.FSInfo;
import com.sun.tools.javac.file.Locations;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.main.OptionHelper;
import com.sun.tools.javac.main.OptionHelper.GrumpyHelper;
import com.sun.tools.javac.util.JCDiagnostic.SimpleDiagnosticPosition;

import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.util.*;

public abstract class BaseFileManager {
    private static final Set<Option> javacFileManagerOptions =
            Option.getJavacFileManagerOptions();
    protected final Map<JavaFileObject, ContentCacheEntry> contentCache
            = new HashMap<JavaFileObject, ContentCacheEntry>();
    private final ByteBufferCache byteBufferCache;
    public Log log;

    protected Charset charset;
    protected Options options;
    protected String classLoaderClass;
    protected Locations locations;
    private String defaultEncodingName;

    protected BaseFileManager(Charset charset) {
        this.charset = charset;
        byteBufferCache = new ByteBufferCache();
        locations = createLocations();
    }

    public static Kind getKind(String name) {
        if (name.endsWith(Kind.CLASS.extension))
            return Kind.CLASS;
        else if (name.endsWith(Kind.SOURCE.extension))
            return Kind.SOURCE;
        else if (name.endsWith(Kind.HTML.extension))
            return Kind.HTML;
        else
            return Kind.OTHER;
    }

    protected static <T> T nullCheck(T o) {
        o.getClass();
        return o;
    }

    protected static <T> Collection<T> nullCheck(Collection<T> it) {
        for (T t : it)
            t.getClass();
        return it;
    }

    public void setContext(Context context) {
        log = Log.instance(context);
        options = Options.instance(context);
        classLoaderClass = options.get("procloader");
        locations.update(log, options, Lint.instance(context), FSInfo.instance(context));
    }

    protected Locations createLocations() {
        return new Locations();
    }

    protected Source getSource() {
        String sourceName = options.get(Option.SOURCE);
        Source source = null;
        if (sourceName != null)
            source = Source.lookup(sourceName);
        return (source != null ? source : Source.DEFAULT);
    }

    protected ClassLoader getClassLoader(URL[] urls) {
        ClassLoader thisClassLoader = getClass().getClassLoader();


        if (classLoaderClass != null) {
            try {
                Class<? extends ClassLoader> loader =
                        Class.forName(classLoaderClass).asSubclass(ClassLoader.class);
                Class<?>[] constrArgTypes = {URL[].class, ClassLoader.class};
                Constructor<? extends ClassLoader> constr = loader.getConstructor(constrArgTypes);
                return constr.newInstance(urls, thisClassLoader);
            } catch (Throwable t) {

            }
        }
        return new URLClassLoader(urls, thisClassLoader);
    }

    public boolean handleOption(String current, Iterator<String> remaining) {
        OptionHelper helper = new GrumpyHelper(log) {
            @Override
            public String get(Option option) {
                return options.get(option.getText());
            }

            @Override
            public void put(String name, String value) {
                options.put(name, value);
            }

            @Override
            public void remove(String name) {
                options.remove(name);
            }
        };
        for (Option o : javacFileManagerOptions) {
            if (o.matches(current)) {
                if (o.hasArg()) {
                    if (remaining.hasNext()) {
                        if (!o.process(helper, current, remaining.next()))
                            return true;
                    }
                } else {
                    if (!o.process(helper, current))
                        return true;
                }

                throw new IllegalArgumentException(current);
            }
        }
        return false;
    }

    public int isSupportedOption(String option) {
        for (Option o : javacFileManagerOptions) {
            if (o.matches(option))
                return o.hasArg() ? 1 : 0;
        }
        return -1;
    }

    public abstract boolean isDefaultBootClassPath();

    private String getDefaultEncodingName() {
        if (defaultEncodingName == null) {
            defaultEncodingName =
                    new OutputStreamWriter(new ByteArrayOutputStream()).getEncoding();
        }
        return defaultEncodingName;
    }

    public String getEncodingName() {
        String encName = options.get(Option.ENCODING);
        if (encName == null)
            return getDefaultEncodingName();
        else
            return encName;
    }

    public CharBuffer decode(ByteBuffer inbuf, boolean ignoreEncodingErrors) {
        String encodingName = getEncodingName();
        CharsetDecoder decoder;
        try {
            decoder = getDecoder(encodingName, ignoreEncodingErrors);
        } catch (IllegalCharsetNameException e) {
            log.error("unsupported.encoding", encodingName);
            return (CharBuffer) CharBuffer.allocate(1).flip();
        } catch (UnsupportedCharsetException e) {
            log.error("unsupported.encoding", encodingName);
            return (CharBuffer) CharBuffer.allocate(1).flip();
        }

        float factor =
                decoder.averageCharsPerByte() * 0.8f +
                        decoder.maxCharsPerByte() * 0.2f;
        CharBuffer dest = CharBuffer.
                allocate(10 + (int) (inbuf.remaining() * factor));
        while (true) {
            CoderResult result = decoder.decode(inbuf, dest, true);
            dest.flip();
            if (result.isUnderflow()) {

                if (dest.limit() == dest.capacity()) {
                    dest = CharBuffer.allocate(dest.capacity() + 1).put(dest);
                    dest.flip();
                }
                return dest;
            } else if (result.isOverflow()) {
                int newCapacity =
                        10 + dest.capacity() +
                                (int) (inbuf.remaining() * decoder.maxCharsPerByte());
                dest = CharBuffer.allocate(newCapacity).put(dest);
            } else if (result.isMalformed() || result.isUnmappable()) {


                if (!getSource().allowEncodingErrors()) {
                    log.error(new SimpleDiagnosticPosition(dest.limit()),
                            "illegal.char.for.encoding",
                            charset == null ? encodingName : charset.name());
                } else {
                    log.warning(new SimpleDiagnosticPosition(dest.limit()),
                            "illegal.char.for.encoding",
                            charset == null ? encodingName : charset.name());
                }

                inbuf.position(inbuf.position() + result.length());


                dest.position(dest.limit());
                dest.limit(dest.capacity());
                dest.put((char) 0xfffd);
            } else {
                throw new AssertionError(result);
            }
        }

    }

    public CharsetDecoder getDecoder(String encodingName, boolean ignoreEncodingErrors) {
        Charset cs = (this.charset == null)
                ? Charset.forName(encodingName)
                : this.charset;
        CharsetDecoder decoder = cs.newDecoder();
        CodingErrorAction action;
        if (ignoreEncodingErrors)
            action = CodingErrorAction.REPLACE;
        else
            action = CodingErrorAction.REPORT;
        return decoder
                .onMalformedInput(action)
                .onUnmappableCharacter(action);
    }

    public ByteBuffer makeByteBuffer(InputStream in)
            throws IOException {
        int limit = in.available();
        if (limit < 1024) limit = 1024;
        ByteBuffer result = byteBufferCache.get(limit);
        int position = 0;
        while (in.available() != 0) {
            if (position >= limit)

                result = ByteBuffer.
                        allocate(limit <<= 1).
                        put((ByteBuffer) result.flip());
            int count = in.read(result.array(),
                    position,
                    limit - position);
            if (count < 0) break;
            result.position(position += count);
        }
        return (ByteBuffer) result.flip();
    }

    public void recycleByteBuffer(ByteBuffer bb) {
        byteBufferCache.put(bb);
    }

    public CharBuffer getCachedContent(JavaFileObject file) {
        ContentCacheEntry e = contentCache.get(file);
        if (e == null)
            return null;
        if (!e.isValid(file)) {
            contentCache.remove(file);
            return null;
        }
        return e.getValue();
    }

    public void cache(JavaFileObject file, CharBuffer cb) {
        contentCache.put(file, new ContentCacheEntry(file, cb));
    }

    public void flushCache(JavaFileObject file) {
        contentCache.remove(file);
    }

    private static class ByteBufferCache {
        private ByteBuffer cached;

        ByteBuffer get(int capacity) {
            if (capacity < 20480) capacity = 20480;
            ByteBuffer result =
                    (cached != null && cached.capacity() >= capacity)
                            ? (ByteBuffer) cached.clear()
                            : ByteBuffer.allocate(capacity + capacity >> 1);
            cached = null;
            return result;
        }

        void put(ByteBuffer x) {
            cached = x;
        }
    }

    protected static class ContentCacheEntry {
        final long timestamp;
        final SoftReference<CharBuffer> ref;

        ContentCacheEntry(JavaFileObject file, CharBuffer cb) {
            this.timestamp = file.getLastModified();
            this.ref = new SoftReference<CharBuffer>(cb);
        }

        boolean isValid(JavaFileObject file) {
            return timestamp == file.getLastModified();
        }

        CharBuffer getValue() {
            return ref.get();
        }
    }
}
