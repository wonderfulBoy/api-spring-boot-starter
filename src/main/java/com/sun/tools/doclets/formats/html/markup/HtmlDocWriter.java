package com.sun.tools.doclets.formats.html.markup;

import com.sun.javadoc.ClassDoc;
import com.sun.tools.doclets.formats.html.SectionName;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocFile;
import com.sun.tools.doclets.internal.toolkit.util.DocLink;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public abstract class HtmlDocWriter extends HtmlWriter {
    public static final String CONTENT_TYPE = "text/html";

    public HtmlDocWriter(Configuration configuration, DocPath filename)
            throws IOException {
        super(configuration, filename);
        configuration.message.notice("doclet.Generating_0",
                DocFile.createFileForOutput(configuration, filename).getPath());
    }

    public abstract Configuration configuration();

    public Content getHyperLink(DocPath link, String label) {
        return getHyperLink(link, new StringContent(label), false, "", "", "");
    }

    public Content getHyperLink(String where,
                                Content label) {
        return getHyperLink(getDocLink(where), label, "", "");
    }

    public Content getHyperLink(SectionName sectionName,
                                Content label) {
        return getHyperLink(getDocLink(sectionName), label, "", "");
    }

    public Content getHyperLink(SectionName sectionName, String where,
                                Content label) {
        return getHyperLink(getDocLink(sectionName, where), label, "", "");
    }

    public DocLink getDocLink(String where) {
        return DocLink.fragment(getName(where));
    }

    public DocLink getDocLink(SectionName sectionName) {
        return DocLink.fragment(sectionName.getName());
    }

    public DocLink getDocLink(SectionName sectionName, String where) {
        return DocLink.fragment(sectionName.getName() + getName(where));
    }

    public String getName(String name) {
        StringBuilder sb = new StringBuilder();
        char ch;

        for (int i = 0; i < name.length(); i++) {
            ch = name.charAt(i);
            switch (ch) {
                case '(':
                case ')':
                case '<':
                case '>':
                case ',':
                    sb.append('-');
                    break;
                case ' ':
                case '[':
                    break;
                case ']':
                    sb.append(":A");
                    break;
                case '$':
                    if (i == 0)
                        sb.append("Z:Z");
                    sb.append(":D");
                    break;
                case '_':
                    if (i == 0)
                        sb.append("Z:Z");
                    sb.append(ch);
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    public Content getHyperLink(DocPath link, Content label) {
        return getHyperLink(link, label, "", "");
    }

    public Content getHyperLink(DocLink link, Content label) {
        return getHyperLink(link, label, "", "");
    }

    public Content getHyperLink(DocPath link,
                                Content label, boolean strong,
                                String stylename, String title, String target) {
        return getHyperLink(new DocLink(link), label, strong,
                stylename, title, target);
    }

    public Content getHyperLink(DocLink link,
                                Content label, boolean strong,
                                String stylename, String title, String target) {
        Content body = label;
        if (strong) {
            body = HtmlTree.SPAN(HtmlStyle.typeNameLink, body);
        }
        if (stylename != null && stylename.length() != 0) {
            HtmlTree t = new HtmlTree(HtmlTag.FONT, body);
            t.addAttr(HtmlAttr.CLASS, stylename);
            body = t;
        }
        HtmlTree l = HtmlTree.A(link.toString(), body);
        if (title != null && title.length() != 0) {
            l.addAttr(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            l.addAttr(HtmlAttr.TARGET, target);
        }
        return l;
    }

    public Content getHyperLink(DocPath link,
                                Content label, String title, String target) {
        return getHyperLink(new DocLink(link), label, title, target);
    }

    public Content getHyperLink(DocLink link,
                                Content label, String title, String target) {
        HtmlTree anchor = HtmlTree.A(link.toString(), label);
        if (title != null && title.length() != 0) {
            anchor.addAttr(HtmlAttr.TITLE, title);
        }
        if (target != null && target.length() != 0) {
            anchor.addAttr(HtmlAttr.TARGET, target);
        }
        return anchor;
    }

    public String getPkgName(ClassDoc cd) {
        String pkgName = cd.containingPackage().name();
        if (pkgName.length() > 0) {
            pkgName += ".";
            return pkgName;
        }
        return "";
    }

    public boolean getMemberDetailsListPrinted() {
        return memberDetailsListPrinted;
    }

    public void printFramesetDocument(String title, boolean noTimeStamp,
                                      Content frameset) throws IOException {
        Content htmlDocType = DocType.FRAMESET;
        Content htmlComment = new Comment(configuration.getText("doclet.New_Page"));
        Content head = new HtmlTree(HtmlTag.HEAD);
        head.addContent(getGeneratedBy(!noTimeStamp));
        if (configuration.charset.length() > 0) {
            Content meta = HtmlTree.META("Content-Type", CONTENT_TYPE,
                    configuration.charset);
            head.addContent(meta);
        }
        Content windowTitle = HtmlTree.TITLE(new StringContent(title));
        head.addContent(windowTitle);
        head.addContent(getFramesetJavaScript());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, frameset);
        Content htmlDocument = new HtmlDocument(htmlDocType,
                htmlComment, htmlTree);
        write(htmlDocument);
    }

    protected Comment getGeneratedBy(boolean timestamp) {
        String text = "Generated by javadoc";
        if (timestamp) {
            Calendar calendar = new GregorianCalendar(TimeZone.getDefault());
            Date today = calendar.getTime();
            text += " (" + configuration.getDocletSpecificBuildDate() + ") on " + today;
        }
        return new Comment(text);
    }
}
