package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.tools.doclets.formats.html.markup.HtmlConstants;
import com.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.sun.tools.doclets.formats.html.markup.HtmlTag;
import com.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;
import java.util.List;

public class AllClassesFrameWriter extends HtmlDocletWriter {

    final HtmlTree BR = new HtmlTree(HtmlTag.BR);
    protected IndexBuilder indexbuilder;

    public AllClassesFrameWriter(ConfigurationImpl configuration,
                                 DocPath filename, IndexBuilder indexbuilder)
            throws IOException {
        super(configuration, filename);
        this.indexbuilder = indexbuilder;
    }

    public static void generate(ConfigurationImpl configuration,
                                IndexBuilder indexbuilder) {
        AllClassesFrameWriter allclassgen;
        DocPath filename = DocPaths.ALLCLASSES_FRAME;
        try {
            allclassgen = new AllClassesFrameWriter(configuration,
                    filename, indexbuilder);
            allclassgen.buildAllClassesFile(true);
            allclassgen.close();
            filename = DocPaths.ALLCLASSES_NOFRAME;
            allclassgen = new AllClassesFrameWriter(configuration,
                    filename, indexbuilder);
            allclassgen.buildAllClassesFile(false);
            allclassgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.
                    error("doclet.exception_encountered",
                            exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void buildAllClassesFile(boolean wantFrames) throws IOException {
        String label = configuration.getText("doclet.All_Classes");
        Content body = getBody(false, getWindowTitle(label));
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING,
                HtmlStyle.bar, allclassesLabel);
        body.addContent(heading);
        Content ul = new HtmlTree(HtmlTag.UL);

        addAllClasses(ul, wantFrames);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, ul);
        body.addContent(div);
        printHtmlDocument(null, false, body);
    }

    protected void addAllClasses(Content content, boolean wantFrames) {
        for (int i = 0; i < indexbuilder.elements().length; i++) {
            Character unicode = (Character) ((indexbuilder.elements())[i]);
            addContents(indexbuilder.getMemberList(unicode), wantFrames, content);
        }
    }

    protected void addContents(List<Doc> classlist, boolean wantFrames,
                               Content content) {
        for (int i = 0; i < classlist.size(); i++) {
            ClassDoc cd = (ClassDoc) classlist.get(i);
            if (!Util.isCoreClass(cd)) {
                continue;
            }
            Content label = italicsClassName(cd, false);
            Content linkContent;
            if (wantFrames) {
                linkContent = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.ALL_CLASSES_FRAME, cd).label(label).target("classFrame"));
            } else {
                linkContent = getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, cd).label(label));
            }
            Content li = HtmlTree.LI(linkContent);
            content.addContent(li);
        }
    }
}
