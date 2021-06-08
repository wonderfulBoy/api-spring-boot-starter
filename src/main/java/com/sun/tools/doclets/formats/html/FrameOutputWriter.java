package com.sun.tools.doclets.formats.html;

import com.sun.tools.doclets.formats.html.markup.HtmlConstants;
import com.sun.tools.doclets.formats.html.markup.HtmlTag;
import com.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;

import java.io.IOException;

public class FrameOutputWriter extends HtmlDocletWriter {

    private final String SCROLL_YES = "yes";
    int noOfPackages;

    public FrameOutputWriter(ConfigurationImpl configuration,
                             DocPath filename) throws IOException {
        super(configuration, filename);
        noOfPackages = configuration.packages.length;
    }

    public static void generate(ConfigurationImpl configuration) {
        FrameOutputWriter framegen;
        DocPath filename = DocPath.empty;
        try {
            filename = DocPaths.INDEX;
            framegen = new FrameOutputWriter(configuration, filename);
            framegen.generateFrameFile();
            framegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void generateFrameFile() throws IOException {
        Content frameset = getFrameDetails();
        if (configuration.windowtitle.length() > 0) {
            printFramesetDocument(configuration.windowtitle, configuration.notimestamp,
                    frameset);
        } else {
            printFramesetDocument(configuration.getText("doclet.Generated_Docs_Untitled"),
                    configuration.notimestamp, frameset);
        }
    }

    protected void addFrameWarning(Content contentTree) {
        Content noframes = new HtmlTree(HtmlTag.NOFRAMES);
        Content noScript = HtmlTree.NOSCRIPT(
                HtmlTree.DIV(getResource("doclet.No_Script_Message")));
        noframes.addContent(noScript);
        Content noframesHead = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                getResource("doclet.Frame_Alert"));
        noframes.addContent(noframesHead);
        Content p = HtmlTree.P(getResource("doclet.Frame_Warning_Message",
                getHyperLink(configuration.topFile,
                        configuration.getText("doclet.Non_Frame_Version"))));
        noframes.addContent(p);
        contentTree.addContent(noframes);
    }

    protected Content getFrameDetails() {
        HtmlTree frameset = HtmlTree.FRAMESET("20%,80%", null, "Documentation frame",
                "top.loadFrames()");
        if (noOfPackages <= 1) {
            addAllClassesFrameTag(frameset);
        } else if (noOfPackages > 1) {
            HtmlTree leftFrameset = HtmlTree.FRAMESET(null, "30%,70%", "Left frames",
                    "top.loadFrames()");
            addAllPackagesFrameTag(leftFrameset);
            addAllClassesFrameTag(leftFrameset);
            frameset.addContent(leftFrameset);
        }
        addClassFrameTag(frameset);
        addFrameWarning(frameset);
        return frameset;
    }

    private void addAllPackagesFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.FRAME(DocPaths.OVERVIEW_FRAME.getPath(),
                "packageListFrame", configuration.getText("doclet.All_Packages"));
        contentTree.addContent(frame);
    }

    private void addAllClassesFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.FRAME(DocPaths.ALLCLASSES_FRAME.getPath(),
                "packageFrame", configuration.getText("doclet.All_classes_and_interfaces"));
        contentTree.addContent(frame);
    }

    private void addClassFrameTag(Content contentTree) {
        HtmlTree frame = HtmlTree.FRAME(configuration.topFile.getPath(), "classFrame",
                configuration.getText("doclet.Package_class_and_interface_descriptions"),
                SCROLL_YES);
        contentTree.addContent(frame);
    }
}
