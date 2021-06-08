package com.sun.source.doctree;
@jdk.Exported
public interface DocTreeVisitor<R,P> {
    R visitAttribute(AttributeTree node, P p);
    R visitAuthor(AuthorTree node, P p);
    R visitComment(CommentTree node, P p);
    R visitDeprecated(DeprecatedTree node, P p);
    R visitDocComment(DocCommentTree node, P p);
    R visitDocRoot(DocRootTree node, P p);
    R visitEndElement(EndElementTree node, P p);
    R visitEntity(EntityTree node, P p);
    R visitErroneous(ErroneousTree node, P p);
    R visitIdentifier(IdentifierTree node, P p);
    R visitInheritDoc(InheritDocTree node, P p);
    R visitLink(LinkTree node, P p);
    R visitLiteral(LiteralTree node, P p);
    R visitParam(ParamTree node, P p);
    R visitReference(ReferenceTree node, P p);
    R visitReturn(ReturnTree node, P p);
    R visitSee(SeeTree node, P p);
    R visitSerial(SerialTree node, P p);
    R visitSerialData(SerialDataTree node, P p);
    R visitSerialField(SerialFieldTree node, P p);
    R visitSince(SinceTree node, P p);
    R visitStartElement(StartElementTree node, P p);
    R visitText(TextTree node, P p);
    R visitThrows(ThrowsTree node, P p);
    R visitUnknownBlockTag(UnknownBlockTagTree node, P p);
    R visitUnknownInlineTag(UnknownInlineTagTree node, P p);
    R visitValue(ValueTree node, P p);
    R visitVersion(VersionTree node, P p);
    R visitOther(DocTree node, P p);
}
