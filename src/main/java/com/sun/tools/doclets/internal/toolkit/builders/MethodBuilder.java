package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.MethodWriter;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MethodBuilder extends AbstractMemberBuilder {

    private final ClassDoc classDoc;
    private final VisibleMemberMap visibleMemberMap;
    private final MethodWriter writer;
    private int currentMethodIndex;
    private List<ProgramElementDoc> methods;

    private MethodBuilder(Context context,
                          ClassDoc classDoc,
                          MethodWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap = new VisibleMemberMap(
                classDoc,
                VisibleMemberMap.METHODS,
                configuration);
        methods =
                new ArrayList<ProgramElementDoc>(visibleMemberMap.getLeafClassMembers(
                        configuration));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(methods, configuration.getMemberComparator());
        }
    }

    public static MethodBuilder getInstance(Context context,
                                            ClassDoc classDoc, MethodWriter writer) {
        return new MethodBuilder(context, classDoc, writer);
    }

    public String getName() {
        return "MethodDetails";
    }

    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    public boolean hasMembersToDocument() {
        return methods.size() > 0;
    }

    public void buildMethodDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = methods.size();
        if (size > 0) {
            Content methodDetailsTree = writer.getMethodDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentMethodIndex = 0; currentMethodIndex < size;
                 currentMethodIndex++) {
                Content methodDocTree = writer.getMethodDocTreeHeader(
                        (MethodDoc) methods.get(currentMethodIndex),
                        methodDetailsTree);
                buildChildren(node, methodDocTree);
                methodDetailsTree.addContent(writer.getMethodDoc(
                        methodDocTree, (currentMethodIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getMethodDetails(methodDetailsTree));
        }
    }

    public void buildSignature(XMLNode node, Content methodDocTree) {
        methodDocTree.addContent(
                writer.getSignature((MethodDoc) methods.get(currentMethodIndex)));
    }

    public void buildDeprecationInfo(XMLNode node, Content methodDocTree) {
        writer.addDeprecated(
                (MethodDoc) methods.get(currentMethodIndex), methodDocTree);
    }

    public void buildMethodComments(XMLNode node, Content methodDocTree) {
        if (!configuration.nocomment) {
            MethodDoc method = (MethodDoc) methods.get(currentMethodIndex);
            if (method.inlineTags().length == 0) {
                DocFinder.Output docs = DocFinder.search(
                        new DocFinder.Input(method));
                method = docs.inlineTags != null && docs.inlineTags.length > 0 ?
                        (MethodDoc) docs.holder : method;
            }


            writer.addComments(method.containingClass(), method, methodDocTree);
        }
    }

    public void buildTagInfo(XMLNode node, Content methodDocTree) {
        writer.addTags((MethodDoc) methods.get(currentMethodIndex),
                methodDocTree);
    }

    public MethodWriter getWriter() {
        return writer;
    }
}
