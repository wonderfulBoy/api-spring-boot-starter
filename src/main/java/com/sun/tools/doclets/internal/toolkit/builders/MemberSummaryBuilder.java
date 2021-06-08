package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.text.MessageFormat;
import java.util.*;

public class MemberSummaryBuilder extends AbstractMemberBuilder {

    public static final String NAME = "MemberSummary";

    private final VisibleMemberMap[] visibleMemberMaps;
    private final ClassDoc classDoc;
    private MemberSummaryWriter[] memberSummaryWriters;

    private MemberSummaryBuilder(Context context, ClassDoc classDoc) {
        super(context);
        this.classDoc = classDoc;
        visibleMemberMaps =
                new VisibleMemberMap[VisibleMemberMap.NUM_MEMBER_TYPES];
        for (int i = 0; i < VisibleMemberMap.NUM_MEMBER_TYPES; i++) {
            visibleMemberMaps[i] =
                    new VisibleMemberMap(
                            classDoc,
                            i,
                            configuration);
        }
    }

    public static MemberSummaryBuilder getInstance(
            ClassWriter classWriter, Context context)
            throws Exception {
        MemberSummaryBuilder builder = new MemberSummaryBuilder(context,
                classWriter.getClassDoc());
        builder.memberSummaryWriters =
                new MemberSummaryWriter[VisibleMemberMap.NUM_MEMBER_TYPES];
        WriterFactory wf = context.configuration.getWriterFactory();
        for (int i = 0; i < VisibleMemberMap.NUM_MEMBER_TYPES; i++) {
            builder.memberSummaryWriters[i] =
                    builder.visibleMemberMaps[i].noVisibleMembers() ?
                            null :
                            wf.getMemberSummaryWriter(classWriter, i);
        }
        return builder;
    }

    public static MemberSummaryBuilder getInstance(
            AnnotationTypeWriter annotationTypeWriter, Context context)
            throws Exception {
        MemberSummaryBuilder builder = new MemberSummaryBuilder(context,
                annotationTypeWriter.getAnnotationTypeDoc());
        builder.memberSummaryWriters =
                new MemberSummaryWriter[VisibleMemberMap.NUM_MEMBER_TYPES];
        WriterFactory wf = context.configuration.getWriterFactory();
        for (int i = 0; i < VisibleMemberMap.NUM_MEMBER_TYPES; i++) {
            builder.memberSummaryWriters[i] =
                    builder.visibleMemberMaps[i].noVisibleMembers() ?
                            null :
                            wf.getMemberSummaryWriter(
                                    annotationTypeWriter, i);
        }
        return builder;
    }

    public String getName() {
        return NAME;
    }

    public VisibleMemberMap getVisibleMemberMap(int type) {
        return visibleMemberMaps[type];
    }

    public MemberSummaryWriter getMemberSummaryWriter(int type) {
        return memberSummaryWriters[type];
    }

    public List<ProgramElementDoc> members(int type) {
        return visibleMemberMaps[type].getLeafClassMembers(configuration);
    }

    public boolean hasMembersToDocument() {
        if (classDoc instanceof AnnotationTypeDoc) {
            return ((AnnotationTypeDoc) classDoc).elements().length > 0;
        }
        for (int i = 0; i < VisibleMemberMap.NUM_MEMBER_TYPES; i++) {
            VisibleMemberMap members = visibleMemberMaps[i];
            if (!members.noVisibleMembers()) {
                return true;
            }
        }
        return false;
    }

    public void buildEnumConstantsSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.ENUM_CONSTANTS];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.ENUM_CONSTANTS];
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    public void buildAnnotationTypeFieldsSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.ANNOTATION_TYPE_FIELDS];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.ANNOTATION_TYPE_FIELDS];
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    public void buildAnnotationTypeOptionalMemberSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL];
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    public void buildAnnotationTypeRequiredMemberSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED];
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    public void buildFieldsSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.FIELDS];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.FIELDS];
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    public void buildPropertiesSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.PROPERTIES];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.PROPERTIES];
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    public void buildNestedClassesSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.INNERCLASSES];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.INNERCLASSES];
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    public void buildMethodsSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.METHODS];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.METHODS];
        addSummary(writer, visibleMemberMap, true, memberSummaryTree);
    }

    public void buildConstructorsSummary(XMLNode node, Content memberSummaryTree) {
        MemberSummaryWriter writer =
                memberSummaryWriters[VisibleMemberMap.CONSTRUCTORS];
        VisibleMemberMap visibleMemberMap =
                visibleMemberMaps[VisibleMemberMap.CONSTRUCTORS];
        addSummary(writer, visibleMemberMap, false, memberSummaryTree);
    }

    private void buildSummary(MemberSummaryWriter writer,
                              VisibleMemberMap visibleMemberMap, LinkedList<Content> summaryTreeList) {
        List<ProgramElementDoc> members = new ArrayList<ProgramElementDoc>(visibleMemberMap.getLeafClassMembers(
                configuration));
        if (members.size() > 0) {
            Collections.sort(members);
            List<Content> tableContents = new LinkedList<Content>();
            for (int i = 0; i < members.size(); i++) {
                ProgramElementDoc member = members.get(i);
                final ProgramElementDoc propertyDoc =
                        visibleMemberMap.getPropertyMemberDoc(member);
                if (propertyDoc != null) {
                    processProperty(visibleMemberMap, member, propertyDoc);
                }
                Tag[] firstSentenceTags = member.firstSentenceTags();
                if (member instanceof MethodDoc && firstSentenceTags.length == 0) {


                    DocFinder.Output inheritedDoc =
                            DocFinder.search(new DocFinder.Input(member));
                    if (inheritedDoc.holder != null
                            && inheritedDoc.holder.firstSentenceTags().length > 0) {
                        firstSentenceTags = inheritedDoc.holder.firstSentenceTags();
                    }
                }
                writer.addMemberSummary(classDoc, member, firstSentenceTags,
                        tableContents, i);
            }
            summaryTreeList.add(writer.getSummaryTableTree(classDoc, tableContents));
        }
    }

    private void processProperty(VisibleMemberMap visibleMemberMap,
                                 ProgramElementDoc member,
                                 ProgramElementDoc propertyDoc) {
        StringBuilder commentTextBuilder = new StringBuilder();
        final boolean isSetter = isSetter(member);
        final boolean isGetter = isGetter(member);
        if (isGetter || isSetter) {

            if (isSetter) {
                commentTextBuilder.append(
                        MessageFormat.format(
                                configuration.getText("doclet.PropertySetterWithName"),
                                Util.propertyNameFromMethodName(member.name())));
            }
            if (isGetter) {
                commentTextBuilder.append(
                        MessageFormat.format(
                                configuration.getText("doclet.PropertyGetterWithName"),
                                Util.propertyNameFromMethodName(member.name())));
            }
            if (propertyDoc.commentText() != null
                    && !propertyDoc.commentText().isEmpty()) {
                commentTextBuilder.append(" \n @propertyDescription ");
            }
        }
        commentTextBuilder.append(propertyDoc.commentText());

        List<Tag> allTags = new LinkedList<Tag>();
        String[] tagNames = {"@defaultValue", "@since"};
        for (String tagName : tagNames) {
            Tag[] tags = propertyDoc.tags(tagName);
            if (tags != null) {
                allTags.addAll(Arrays.asList(tags));
            }
        }
        for (Tag tag : allTags) {
            commentTextBuilder.append("\n")
                    .append(tag.name())
                    .append(" ")
                    .append(tag.text());
        }

        if (!isGetter && !isSetter) {
            MethodDoc getter = (MethodDoc) visibleMemberMap.getGetterForProperty(member);
            MethodDoc setter = (MethodDoc) visibleMemberMap.getSetterForProperty(member);
            if ((null != getter)
                    && (commentTextBuilder.indexOf("@see #" + getter.name()) == -1)) {
                commentTextBuilder.append("\n @see #")
                        .append(getter.name())
                        .append("() ");
            }
            if ((null != setter)
                    && (commentTextBuilder.indexOf("@see #" + setter.name()) == -1)) {
                String typeName = setter.parameters()[0].typeName();

                typeName = typeName.split("<")[0];
                if (typeName.contains(".")) {
                    typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
                }
                commentTextBuilder.append("\n @see #").append(setter.name());
                if (setter.parameters()[0].type().asTypeVariable() == null) {
                    commentTextBuilder.append("(").append(typeName).append(")");
                }
                commentTextBuilder.append(" \n");
            }
        }
        member.setRawCommentText(commentTextBuilder.toString());
    }

    private boolean isGetter(ProgramElementDoc ped) {
        final String pedName = ped.name();
        return pedName.startsWith("get") || pedName.startsWith("is");
    }

    private boolean isSetter(ProgramElementDoc ped) {
        return ped.name().startsWith("set");
    }

    private void buildInheritedSummary(MemberSummaryWriter writer,
                                       VisibleMemberMap visibleMemberMap, LinkedList<Content> summaryTreeList) {
        for (Iterator<ClassDoc> iter = visibleMemberMap.getVisibleClassesList().iterator();
             iter.hasNext(); ) {
            ClassDoc inhclass = iter.next();
            if (!(inhclass.isPublic() ||
                    Util.isLinkable(inhclass, configuration))) {
                continue;
            }
            if (inhclass == classDoc) {
                continue;
            }
            List<ProgramElementDoc> inhmembers = visibleMemberMap.getMembersFor(inhclass);
            if (inhmembers.size() > 0) {
                Collections.sort(inhmembers);
                Content inheritedTree = writer.getInheritedSummaryHeader(inhclass);
                Content linksTree = writer.getInheritedSummaryLinksTree();
                for (int j = 0; j < inhmembers.size(); ++j) {
                    writer.addInheritedMemberSummary(
                            inhclass.isPackagePrivate() &&
                                    !Util.isLinkable(inhclass, configuration) ?
                                    classDoc : inhclass,
                            inhmembers.get(j),
                            j == 0,
                            j == inhmembers.size() - 1, linksTree);
                }
                inheritedTree.addContent(linksTree);
                summaryTreeList.add(writer.getMemberTree(inheritedTree));
            }
        }
    }

    private void addSummary(MemberSummaryWriter writer,
                            VisibleMemberMap visibleMemberMap, boolean showInheritedSummary,
                            Content memberSummaryTree) {
        LinkedList<Content> summaryTreeList = new LinkedList<Content>();
        buildSummary(writer, visibleMemberMap, summaryTreeList);
        if (showInheritedSummary)
            buildInheritedSummary(writer, visibleMemberMap, summaryTreeList);
        if (!summaryTreeList.isEmpty()) {
            Content memberTree = writer.getMemberSummaryHeader(
                    classDoc, memberSummaryTree);
            for (int i = 0; i < summaryTreeList.size(); i++) {
                memberTree.addContent(summaryTreeList.get(i));
            }
            memberSummaryTree.addContent(writer.getMemberTree(memberTree));
        }
    }
}
