package com.github.api.sun.source.util;

import com.github.api.sun.source.doctree.*;

@jdk.Exported
public class DocTreeScanner<R, P> implements DocTreeVisitor<R, P> {
    public R scan(DocTree node, P p) {
        return (node == null) ? null : node.accept(this, p);
    }

    private R scanAndReduce(DocTree node, P p, R r) {
        return reduce(scan(node, p), r);
    }

    public R scan(Iterable<? extends DocTree> nodes, P p) {
        R r = null;
        if (nodes != null) {
            boolean first = true;
            for (DocTree node : nodes) {
                r = (first ? scan(node, p) : scanAndReduce(node, p, r));
                first = false;
            }
        }
        return r;
    }

    private R scanAndReduce(Iterable<? extends DocTree> nodes, P p, R r) {
        return reduce(scan(nodes, p), r);
    }

    public R reduce(R r1, R r2) {
        return r1;
    }

    @Override
    public R visitAttribute(AttributeTree node, P p) {
        return null;
    }

    @Override
    public R visitAuthor(AuthorTree node, P p) {
        return scan(node.getName(), p);
    }

    @Override
    public R visitComment(CommentTree node, P p) {
        return null;
    }

    @Override
    public R visitDeprecated(DeprecatedTree node, P p) {
        return scan(node.getBody(), p);
    }

    @Override
    public R visitDocComment(DocCommentTree node, P p) {
        R r = scan(node.getFirstSentence(), p);
        r = scanAndReduce(node.getBody(), p, r);
        r = scanAndReduce(node.getBlockTags(), p, r);
        return r;
    }

    @Override
    public R visitDocRoot(DocRootTree node, P p) {
        return null;
    }

    @Override
    public R visitEndElement(EndElementTree node, P p) {
        return null;
    }

    @Override
    public R visitEntity(EntityTree node, P p) {
        return null;
    }

    @Override
    public R visitErroneous(ErroneousTree node, P p) {
        return null;
    }

    @Override
    public R visitIdentifier(IdentifierTree node, P p) {
        return null;
    }

    @Override
    public R visitInheritDoc(InheritDocTree node, P p) {
        return null;
    }

    @Override
    public R visitLink(LinkTree node, P p) {
        R r = scan(node.getReference(), p);
        r = scanAndReduce(node.getLabel(), p, r);
        return r;
    }

    @Override
    public R visitLiteral(LiteralTree node, P p) {
        return null;
    }

    @Override
    public R visitParam(ParamTree node, P p) {
        R r = scan(node.getName(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    @Override
    public R visitReference(ReferenceTree node, P p) {
        return null;
    }

    @Override
    public R visitReturn(ReturnTree node, P p) {
        return scan(node.getDescription(), p);
    }

    @Override
    public R visitSee(SeeTree node, P p) {
        return scan(node.getReference(), p);
    }

    @Override
    public R visitSerial(SerialTree node, P p) {
        return scan(node.getDescription(), p);
    }

    @Override
    public R visitSerialData(SerialDataTree node, P p) {
        return scan(node.getDescription(), p);
    }

    @Override
    public R visitSerialField(SerialFieldTree node, P p) {
        R r = scan(node.getName(), p);
        r = scanAndReduce(node.getType(), p, r);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    @Override
    public R visitSince(SinceTree node, P p) {
        return scan(node.getBody(), p);
    }

    @Override
    public R visitStartElement(StartElementTree node, P p) {
        return scan(node.getAttributes(), p);
    }

    @Override
    public R visitText(TextTree node, P p) {
        return null;
    }

    @Override
    public R visitThrows(ThrowsTree node, P p) {
        R r = scan(node.getExceptionName(), p);
        r = scanAndReduce(node.getDescription(), p, r);
        return r;
    }

    @Override
    public R visitUnknownBlockTag(UnknownBlockTagTree node, P p) {
        return scan(node.getContent(), p);
    }

    @Override
    public R visitUnknownInlineTag(UnknownInlineTagTree node, P p) {
        return scan(node.getContent(), p);
    }

    @Override
    public R visitValue(ValueTree node, P p) {
        return scan(node.getReference(), p);
    }

    @Override
    public R visitVersion(VersionTree node, P p) {
        return scan(node.getBody(), p);
    }

    @Override
    public R visitOther(DocTree node, P p) {
        return null;
    }
}
