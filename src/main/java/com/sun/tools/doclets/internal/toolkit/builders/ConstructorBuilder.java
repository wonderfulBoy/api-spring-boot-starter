package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.internal.toolkit.ConstructorWriter;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConstructorBuilder extends AbstractMemberBuilder {

    public static final String NAME = "ConstructorDetails";
    private final ClassDoc classDoc;
    private final VisibleMemberMap visibleMemberMap;
    private final ConstructorWriter writer;
    private final List<ProgramElementDoc> constructors;
    private int currentConstructorIndex;

    private ConstructorBuilder(Context context,
                               ClassDoc classDoc,
                               ConstructorWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                        classDoc,
                        VisibleMemberMap.CONSTRUCTORS,
                        configuration);
        constructors =
                new ArrayList<ProgramElementDoc>(visibleMemberMap.getMembersFor(classDoc));
        for (int i = 0; i < constructors.size(); i++) {
            if (constructors.get(i).isProtected()
                    || constructors.get(i).isPrivate()) {
                writer.setFoundNonPubConstructor(true);
            }
        }
        if (configuration.getMemberComparator() != null) {
            Collections.sort(constructors, configuration.getMemberComparator());
        }
    }

    public static ConstructorBuilder getInstance(Context context,
                                                 ClassDoc classDoc, ConstructorWriter writer) {
        return new ConstructorBuilder(context, classDoc, writer);
    }

    public String getName() {
        return NAME;
    }

    public boolean hasMembersToDocument() {
        return constructors.size() > 0;
    }

    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    public ConstructorWriter getWriter() {
        return writer;
    }

    public void buildConstructorDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = constructors.size();
        if (size > 0) {
            Content constructorDetailsTree = writer.getConstructorDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentConstructorIndex = 0; currentConstructorIndex < size;
                 currentConstructorIndex++) {
                Content constructorDocTree = writer.getConstructorDocTreeHeader(
                        (ConstructorDoc) constructors.get(currentConstructorIndex),
                        constructorDetailsTree);
                buildChildren(node, constructorDocTree);
                constructorDetailsTree.addContent(writer.getConstructorDoc(
                        constructorDocTree, (currentConstructorIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getConstructorDetails(constructorDetailsTree));
        }
    }

    public void buildSignature(XMLNode node, Content constructorDocTree) {
        constructorDocTree.addContent(
                writer.getSignature(
                        (ConstructorDoc) constructors.get(currentConstructorIndex)));
    }

    public void buildDeprecationInfo(XMLNode node, Content constructorDocTree) {
        writer.addDeprecated(
                (ConstructorDoc) constructors.get(currentConstructorIndex), constructorDocTree);
    }

    public void buildConstructorComments(XMLNode node, Content constructorDocTree) {
        if (!configuration.nocomment) {
            writer.addComments(
                    (ConstructorDoc) constructors.get(currentConstructorIndex),
                    constructorDocTree);
        }
    }

    public void buildTagInfo(XMLNode node, Content constructorDocTree) {
        writer.addTags((ConstructorDoc) constructors.get(currentConstructorIndex),
                constructorDocTree);
    }
}
