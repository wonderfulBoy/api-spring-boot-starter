package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.util.Position.LineMap;

public interface Lexer {

    void nextToken();

    Token token();

    Token token(int lookahead);

    Token prevToken();

    Token split();

    int errPos();

    void errPos(int pos);

    LineMap getLineMap();
}
