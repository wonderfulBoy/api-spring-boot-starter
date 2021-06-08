package com.sun.tools.javadoc;

import com.sun.javadoc.Doc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Tag;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Position;

import javax.tools.FileObject;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.CollationKey;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class DocImpl implements Doc, Comparable<Object> {

    protected final DocEnv env;

    protected TreePath treePath;

    protected String documentation;

    private Comment comment;

    private CollationKey collationkey = null;

    private Tag[] firstSentence;

    private Tag[] inlineTags;

    DocImpl(DocEnv env, TreePath treePath) {
        this.treePath = treePath;
        this.documentation = getCommentText(treePath);
        this.env = env;
    }

    private static String getCommentText(TreePath p) {
        if (p == null)
            return null;

        JCCompilationUnit topLevel = (JCCompilationUnit) p.getCompilationUnit();
        JCTree tree = (JCTree) p.getLeaf();
        return topLevel.docComments.getCommentText(tree);
    }

    protected String documentation() {
        if (documentation == null) documentation = "";
        return documentation;
    }

    Comment comment() {
        if (comment == null) {
            String d = documentation();
            if (env.doclint != null
                    && treePath != null
                    && d.equals(getCommentText(treePath))) {
                env.doclint.scan(treePath);
            }
            comment = new Comment(this, d);
        }
        return comment;
    }

    public String commentText() {
        return comment().commentText();
    }

    public Tag[] tags() {
        return comment().tags();
    }

    public Tag[] tags(String tagname) {
        return comment().tags(tagname);
    }

    public SeeTag[] seeTags() {
        return comment().seeTags();
    }

    public Tag[] inlineTags() {
        if (inlineTags == null) {
            inlineTags = Comment.getInlineTags(this, commentText());
        }
        return inlineTags;
    }

    public Tag[] firstSentenceTags() {
        if (firstSentence == null) {
            inlineTags();
            try {
                env.setSilent(true);
                firstSentence = Comment.firstSentenceTags(this, commentText());
            } finally {
                env.setSilent(false);
            }
        }
        return firstSentence;
    }

    String readHTMLDocumentation(InputStream input, FileObject filename) throws IOException {
        byte[] filecontents = new byte[input.available()];
        try {
            DataInputStream dataIn = new DataInputStream(input);
            dataIn.readFully(filecontents);
        } finally {
            input.close();
        }
        String encoding = env.getEncoding();
        String rawDoc = (encoding != null)
                ? new String(filecontents, encoding)
                : new String(filecontents);
        Pattern bodyPat = Pattern.compile("(?is).*<body\\b[^>]*>(.*)</body\\b.*");
        Matcher m = bodyPat.matcher(rawDoc);
        if (m.matches()) {
            return m.group(1);
        } else {
            String key = rawDoc.matches("(?is).*<body\\b.*")
                    ? "javadoc.End_body_missing_from_html_file"
                    : "javadoc.Body_missing_from_html_file";
            env.error(SourcePositionImpl.make(filename, Position.NOPOS, null), key);
            return "";
        }
    }

    public String getRawCommentText() {
        return documentation();
    }

    public void setRawCommentText(String rawDocumentation) {
        treePath = null;
        documentation = rawDocumentation;
        comment = null;
    }

    void setTreePath(TreePath treePath) {
        this.treePath = treePath;
        documentation = getCommentText(treePath);
        comment = null;
    }

    CollationKey key() {
        if (collationkey == null) {
            collationkey = generateKey();
        }
        return collationkey;
    }

    CollationKey generateKey() {
        String k = name();
        return env.doclocale.collator.getCollationKey(k);
    }

    @Override
    public String toString() {
        return qualifiedName();
    }

    public abstract String name();

    public abstract String qualifiedName();

    public int compareTo(Object obj) {
        return key().compareTo(((DocImpl) obj).key());
    }

    public boolean isField() {
        return false;
    }

    public boolean isEnumConstant() {
        return false;
    }

    public boolean isConstructor() {
        return false;
    }

    public boolean isMethod() {
        return false;
    }

    public boolean isAnnotationTypeElement() {
        return false;
    }

    public boolean isInterface() {
        return false;
    }

    public boolean isException() {
        return false;
    }

    public boolean isError() {
        return false;
    }

    public boolean isEnum() {
        return false;
    }

    public boolean isAnnotationType() {
        return false;
    }

    public boolean isOrdinaryClass() {
        return false;
    }

    public boolean isClass() {
        return false;
    }

    public abstract boolean isIncluded();

    public SourcePosition position() {
        return null;
    }

}
