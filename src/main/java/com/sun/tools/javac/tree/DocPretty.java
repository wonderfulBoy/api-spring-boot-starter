package com.sun.tools.javac.tree;

import com.sun.source.doctree.*;
import com.sun.source.doctree.AttributeTree.ValueKind;
import com.sun.tools.javac.util.Convert;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class DocPretty implements DocTreeVisitor<Void, Void> {
    final Writer out;
    final String lineSep = System.getProperty("line.separator");
    int lmargin = 0;

    public DocPretty(Writer out) {
        this.out = out;
    }

    public void print(DocTree tree) throws IOException {
        try {
            if (tree == null)
                print("/*missing*/");
            else {
                tree.accept(this, null);
            }
        } catch (UncheckedIOException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    protected void print(Object s) throws IOException {
        out.write(Convert.escapeUnicode(s.toString()));
    }

    public void print(List<? extends DocTree> list) throws IOException {
        for (DocTree t : list) {
            print(t);
        }
    }

    protected void print(List<? extends DocTree> list, String sep) throws IOException {
        if (list.isEmpty())
            return;
        boolean first = true;
        for (DocTree t : list) {
            if (!first)
                print(sep);
            print(t);
            first = false;
        }
    }

    protected void println() throws IOException {
        out.write(lineSep);
    }

    protected void printTagName(DocTree node) throws IOException {
        out.write("@");
        out.write(node.getKind().tagName);
    }

    public Void visitAttribute(AttributeTree node, Void p) {
        try {
            print(node.getName());
            String quote;
            switch (node.getValueKind()) {
                case EMPTY:
                    quote = null;
                    break;
                case UNQUOTED:
                    quote = "";
                    break;
                case SINGLE:
                    quote = "'";
                    break;
                case DOUBLE:
                    quote = "\"";
                    break;
                default:
                    throw new AssertionError();
            }
            if (quote != null) {
                print("=" + quote);
                print(node.getValue());
                print(quote);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitAuthor(AuthorTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            print(node.getName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitComment(CommentTree node, Void p) {
        try {
            print(node.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitDeprecated(DeprecatedTree node, Void p) {
        try {
            printTagName(node);
            if (!node.getBody().isEmpty()) {
                print(" ");
                print(node.getBody());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitDocComment(DocCommentTree node, Void p) {
        try {
            List<? extends DocTree> fs = node.getFirstSentence();
            List<? extends DocTree> b = node.getBody();
            List<? extends DocTree> t = node.getBlockTags();
            print(fs);
            if (!fs.isEmpty() && !b.isEmpty())
                print(" ");
            print(b);
            if ((!fs.isEmpty() || !b.isEmpty()) && !t.isEmpty())
                print("\n");
            print(t, "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitDocRoot(DocRootTree node, Void p) {
        try {
            print("{");
            printTagName(node);
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitEndElement(EndElementTree node, Void p) {
        try {
            print("</");
            print(node.getName());
            print(">");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitEntity(EntityTree node, Void p) {
        try {
            print("&");
            print(node.getName());
            print(";");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitErroneous(ErroneousTree node, Void p) {
        try {
            print(node.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitIdentifier(IdentifierTree node, Void p) {
        try {
            print(node.getName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitInheritDoc(InheritDocTree node, Void p) {
        try {
            print("{");
            printTagName(node);
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitLink(LinkTree node, Void p) {
        try {
            print("{");
            printTagName(node);
            print(" ");
            print(node.getReference());
            if (!node.getLabel().isEmpty()) {
                print(" ");
                print(node.getLabel());
            }
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitLiteral(LiteralTree node, Void p) {
        try {
            print("{");
            printTagName(node);
            print(" ");
            print(node.getBody());
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitParam(ParamTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            if (node.isTypeParameter()) print("<");
            print(node.getName());
            if (node.isTypeParameter()) print(">");
            if (!node.getDescription().isEmpty()) {
                print(" ");
                print(node.getDescription());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitReference(ReferenceTree node, Void p) {
        try {
            print(node.getSignature());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitReturn(ReturnTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            print(node.getDescription());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitSee(SeeTree node, Void p) {
        try {
            printTagName(node);
            boolean first = true;
            boolean needSep = true;
            for (DocTree t : node.getReference()) {
                if (needSep) print(" ");
                needSep = (first && (t instanceof ReferenceTree));
                first = false;
                print(t);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitSerial(SerialTree node, Void p) {
        try {
            printTagName(node);
            if (!node.getDescription().isEmpty()) {
                print(" ");
                print(node.getDescription());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitSerialData(SerialDataTree node, Void p) {
        try {
            printTagName(node);
            if (!node.getDescription().isEmpty()) {
                print(" ");
                print(node.getDescription());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitSerialField(SerialFieldTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            print(node.getName());
            print(" ");
            print(node.getType());
            if (!node.getDescription().isEmpty()) {
                print(" ");
                print(node.getDescription());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitSince(SinceTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            print(node.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitStartElement(StartElementTree node, Void p) {
        try {
            print("<");
            print(node.getName());
            List<? extends DocTree> attrs = node.getAttributes();
            if (!attrs.isEmpty()) {
                print(" ");
                print(attrs);
                DocTree last = node.getAttributes().get(attrs.size() - 1);
                if (node.isSelfClosing() && last instanceof AttributeTree
                        && ((AttributeTree) last).getValueKind() == ValueKind.UNQUOTED)
                    print(" ");
            }
            if (node.isSelfClosing())
                print("/");
            print(">");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitText(TextTree node, Void p) {
        try {
            print(node.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitThrows(ThrowsTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            print(node.getExceptionName());
            if (!node.getDescription().isEmpty()) {
                print(" ");
                print(node.getDescription());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
        try {
            print("@");
            print(node.getTagName());
            print(" ");
            print(node.getContent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitUnknownInlineTag(UnknownInlineTagTree node, Void p) {
        try {
            print("{");
            print("@");
            print(node.getTagName());
            print(" ");
            print(node.getContent());
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitValue(ValueTree node, Void p) {
        try {
            print("{");
            printTagName(node);
            if (node.getReference() != null) {
                print(" ");
                print(node.getReference());
            }
            print("}");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitVersion(VersionTree node, Void p) {
        try {
            printTagName(node);
            print(" ");
            print(node.getBody());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    public Void visitOther(DocTree node, Void p) {
        try {
            print("(UNKNOWN: " + node + ")");
            println();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return null;
    }

    private static class UncheckedIOException extends Error {
        static final long serialVersionUID = -4032692679158424751L;

        UncheckedIOException(IOException e) {
            super(e.getMessage(), e);
        }
    }
}
