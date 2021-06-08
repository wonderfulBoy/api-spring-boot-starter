package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ParamTaglet extends BaseTaglet implements InheritableTaglet {

    public ParamTaglet() {
        name = "param";
    }

    private static Map<String, String> getRankMap(Object[] params) {
        if (params == null) {
            return null;
        }
        HashMap<String, String> result = new HashMap<String, String>();
        for (int i = 0; i < params.length; i++) {
            String name = params[i] instanceof Parameter ?
                    ((Parameter) params[i]).name() :
                    ((TypeVariable) params[i]).typeName();
            result.put(name, String.valueOf(i));
        }
        return result;
    }

    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        if (input.tagId == null) {
            input.isTypeVariableParamTag = ((ParamTag) input.tag).isTypeParameter();
            Object[] parameters = input.isTypeVariableParamTag ?
                    ((MethodDoc) input.tag.holder()).typeParameters() :
                    ((MethodDoc) input.tag.holder()).parameters();
            String target = ((ParamTag) input.tag).parameterName();
            int i;
            for (i = 0; i < parameters.length; i++) {
                String name = parameters[i] instanceof Parameter ?
                        ((Parameter) parameters[i]).name() :
                        ((TypeVariable) parameters[i]).typeName();
                if (name.equals(target)) {
                    input.tagId = String.valueOf(i);
                    break;
                }
            }
            if (i == parameters.length) {


                return;
            }
        }
        ParamTag[] tags = input.isTypeVariableParamTag ?
                ((MethodDoc) input.element).typeParamTags() : ((MethodDoc) input.element).paramTags();
        Map<String, String> rankMap = getRankMap(input.isTypeVariableParamTag ?
                ((MethodDoc) input.element).typeParameters() :
                ((MethodDoc) input.element).parameters());
        for (int i = 0; i < tags.length; i++) {
            if (rankMap.containsKey(tags[i].parameterName()) &&
                    rankMap.get(tags[i].parameterName()).equals((input.tagId))) {
                output.holder = input.element;
                output.holderTag = tags[i];
                output.inlineTags = input.isFirstSentence ?
                        tags[i].firstSentenceTags() : tags[i].inlineTags();
                return;
            }
        }
    }

    public boolean inField() {
        return false;
    }

    public boolean inMethod() {
        return true;
    }

    public boolean inOverview() {
        return false;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        if (holder instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc member = (ExecutableMemberDoc) holder;
            Content output = getTagletOutput(false, member, writer,
                    member.typeParameters(), member.typeParamTags());
            output.addContent(getTagletOutput(true, member, writer,
                    member.parameters(), member.paramTags()));
            return output;
        } else {
            ClassDoc classDoc = (ClassDoc) holder;
            return getTagletOutput(false, classDoc, writer,
                    classDoc.typeParameters(), classDoc.typeParamTags());
        }
    }

    private Content getTagletOutput(boolean isNonTypeParams, Doc holder,
                                    TagletWriter writer, Object[] formalParameters, ParamTag[] paramTags) {
        Content result = writer.getOutputInstance();
        Set<String> alreadyDocumented = new HashSet<String>();
        if (paramTags.length > 0) {
            result.addContent(
                    processParamTags(isNonTypeParams, paramTags,
                            getRankMap(formalParameters), writer, alreadyDocumented)
            );
        }
        if (alreadyDocumented.size() != formalParameters.length) {


            result.addContent(getInheritedTagletOutput(isNonTypeParams, holder,
                    writer, formalParameters, alreadyDocumented));
        }
        return result;
    }

    private Content getInheritedTagletOutput(boolean isNonTypeParams, Doc holder,
                                             TagletWriter writer, Object[] formalParameters,
                                             Set<String> alreadyDocumented) {
        Content result = writer.getOutputInstance();
        if ((!alreadyDocumented.contains(null)) &&
                holder instanceof MethodDoc) {
            for (int i = 0; i < formalParameters.length; i++) {
                if (alreadyDocumented.contains(String.valueOf(i))) {
                    continue;
                }


                DocFinder.Output inheritedDoc =
                        DocFinder.search(new DocFinder.Input((MethodDoc) holder, this,
                                String.valueOf(i), !isNonTypeParams));
                if (inheritedDoc.inlineTags != null &&
                        inheritedDoc.inlineTags.length > 0) {
                    result.addContent(
                            processParamTag(isNonTypeParams, writer,
                                    (ParamTag) inheritedDoc.holderTag,
                                    isNonTypeParams ?
                                            ((Parameter) formalParameters[i]).name() :
                                            ((TypeVariable) formalParameters[i]).typeName(),
                                    alreadyDocumented.size() == 0));
                }
                alreadyDocumented.add(String.valueOf(i));
            }
        }
        return result;
    }

    private Content processParamTags(boolean isNonTypeParams,
                                     ParamTag[] paramTags, Map<String, String> rankMap, TagletWriter writer,
                                     Set<String> alreadyDocumented) {
        Content result = writer.getOutputInstance();
        if (paramTags.length > 0) {
            for (int i = 0; i < paramTags.length; ++i) {
                ParamTag pt = paramTags[i];
                String paramName = isNonTypeParams ?
                        pt.parameterName() : "<" + pt.parameterName() + ">";
                if (!rankMap.containsKey(pt.parameterName())) {
                    writer.getMsgRetriever().warning(pt.position(),
                            isNonTypeParams ?
                                    "doclet.Parameters_warn" :
                                    "doclet.Type_Parameters_warn",
                            paramName);
                }
                String rank = rankMap.get(pt.parameterName());
                if (rank != null && alreadyDocumented.contains(rank)) {
                    writer.getMsgRetriever().warning(pt.position(),
                            isNonTypeParams ?
                                    "doclet.Parameters_dup_warn" :
                                    "doclet.Type_Parameters_dup_warn",
                            paramName);
                }
                result.addContent(processParamTag(isNonTypeParams, writer, pt,
                        pt.parameterName(), alreadyDocumented.size() == 0));
                alreadyDocumented.add(rank);
            }
        }
        return result;
    }

    private Content processParamTag(boolean isNonTypeParams,
                                    TagletWriter writer, ParamTag paramTag, String name,
                                    boolean isFirstParam) {
        Content result = writer.getOutputInstance();
        String header = writer.configuration().getText(
                isNonTypeParams ? "doclet.Parameters" : "doclet.TypeParameters");
        if (isFirstParam) {
            result.addContent(writer.getParamHeader(header));
        }
        result.addContent(writer.paramTagOutput(paramTag,
                name));
        return result;
    }
}
