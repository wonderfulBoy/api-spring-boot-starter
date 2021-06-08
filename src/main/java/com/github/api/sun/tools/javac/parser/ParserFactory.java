package com.github.api.sun.tools.javac.parser;

import com.github.api.sun.tools.javac.code.Source;
import com.github.api.sun.tools.javac.tree.DocTreeMaker;
import com.github.api.sun.tools.javac.tree.TreeMaker;
import com.github.api.sun.tools.javac.util.Context;
import com.github.api.sun.tools.javac.util.Log;
import com.github.api.sun.tools.javac.util.Names;
import com.github.api.sun.tools.javac.util.Options;

import java.util.Locale;

public class ParserFactory {

    protected static final Context.Key<ParserFactory> parserFactoryKey = new Context.Key<ParserFactory>();
    final TreeMaker F;
    final DocTreeMaker docTreeMaker;
    final Log log;
    final Tokens tokens;
    final Source source;
    final Names names;
    final Options options;
    final ScannerFactory scannerFactory;
    final Locale locale;
    protected ParserFactory(Context context) {
        super();
        context.put(parserFactoryKey, this);
        this.F = TreeMaker.instance(context);
        this.docTreeMaker = DocTreeMaker.instance(context);
        this.log = Log.instance(context);
        this.names = Names.instance(context);
        this.tokens = Tokens.instance(context);
        this.source = Source.instance(context);
        this.options = Options.instance(context);
        this.scannerFactory = ScannerFactory.instance(context);
        this.locale = context.get(Locale.class);
    }

    public static ParserFactory instance(Context context) {
        ParserFactory instance = context.get(parserFactoryKey);
        if (instance == null) {
            instance = new ParserFactory(context);
        }
        return instance;
    }

    public JavacParser newParser(CharSequence input, boolean keepDocComments, boolean keepEndPos, boolean keepLineMap) {
        Lexer lexer = scannerFactory.newScanner(input, keepDocComments);
        return new JavacParser(this, lexer, keepDocComments, keepLineMap, keepEndPos);
    }
}
