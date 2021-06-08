package com.github.api.sun.tools.doclets.internal.toolkit.builders;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.MemberDoc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.AnnotationTypeRequiredMemberWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotationTypeRequiredMemberBuilder extends AbstractMemberBuilder {

    protected ClassDoc classDoc;

    protected VisibleMemberMap visibleMemberMap;

    protected AnnotationTypeRequiredMemberWriter writer;

    protected List<ProgramElementDoc> members;

    protected int currentMemberIndex;

    protected AnnotationTypeRequiredMemberBuilder(Context context,
                                                  ClassDoc classDoc,
                                                  AnnotationTypeRequiredMemberWriter writer,
                                                  int memberType) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        this.visibleMemberMap = new VisibleMemberMap(classDoc, memberType,
                configuration);
        this.members = new ArrayList<ProgramElementDoc>(
                this.visibleMemberMap.getMembersFor(classDoc));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(this.members, configuration.getMemberComparator());
        }
    }

    public static AnnotationTypeRequiredMemberBuilder getInstance(
            Context context, ClassDoc classDoc,
            AnnotationTypeRequiredMemberWriter writer) {
        return new AnnotationTypeRequiredMemberBuilder(context, classDoc,
                writer,
                VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED);
    }

    public String getName() {
        return "AnnotationTypeRequiredMemberDetails";
    }

    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    public boolean hasMembersToDocument() {
        return members.size() > 0;
    }

    public void buildAnnotationTypeRequiredMember(XMLNode node, Content memberDetailsTree) {
        buildAnnotationTypeMember(node, memberDetailsTree);
    }

    public void buildAnnotationTypeMember(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = members.size();
        if (size > 0) {
            writer.addAnnotationDetailsMarker(memberDetailsTree);
            for (currentMemberIndex = 0; currentMemberIndex < size;
                 currentMemberIndex++) {
                Content detailsTree = writer.getMemberTreeHeader();
                writer.addAnnotationDetailsTreeHeader(classDoc, detailsTree);
                Content annotationDocTree = writer.getAnnotationDocTreeHeader(
                        (MemberDoc) members.get(currentMemberIndex), detailsTree);
                buildChildren(node, annotationDocTree);
                detailsTree.addContent(writer.getAnnotationDoc(
                        annotationDocTree, (currentMemberIndex == size - 1)));
                memberDetailsTree.addContent(writer.getAnnotationDetails(detailsTree));
            }
        }
    }

    public void buildSignature(XMLNode node, Content annotationDocTree) {
        annotationDocTree.addContent(
                writer.getSignature((MemberDoc) members.get(currentMemberIndex)));
    }

    public void buildDeprecationInfo(XMLNode node, Content annotationDocTree) {
        writer.addDeprecated((MemberDoc) members.get(currentMemberIndex),
                annotationDocTree);
    }

    public void buildMemberComments(XMLNode node, Content annotationDocTree) {
        if (!configuration.nocomment) {
            writer.addComments((MemberDoc) members.get(currentMemberIndex),
                    annotationDocTree);
        }
    }

    public void buildTagInfo(XMLNode node, Content annotationDocTree) {
        writer.addTags((MemberDoc) members.get(currentMemberIndex),
                annotationDocTree);
    }

    public AnnotationTypeRequiredMemberWriter getWriter() {
        return writer;
    }
}
