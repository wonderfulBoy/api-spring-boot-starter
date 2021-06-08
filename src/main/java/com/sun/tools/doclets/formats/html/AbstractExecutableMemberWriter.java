package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocletConstants;

public abstract class AbstractExecutableMemberWriter extends AbstractMemberWriter {
    public AbstractExecutableMemberWriter(SubWriterHolderWriter writer,
                                          ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public AbstractExecutableMemberWriter(SubWriterHolderWriter writer) {
        super(writer);
    }

    protected void addTypeParameters(ExecutableMemberDoc member, Content htmltree) {
        Content typeParameters = getTypeParameters(member);
        if (!typeParameters.isEmpty()) {
            htmltree.addContent(typeParameters);
            htmltree.addContent(writer.getSpace());
        }
    }

    protected Content getTypeParameters(ExecutableMemberDoc member) {
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.MEMBER_TYPE_PARAMS, member);
        return writer.getTypeParameterLinks(linkInfo);
    }

    protected Content getDeprecatedLink(ProgramElementDoc member) {
        ExecutableMemberDoc emd = (ExecutableMemberDoc) member;
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER, emd,
                emd.qualifiedName() + emd.flatSignature());
    }

    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
                                  Content tdSummary) {
        ExecutableMemberDoc emd = (ExecutableMemberDoc) member;
        String name = emd.name();
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, cd, emd,
                        name, false));
        Content code = HtmlTree.CODE(memberLink);
        addParameters(emd, false, code, name.length() - 1);
        tdSummary.addContent(code);
    }

    protected void addInheritedSummaryLink(ClassDoc cd,
                                           ProgramElementDoc member, Content linksTree) {
        linksTree.addContent(
                writer.getDocLink(LinkInfoImpl.Kind.MEMBER, cd, (MemberDoc) member,
                        member.name(), false));
    }

    protected void addParam(ExecutableMemberDoc member, Parameter param,
                            boolean isVarArg, Content tree) {
        if (param.type() != null) {
            Content link = writer.getLink(new LinkInfoImpl(
                    configuration, LinkInfoImpl.Kind.EXECUTABLE_MEMBER_PARAM,
                    param.type()).varargs(isVarArg));
            tree.addContent(link);
        }
        if (param.name().length() > 0) {
            tree.addContent(writer.getSpace());
            tree.addContent(param.name());
        }
    }

    protected void addReceiverAnnotations(ExecutableMemberDoc member, Type rcvrType,
                                          AnnotationDesc[] descList, Content tree) {
        writer.addReceiverAnnotationInfo(member, descList, tree);
        tree.addContent(writer.getSpace());
        tree.addContent(rcvrType.typeName());
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, rcvrType);
        tree.addContent(writer.getTypeParameterLinks(linkInfo));
        tree.addContent(writer.getSpace());
        tree.addContent("this");
    }

    protected void addParameters(ExecutableMemberDoc member, Content htmltree, int indentSize) {
        addParameters(member, true, htmltree, indentSize);
    }

    protected void addParameters(ExecutableMemberDoc member,
                                 boolean includeAnnotations, Content htmltree, int indentSize) {
        htmltree.addContent("(");
        String sep = "";
        Parameter[] params = member.parameters();
        String indent = makeSpace(indentSize + 1);
        Type rcvrType = member.receiverType();
        if (includeAnnotations && rcvrType instanceof AnnotatedType) {
            AnnotationDesc[] descList = rcvrType.asAnnotatedType().annotations();
            if (descList.length > 0) {
                addReceiverAnnotations(member, rcvrType, descList, htmltree);
                sep = "," + DocletConstants.NL + indent;
            }
        }
        int paramstart;
        for (paramstart = 0; paramstart < params.length; paramstart++) {
            htmltree.addContent(sep);
            Parameter param = params[paramstart];
            if (!param.name().startsWith("this$")) {
                if (includeAnnotations) {
                    boolean foundAnnotations =
                            writer.addAnnotationInfo(indent.length(),
                                    member, param, htmltree);
                    if (foundAnnotations) {
                        htmltree.addContent(DocletConstants.NL);
                        htmltree.addContent(indent);
                    }
                }
                addParam(member, param,
                        (paramstart == params.length - 1) && member.isVarArgs(), htmltree);
                break;
            }
        }
        for (int i = paramstart + 1; i < params.length; i++) {
            htmltree.addContent(",");
            htmltree.addContent(DocletConstants.NL);
            htmltree.addContent(indent);
            if (includeAnnotations) {
                boolean foundAnnotations =
                        writer.addAnnotationInfo(indent.length(), member, params[i],
                                htmltree);
                if (foundAnnotations) {
                    htmltree.addContent(DocletConstants.NL);
                    htmltree.addContent(indent);
                }
            }
            addParam(member, params[i], (i == params.length - 1) && member.isVarArgs(),
                    htmltree);
        }
        htmltree.addContent(")");
    }

    protected void addExceptions(ExecutableMemberDoc member, Content htmltree, int indentSize) {
        Type[] exceptions = member.thrownExceptionTypes();
        if (exceptions.length > 0) {
            LinkInfoImpl memberTypeParam = new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.MEMBER, member);
            String indent = makeSpace(indentSize + 1 - 7);
            htmltree.addContent(DocletConstants.NL);
            htmltree.addContent(indent);
            htmltree.addContent("throws ");
            indent = makeSpace(indentSize + 1);
            Content link = writer.getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.MEMBER, exceptions[0]));
            htmltree.addContent(link);
            for (int i = 1; i < exceptions.length; i++) {
                htmltree.addContent(",");
                htmltree.addContent(DocletConstants.NL);
                htmltree.addContent(indent);
                Content exceptionLink = writer.getLink(new LinkInfoImpl(
                        configuration, LinkInfoImpl.Kind.MEMBER, exceptions[i]));
                htmltree.addContent(exceptionLink);
            }
        }
    }

    protected ClassDoc implementsMethodInIntfac(MethodDoc method,
                                                ClassDoc[] intfacs) {
        for (int i = 0; i < intfacs.length; i++) {
            MethodDoc[] methods = intfacs[i].methods();
            if (methods.length > 0) {
                for (int j = 0; j < methods.length; j++) {
                    if (methods[j].name().equals(method.name()) &&
                            methods[j].signature().equals(method.signature())) {
                        return intfacs[i];
                    }
                }
            }
        }
        return null;
    }

    protected String getErasureAnchor(ExecutableMemberDoc emd) {
        StringBuilder buf = new StringBuilder(emd.name() + "(");
        Parameter[] params = emd.parameters();
        boolean foundTypeVariable = false;
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                buf.append(",");
            }
            Type t = params[i].type();
            foundTypeVariable = foundTypeVariable || t.asTypeVariable() != null;
            buf.append(t.isPrimitive() ?
                    t.typeName() : t.asClassDoc().qualifiedName());
            buf.append(t.dimension());
        }
        buf.append(")");
        return foundTypeVariable ? writer.getName(buf.toString()) : null;
    }
}
