package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Position.LineMap;

import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.sun.tools.javac.parser.Tokens.DUMMY;
import static com.sun.tools.javac.parser.Tokens.Token;

public class Scanner implements Lexer {
    private Tokens tokens;

    private Token token;

    private Token prevToken;

    private List<Token> savedTokens = new ArrayList<Token>();
    private JavaTokenizer tokenizer;

    protected Scanner(ScannerFactory fac, CharBuffer buf) {
        this(fac, new JavaTokenizer(fac, buf));
    }

    protected Scanner(ScannerFactory fac, char[] buf, int inputLength) {
        this(fac, new JavaTokenizer(fac, buf, inputLength));
    }

    protected Scanner(ScannerFactory fac, JavaTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        tokens = fac.tokens;
        token = prevToken = DUMMY;
    }

    public Token token() {
        return token(0);
    }

    public Token token(int lookahead) {
        if (lookahead == 0) {
            return token;
        } else {
            ensureLookahead(lookahead);
            return savedTokens.get(lookahead - 1);
        }
    }

    private void ensureLookahead(int lookahead) {
        for (int i = savedTokens.size(); i < lookahead; i++) {
            savedTokens.add(tokenizer.readToken());
        }
    }

    public Token prevToken() {
        return prevToken;
    }

    public void nextToken() {
        prevToken = token;
        if (!savedTokens.isEmpty()) {
            token = savedTokens.remove(0);
        } else {
            token = tokenizer.readToken();
        }
    }

    public Token split() {
        Token[] splitTokens = token.split(tokens);
        prevToken = splitTokens[0];
        token = splitTokens[1];
        return token;
    }

    public LineMap getLineMap() {
        return tokenizer.getLineMap();
    }

    public int errPos() {
        return tokenizer.errPos();
    }

    public void errPos(int pos) {
        tokenizer.errPos(pos);
    }
}
