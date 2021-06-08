package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.*;

import javax.tools.FileObject;
import java.io.*;

public class SourceToHTMLConverter {

    private static final int NUM_BLANK_LINES = 60;

    private static final String NEW_LINE = DocletConstants.NL;
    private final ConfigurationImpl configuration;
    private final RootDoc rootDoc;
    private DocPath outputdir;

    private DocPath relativePath = DocPath.empty;

    private SourceToHTMLConverter(ConfigurationImpl configuration, RootDoc rd,
                                  DocPath outputdir) {
        this.configuration = configuration;
        this.rootDoc = rd;
        this.outputdir = outputdir;
    }

    public static void convertRoot(ConfigurationImpl configuration, RootDoc rd,
                                   DocPath outputdir) {
        new SourceToHTMLConverter(configuration, rd, outputdir).generate();
    }

    private static Content getHeader() {
        return new HtmlTree(HtmlTag.BODY);
    }

    private static void addLineNo(Content pre, int lineno) {
        HtmlTree span = new HtmlTree(HtmlTag.SPAN);
        span.addStyle(HtmlStyle.sourceLineNo);
        if (lineno < 10) {
            span.addContent("00" + lineno);
        } else if (lineno < 100) {
            span.addContent("0" + lineno);
        } else {
            span.addContent(Integer.toString(lineno));
        }
        pre.addContent(span);
    }

    private static void addBlankLines(Content pre) {
        for (int i = 0; i < NUM_BLANK_LINES; i++) {
            pre.addContent(NEW_LINE);
        }
    }

    public static String getAnchorName(Doc d) {
        return "line." + d.position().line();
    }

    void generate() {
        if (rootDoc == null || outputdir == null) {
            return;
        }
        PackageDoc[] pds = rootDoc.specifiedPackages();
        for (int i = 0; i < pds.length; i++) {


            if (!(configuration.nodeprecated && Util.isDeprecated(pds[i])))
                convertPackage(pds[i], outputdir);
        }
        ClassDoc[] cds = rootDoc.specifiedClasses();
        for (int i = 0; i < cds.length; i++) {


            if (!(configuration.nodeprecated &&
                    (Util.isDeprecated(cds[i]) || Util.isDeprecated(cds[i].containingPackage()))))
                convertClass(cds[i], outputdir);
        }
    }

    public void convertPackage(PackageDoc pd, DocPath outputdir) {
        if (pd == null) {
            return;
        }
        ClassDoc[] cds = pd.allClasses();
        for (int i = 0; i < cds.length; i++) {


            if (!(configuration.nodeprecated && Util.isDeprecated(cds[i])))
                convertClass(cds[i], outputdir);
        }
    }

    public void convertClass(ClassDoc cd, DocPath outputdir) {
        if (cd == null) {
            return;
        }
        try {
            SourcePosition sp = cd.position();
            if (sp == null)
                return;
            Reader r;

            if (sp instanceof com.github.api.sun.tools.javadoc.SourcePositionImpl) {
                FileObject fo = ((com.github.api.sun.tools.javadoc.SourcePositionImpl) sp).fileObject();
                if (fo == null)
                    return;
                r = fo.openReader(true);
            } else {
                File file = sp.file();
                if (file == null)
                    return;
                r = new FileReader(file);
            }
            LineNumberReader reader = new LineNumberReader(r);
            int lineno = 1;
            String line;
            relativePath = DocPaths.SOURCE_OUTPUT
                    .resolve(DocPath.forPackage(cd))
                    .invert();
            Content body = getHeader();
            Content pre = new HtmlTree(HtmlTag.PRE);
            try {
                while ((line = reader.readLine()) != null) {
                    addLineNo(pre, lineno);
                    addLine(pre, line, lineno);
                    lineno++;
                }
            } finally {
                reader.close();
            }
            addBlankLines(pre);
            Content div = HtmlTree.DIV(HtmlStyle.sourceContainer, pre);
            body.addContent(div);
            writeToFile(body, outputdir.resolve(DocPath.forClass(cd)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeToFile(Content body, DocPath path) throws IOException {
        Content htmlDocType = DocType.TRANSITIONAL;
        Content head = new HtmlTree(HtmlTag.HEAD);
        head.addContent(HtmlTree.TITLE(new StringContent(
                configuration.getText("doclet.Window_Source_title"))));
        head.addContent(getStyleSheetProperties());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, body);
        Content htmlDocument = new HtmlDocument(htmlDocType, htmlTree);
        configuration.message.notice("doclet.Generating_0", path.getPath());
        DocFile df = DocFile.createFileForOutput(configuration, path);
        Writer w = df.openWriter();
        try {
            htmlDocument.write(w, true);
        } finally {
            w.close();
        }
    }

    public HtmlTree getStyleSheetProperties() {
        String filename = configuration.stylesheetfile;
        DocPath stylesheet;
        if (filename.length() > 0) {
            DocFile file = DocFile.createFileForInput(configuration, filename);
            stylesheet = DocPath.create(file.getName());
        } else {
            stylesheet = DocPaths.STYLESHEET;
        }
        DocPath p = relativePath.resolve(stylesheet);
        HtmlTree link = HtmlTree.LINK("stylesheet", "text/css", p.getPath(), "Style");
        return link;
    }

    private void addLine(Content pre, String line, int currentLineNo) {
        if (line != null) {
            pre.addContent(Util.replaceTabs(configuration, line));
            Content anchor = HtmlTree.A_NAME("line." + currentLineNo);
            pre.addContent(anchor);
            pre.addContent(NEW_LINE);
        }
    }
}
