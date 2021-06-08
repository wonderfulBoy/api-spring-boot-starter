package com.github.api.sun.tools.doclets.formats.html.markup;

import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocFile;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletConstants;
import com.github.api.sun.tools.doclets.internal.toolkit.util.MethodTypes;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;

public class HtmlWriter {

    public final Content overviewLabel;
    public final Content defaultPackageLabel;
    public final Content packageLabel;
    public final Content profileLabel;
    public final Content useLabel;
    public final Content prevLabel;
    public final Content nextLabel;
    public final Content prevclassLabel;
    public final Content nextclassLabel;
    public final Content summaryLabel;
    public final Content detailLabel;
    public final Content framesLabel;
    public final Content noframesLabel;
    public final Content treeLabel;
    public final Content classLabel;
    public final Content deprecatedLabel;
    public final Content deprecatedPhrase;
    public final Content allclassesLabel;
    public final Content allpackagesLabel;
    public final Content allprofilesLabel;
    public final Content indexLabel;
    public final Content helpLabel;
    public final Content seeLabel;
    public final Content descriptionLabel;
    public final Content prevpackageLabel;
    public final Content nextpackageLabel;
    public final Content prevprofileLabel;
    public final Content nextprofileLabel;
    public final Content packagesLabel;
    public final Content profilesLabel;
    public final Content methodDetailsLabel;
    public final Content annotationTypeDetailsLabel;
    public final Content fieldDetailsLabel;
    public final Content propertyDetailsLabel;
    public final Content constructorDetailsLabel;
    public final Content enumConstantsDetailsLabel;
    public final Content specifiedByLabel;
    public final Content overridesLabel;
    public final Content descfrmClassLabel;
    public final Content descfrmInterfaceLabel;
    protected final String[] profileTableHeader;
    protected final String[] packageTableHeader;
    protected final String useTableSummary;
    protected final String modifierTypeHeader;
    private final Writer writer;
    protected String winTitle;
    protected Configuration configuration;
    protected boolean memberDetailsListPrinted;
    private Content script;

    public HtmlWriter(Configuration configuration, DocPath path)
            throws IOException {
        writer = DocFile.createFileForOutput(configuration, path).openWriter();
        this.configuration = configuration;
        this.memberDetailsListPrinted = false;
        profileTableHeader = new String[]{
                configuration.getText("doclet.Profile"),
                configuration.getText("doclet.Description")
        };
        packageTableHeader = new String[]{
                configuration.getText("doclet.Package"),
                configuration.getText("doclet.Description")
        };
        useTableSummary = configuration.getText("doclet.Use_Table_Summary",
                configuration.getText("doclet.packages"));
        modifierTypeHeader = configuration.getText("doclet.0_and_1",
                configuration.getText("doclet.Modifier"),
                configuration.getText("doclet.Type"));
        overviewLabel = getResource("doclet.Overview");
        defaultPackageLabel = new StringContent(DocletConstants.DEFAULT_PACKAGE_NAME);
        packageLabel = getResource("doclet.Package");
        profileLabel = getResource("doclet.Profile");
        useLabel = getResource("doclet.navClassUse");
        prevLabel = getResource("doclet.Prev");
        nextLabel = getResource("doclet.Next");
        prevclassLabel = getNonBreakResource("doclet.Prev_Class");
        nextclassLabel = getNonBreakResource("doclet.Next_Class");
        summaryLabel = getResource("doclet.Summary");
        detailLabel = getResource("doclet.Detail");
        framesLabel = getResource("doclet.Frames");
        noframesLabel = getNonBreakResource("doclet.No_Frames");
        treeLabel = getResource("doclet.Tree");
        classLabel = getResource("doclet.Class");
        deprecatedLabel = getResource("doclet.navDeprecated");
        deprecatedPhrase = getResource("doclet.Deprecated");
        allclassesLabel = getNonBreakResource("doclet.All_Classes");
        allpackagesLabel = getNonBreakResource("doclet.All_Packages");
        allprofilesLabel = getNonBreakResource("doclet.All_Profiles");
        indexLabel = getResource("doclet.Index");
        helpLabel = getResource("doclet.Help");
        seeLabel = getResource("doclet.See");
        descriptionLabel = getResource("doclet.Description");
        prevpackageLabel = getNonBreakResource("doclet.Prev_Package");
        nextpackageLabel = getNonBreakResource("doclet.Next_Package");
        prevprofileLabel = getNonBreakResource("doclet.Prev_Profile");
        nextprofileLabel = getNonBreakResource("doclet.Next_Profile");
        packagesLabel = getResource("doclet.Packages");
        profilesLabel = getResource("doclet.Profiles");
        methodDetailsLabel = getResource("doclet.Method_Detail");
        annotationTypeDetailsLabel = getResource("doclet.Annotation_Type_Member_Detail");
        fieldDetailsLabel = getResource("doclet.Field_Detail");
        propertyDetailsLabel = getResource("doclet.Property_Detail");
        constructorDetailsLabel = getResource("doclet.Constructor_Detail");
        enumConstantsDetailsLabel = getResource("doclet.Enum_Constant_Detail");
        specifiedByLabel = getResource("doclet.Specified_By");
        overridesLabel = getResource("doclet.Overrides");
        descfrmClassLabel = getResource("doclet.Description_From_Class");
        descfrmInterfaceLabel = getResource("doclet.Description_From_Interface");
    }

    private static String escapeJavaScriptChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\'':
                    sb.append("\\'");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (ch < 32 || ch >= 127) {
                        sb.append(String.format("\\u%04X", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    public void write(Content c) throws IOException {
        c.write(writer, true);
    }

    public void close() throws IOException {
        writer.close();
    }

    public Content getResource(String key) {
        return configuration.getResource(key);
    }

    public Content getNonBreakResource(String key) {
        String text = configuration.getText(key);
        Content c = configuration.newContent();
        int start = 0;
        int p;
        while ((p = text.indexOf(" ", start)) != -1) {
            c.addContent(text.substring(start, p));
            c.addContent(RawHtml.nbsp);
            start = p + 1;
        }
        c.addContent(text.substring(start));
        return c;
    }

    public Content getResource(String key, Object o) {
        return configuration.getResource(key, o);
    }

    public Content getResource(String key, Object o0, Object o1) {
        return configuration.getResource(key, o0, o1);
    }

    protected HtmlTree getWinTitleScript() {
        HtmlTree script = new HtmlTree(HtmlTag.SCRIPT);
        if (winTitle != null && winTitle.length() > 0) {
            script.addAttr(HtmlAttr.TYPE, "text/javascript");
            String scriptCode = "<!--" + DocletConstants.NL +
                    "    try {" + DocletConstants.NL +
                    "        if (location.href.indexOf('is-external=true') == -1) {" + DocletConstants.NL +
                    "            parent.document.title=\"" + escapeJavaScriptChars(winTitle) + "\";" + DocletConstants.NL +
                    "        }" + DocletConstants.NL +
                    "    }" + DocletConstants.NL +
                    "    catch(err) {" + DocletConstants.NL +
                    "    }" + DocletConstants.NL +
                    "//-->" + DocletConstants.NL;
            RawHtml scriptContent = new RawHtml(scriptCode);
            script.addContent(scriptContent);
        }
        return script;
    }

    protected Content getFramesetJavaScript() {
        HtmlTree script = new HtmlTree(HtmlTag.SCRIPT);
        script.addAttr(HtmlAttr.TYPE, "text/javascript");
        String scriptCode = DocletConstants.NL +
                "    targetPage = \"\" + window.location.search;" + DocletConstants.NL +
                "    if (targetPage != \"\" && targetPage != \"undefined\")" + DocletConstants.NL +
                "        targetPage = targetPage.substring(1);" + DocletConstants.NL +
                "    if (targetPage.indexOf(\":\") != -1 || (targetPage != \"\" && !validURL(targetPage)))" + DocletConstants.NL +
                "        targetPage = \"undefined\";" + DocletConstants.NL +
                "    function validURL(url) {" + DocletConstants.NL +
                "        try {" + DocletConstants.NL +
                "            url = decodeURIComponent(url);" + DocletConstants.NL +
                "        }" + DocletConstants.NL +
                "        catch (error) {" + DocletConstants.NL +
                "            return false;" + DocletConstants.NL +
                "        }" + DocletConstants.NL +
                "        var pos = url.indexOf(\".html\");" + DocletConstants.NL +
                "        if (pos == -1 || pos != url.length - 5)" + DocletConstants.NL +
                "            return false;" + DocletConstants.NL +
                "        var allowNumber = false;" + DocletConstants.NL +
                "        var allowSep = false;" + DocletConstants.NL +
                "        var seenDot = false;" + DocletConstants.NL +
                "        for (var i = 0; i < url.length - 5; i++) {" + DocletConstants.NL +
                "            var ch = url.charAt(i);" + DocletConstants.NL +
                "            if ('a' <= ch && ch <= 'z' ||" + DocletConstants.NL +
                "                    'A' <= ch && ch <= 'Z' ||" + DocletConstants.NL +
                "                    ch == '$' ||" + DocletConstants.NL +
                "                    ch == '_' ||" + DocletConstants.NL +
                "                    ch.charCodeAt(0) > 127) {" + DocletConstants.NL +
                "                allowNumber = true;" + DocletConstants.NL +
                "                allowSep = true;" + DocletConstants.NL +
                "            } else if ('0' <= ch && ch <= '9'" + DocletConstants.NL +
                "                    || ch == '-') {" + DocletConstants.NL +
                "                if (!allowNumber)" + DocletConstants.NL +
                "                     return false;" + DocletConstants.NL +
                "            } else if (ch == '/' || ch == '.') {" + DocletConstants.NL +
                "                if (!allowSep)" + DocletConstants.NL +
                "                    return false;" + DocletConstants.NL +
                "                allowNumber = false;" + DocletConstants.NL +
                "                allowSep = false;" + DocletConstants.NL +
                "                if (ch == '.')" + DocletConstants.NL +
                "                     seenDot = true;" + DocletConstants.NL +
                "                if (ch == '/' && seenDot)" + DocletConstants.NL +
                "                     return false;" + DocletConstants.NL +
                "            } else {" + DocletConstants.NL +
                "                return false;" + DocletConstants.NL +
                "            }" + DocletConstants.NL +
                "        }" + DocletConstants.NL +
                "        return true;" + DocletConstants.NL +
                "    }" + DocletConstants.NL +
                "    function loadFrames() {" + DocletConstants.NL +
                "        if (targetPage != \"\" && targetPage != \"undefined\")" + DocletConstants.NL +
                "             top.classFrame.location = top.targetPage;" + DocletConstants.NL +
                "    }" + DocletConstants.NL;
        RawHtml scriptContent = new RawHtml(scriptCode);
        script.addContent(scriptContent);
        return script;
    }

    public HtmlTree getBody(boolean includeScript, String title) {
        HtmlTree body = new HtmlTree(HtmlTag.BODY);

        this.winTitle = title;


        if (includeScript) {
            this.script = getWinTitleScript();
            body.addContent(script);
            Content noScript = HtmlTree.NOSCRIPT(
                    HtmlTree.DIV(getResource("doclet.No_Script_Message")));
            body.addContent(noScript);
        }
        return body;
    }

    public void generateMethodTypesScript(Map<String, Integer> typeMap,
                                          Set<MethodTypes> methodTypes) {
        String sep = "";
        StringBuilder vars = new StringBuilder("var methods = {");
        for (Map.Entry<String, Integer> entry : typeMap.entrySet()) {
            vars.append(sep);
            sep = ",";
            vars.append("\"");
            vars.append(entry.getKey());
            vars.append("\":");
            vars.append(entry.getValue());
        }
        vars.append("};").append(DocletConstants.NL);
        sep = "";
        vars.append("var tabs = {");
        for (MethodTypes entry : methodTypes) {
            vars.append(sep);
            sep = ",";
            vars.append(entry.value()).append(":");
            vars.append("[").append("\"").append(entry.tabId());
            vars.append("\"").append(sep).append("\"").append(entry.text()).append("\"]");
        }
        vars.append("};").append(DocletConstants.NL);
        addStyles(HtmlStyle.altColor, vars);
        addStyles(HtmlStyle.rowColor, vars);
        addStyles(HtmlStyle.tableTab, vars);
        addStyles(HtmlStyle.activeTableTab, vars);
        script.addContent(new RawHtml(vars.toString()));
    }

    public void addStyles(HtmlStyle style, StringBuilder vars) {
        vars.append("var ").append(style).append(" = \"").append(style)
                .append("\";").append(DocletConstants.NL);
    }

    public HtmlTree getTitle() {
        HtmlTree title = HtmlTree.TITLE(new StringContent(winTitle));
        return title;
    }

    public String codeText(String text) {
        return "<code>" + text + "</code>";
    }

    public Content getSpace() {
        return RawHtml.nbsp;
    }

    public String getModifierTypeHeader() {
        return modifierTypeHeader;
    }
}
