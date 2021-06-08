package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.AnnotationDesc;
import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.javadoc.Type;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.ClassWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.builders.MemberSummaryBuilder;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.ParamTaglet;
import com.github.api.sun.tools.doclets.internal.toolkit.util.*;
import com.github.api.sun.tools.javac.jvm.Profile;
import com.github.api.sun.tools.javadoc.RootDocImpl;

import java.io.IOException;
import java.util.List;

public class ClassWriterImpl extends SubWriterHolderWriter
        implements ClassWriter {
    protected final ClassDoc classDoc;
    protected final ClassTree classtree;
    protected final ClassDoc prev;
    protected final ClassDoc next;

    public ClassWriterImpl(ConfigurationImpl configuration, ClassDoc classDoc,
                           ClassDoc prevClass, ClassDoc nextClass, ClassTree classTree)
            throws IOException {
        super(configuration, DocPath.forClass(classDoc));
        this.classDoc = classDoc;
        configuration.currentcd = classDoc;
        this.classtree = classTree;
        this.prev = prevClass;
        this.next = nextClass;
    }

    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, classLabel);
        return li;
    }

    protected Content getNavLinkClassUse() {
        Content linkContent = getHyperLink(DocPaths.CLASS_USE.resolve(filename), useLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    public Content getNavLinkPrevious() {
        Content li;
        if (prev != null) {
            Content prevLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, prev)
                    .label(prevclassLabel).strong(true));
            li = HtmlTree.LI(prevLink);
        } else
            li = HtmlTree.LI(prevclassLabel);
        return li;
    }

    public Content getNavLinkNext() {
        Content li;
        if (next != null) {
            Content nextLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, next)
                    .label(nextclassLabel).strong(true));
            li = HtmlTree.LI(nextLink);
        } else
            li = HtmlTree.LI(nextclassLabel);
        return li;
    }

    public Content getHeader(String header) {
        String pkgname = (classDoc.containingPackage() != null) ?
                classDoc.containingPackage().name() : "";
        String clname = classDoc.name();
        Content bodyTree = getBody(true, getWindowTitle(clname));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        bodyTree.addContent(HtmlConstants.START_OF_CLASS_DATA);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        if (configuration.showProfiles) {
            String sep = "";
            int profile = configuration.profiles.getProfile(getTypeNameForProfile(classDoc));
            if (profile > 0) {
                Content profNameContent = new StringContent();
                for (int i = profile; i < configuration.profiles.getProfileCount(); i++) {
                    profNameContent.addContent(sep);
                    profNameContent.addContent(Profile.lookup(i).name);
                    sep = ", ";
                }
                Content profileNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, profNameContent);
                div.addContent(profileNameDiv);
            }
        }
        if (pkgname.length() > 0) {
            Content pkgNameContent = new StringContent(pkgname);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, pkgNameContent);
            div.addContent(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, classDoc);

        linkInfo.linkToSelf = false;
        Content headerContent = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.CLASS_PAGE_HEADING, true,
                HtmlStyle.title, headerContent);
        heading.addContent(getTypeParameterLinks(linkInfo));
        div.addContent(heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    public Content getClassContentHeader() {
        return getContentHeader();
    }

    public void addFooter(Content contentTree) {
        contentTree.addContent(HtmlConstants.END_OF_CLASS_DATA);
        addNavLinks(false, contentTree);
        addBottom(contentTree);
    }

    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(classDoc),
                true, contentTree);
    }

    public Content getClassInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    public Content getClassInfo(Content classInfoTree) {
        return getMemberTree(HtmlStyle.description, classInfoTree);
    }

    public void addClassSignature(String modifiers, Content classInfoTree) {
        boolean isInterface = classDoc.isInterface();
        classInfoTree.addContent(new HtmlTree(HtmlTag.BR));
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(classDoc, pre);
        pre.addContent(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, classDoc);

        linkInfo.linkToSelf = false;
        Content className = new StringContent(classDoc.name());
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (configuration.linksource) {
            addSrcLink(classDoc, className, pre);
            pre.addContent(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.typeNameLabel, className);
            span.addContent(parameterLinks);
            pre.addContent(span);
        }
        if (!isInterface) {
            Type superclass = Util.getFirstVisibleSuperClass(classDoc,
                    configuration);
            if (superclass != null) {
                pre.addContent(DocletConstants.NL);
                pre.addContent("extends ");
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                        superclass));
                pre.addContent(link);
            }
        }
        Type[] implIntfacs = classDoc.interfaceTypes();
        if (implIntfacs != null && implIntfacs.length > 0) {
            int counter = 0;
            for (int i = 0; i < implIntfacs.length; i++) {
                ClassDoc classDoc = implIntfacs[i].asClassDoc();
                if (!(classDoc.isPublic() ||
                        Util.isLinkable(classDoc, configuration))) {
                    continue;
                }
                if (counter == 0) {
                    pre.addContent(DocletConstants.NL);
                    pre.addContent(isInterface ? "extends " : "implements ");
                } else {
                    pre.addContent(", ");
                }
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.CLASS_SIGNATURE_PARENT_NAME,
                        implIntfacs[i]));
                pre.addContent(link);
                counter++;
            }
        }
        classInfoTree.addContent(pre);
    }

    public void addClassDescription(Content classInfoTree) {
        if (!configuration.nocomment) {

            if (classDoc.inlineTags().length > 0) {
                addInlineComment(classDoc, classInfoTree);
            }
        }
    }

    public void addClassTagInfo(Content classInfoTree) {
        if (!configuration.nocomment) {

            addTagsInfo(classDoc, classInfoTree);
        }
    }

    private Content getClassInheritenceTree(Type type) {
        Type sup;
        HtmlTree classTreeUl = new HtmlTree(HtmlTag.UL);
        classTreeUl.addStyle(HtmlStyle.inheritance);
        Content liTree = null;
        do {
            sup = Util.getFirstVisibleSuperClass(
                    type instanceof ClassDoc ? (ClassDoc) type : type.asClassDoc(),
                    configuration);
            if (sup != null) {
                HtmlTree ul = new HtmlTree(HtmlTag.UL);
                ul.addStyle(HtmlStyle.inheritance);
                ul.addContent(getTreeForClassHelper(type));
                if (liTree != null)
                    ul.addContent(liTree);
                Content li = HtmlTree.LI(ul);
                liTree = li;
                type = sup;
            } else
                classTreeUl.addContent(getTreeForClassHelper(type));
        }
        while (sup != null);
        if (liTree != null)
            classTreeUl.addContent(liTree);
        return classTreeUl;
    }

    private Content getTreeForClassHelper(Type type) {
        Content li = new HtmlTree(HtmlTag.LI);
        if (type.equals(classDoc)) {
            Content typeParameters = getTypeParameterLinks(
                    new LinkInfoImpl(configuration, LinkInfoImpl.Kind.TREE,
                            classDoc));
            if (configuration.shouldExcludeQualifier(
                    classDoc.containingPackage().name())) {
                li.addContent(type.asClassDoc().name());
                li.addContent(typeParameters);
            } else {
                li.addContent(type.asClassDoc().qualifiedName());
                li.addContent(typeParameters);
            }
        } else {
            Content link = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS_TREE_PARENT, type)
                    .label(configuration.getClassName(type.asClassDoc())));
            li.addContent(link);
        }
        return li;
    }

    public void addClassTree(Content classContentTree) {
        if (!classDoc.isClass()) {
            return;
        }
        classContentTree.addContent(getClassInheritenceTree(classDoc));
    }

    public void addTypeParamInfo(Content classInfoTree) {
        if (classDoc.typeParamTags().length > 0) {
            Content typeParam = (new ParamTaglet()).getTagletOutput(classDoc,
                    getTagletWriterInstance(false));
            Content dl = HtmlTree.DL(typeParam);
            classInfoTree.addContent(dl);
        }
    }

    public void addSubClassInfo(Content classInfoTree) {
        if (classDoc.isClass()) {
            if (classDoc.qualifiedName().equals("java.lang.Object") ||
                    classDoc.qualifiedName().equals("org.omg.CORBA.Object")) {
                return;
            }
            List<ClassDoc> subclasses = classtree.subs(classDoc, false);
            if (subclasses.size() > 0) {
                Content label = getResource(
                        "doclet.Subclasses");
                Content dt = HtmlTree.DT(label);
                Content dl = HtmlTree.DL(dt);
                dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUBCLASSES,
                        subclasses));
                classInfoTree.addContent(dl);
            }
        }
    }

    public void addSubInterfacesInfo(Content classInfoTree) {
        if (classDoc.isInterface()) {
            List<ClassDoc> subInterfaces = classtree.allSubs(classDoc, false);
            if (subInterfaces.size() > 0) {
                Content label = getResource(
                        "doclet.Subinterfaces");
                Content dt = HtmlTree.DT(label);
                Content dl = HtmlTree.DL(dt);
                dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUBINTERFACES,
                        subInterfaces));
                classInfoTree.addContent(dl);
            }
        }
    }

    public void addInterfaceUsageInfo(Content classInfoTree) {
        if (!classDoc.isInterface()) {
            return;
        }
        if (classDoc.qualifiedName().equals("java.lang.Cloneable") ||
                classDoc.qualifiedName().equals("java.io.Serializable")) {
            return;
        }
        List<ClassDoc> implcl = classtree.implementingclasses(classDoc);
        if (implcl.size() > 0) {
            Content label = getResource(
                    "doclet.Implementing_Classes");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_CLASSES,
                    implcl));
            classInfoTree.addContent(dl);
        }
    }

    public void addImplementedInterfacesInfo(Content classInfoTree) {


        List<Type> interfaceArray = Util.getAllInterfaces(classDoc, configuration);
        if (classDoc.isClass() && interfaceArray.size() > 0) {
            Content label = getResource(
                    "doclet.All_Implemented_Interfaces");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.IMPLEMENTED_INTERFACES,
                    interfaceArray));
            classInfoTree.addContent(dl);
        }
    }

    public void addSuperInterfacesInfo(Content classInfoTree) {


        List<Type> interfaceArray = Util.getAllInterfaces(classDoc, configuration);
        if (classDoc.isInterface() && interfaceArray.size() > 0) {
            Content label = getResource(
                    "doclet.All_Superinterfaces");
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            dl.addContent(getClassLinks(LinkInfoImpl.Kind.SUPER_INTERFACES,
                    interfaceArray));
            classInfoTree.addContent(dl);
        }
    }

    public void addNestedClassInfo(Content classInfoTree) {
        ClassDoc outerClass = classDoc.containingClass();
        if (outerClass != null) {
            Content label;
            if (outerClass.isInterface()) {
                label = getResource(
                        "doclet.Enclosing_Interface");
            } else {
                label = getResource(
                        "doclet.Enclosing_Class");
            }
            Content dt = HtmlTree.DT(label);
            Content dl = HtmlTree.DL(dt);
            Content dd = new HtmlTree(HtmlTag.DD);
            dd.addContent(getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, outerClass)));
            dl.addContent(dd);
            classInfoTree.addContent(dl);
        }
    }

    public void addFunctionalInterfaceInfo(Content classInfoTree) {
        if (isFunctionalInterface()) {
            Content dt = HtmlTree.DT(getResource("doclet.Functional_Interface"));
            Content dl = HtmlTree.DL(dt);
            Content dd = new HtmlTree(HtmlTag.DD);
            dd.addContent(getResource("doclet.Functional_Interface_Message"));
            dl.addContent(dd);
            classInfoTree.addContent(dl);
        }
    }

    public boolean isFunctionalInterface() {
        if (configuration.root instanceof RootDocImpl) {
            RootDocImpl root = (RootDocImpl) configuration.root;
            AnnotationDesc[] annotationDescList = classDoc.annotations();
            for (AnnotationDesc annoDesc : annotationDescList) {
                if (root.isFunctionalInterface(annoDesc)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void addClassDeprecationInfo(Content classInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        classInfoTree.addContent(hr);
        Tag[] deprs = classDoc.tags("deprecated");
        if (Util.isDeprecated(classDoc)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            Content div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    div.addContent(getSpace());
                    addInlineDeprecatedComment(classDoc, deprs[0], div);
                }
            }
            classInfoTree.addContent(div);
        }
    }

    private Content getClassLinks(LinkInfoImpl.Kind context, List<?> list) {
        Object[] typeList = list.toArray();
        Content dd = new HtmlTree(HtmlTag.DD);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                Content separator = new StringContent(", ");
                dd.addContent(separator);
            }
            if (typeList[i] instanceof ClassDoc) {
                Content link = getLink(
                        new LinkInfoImpl(configuration, context, (ClassDoc) (typeList[i])));
                dd.addContent(link);
            } else {
                Content link = getLink(
                        new LinkInfoImpl(configuration, context, (Type) (typeList[i])));
                dd.addContent(link);
            }
        }
        return dd;
    }

    protected Content getNavLinkTree() {
        Content treeLinkContent = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel, "", "");
        Content li = HtmlTree.LI(treeLinkContent);
        return li;
    }

    protected void addSummaryDetailLinks(Content subDiv) {
        try {
            Content div = HtmlTree.DIV(getNavSummaryLinks());
            div.addContent(getNavDetailLinks());
            subDiv.addContent(div);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DocletAbortException(e);
        }
    }

    protected Content getNavSummaryLinks() throws Exception {
        Content li = HtmlTree.LI(summaryLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        String[] navLinkLabels = new String[]{
                "doclet.navNested", "doclet.navEnum", "doclet.navField", "doclet.navConstructor",
                "doclet.navMethod"
        };
        for (int i = 0; i < navLinkLabels.length; i++) {
            Content liNav = new HtmlTree(HtmlTag.LI);
            if (i == VisibleMemberMap.ENUM_CONSTANTS && !classDoc.isEnum()) {
                continue;
            }
            if (i == VisibleMemberMap.CONSTRUCTORS && classDoc.isEnum()) {
                continue;
            }
            AbstractMemberWriter writer =
                    ((AbstractMemberWriter) memberSummaryBuilder.
                            getMemberSummaryWriter(i));
            if (writer == null) {
                liNav.addContent(getResource(navLinkLabels[i]));
            } else {
                writer.addNavSummaryLink(
                        memberSummaryBuilder.members(i),
                        memberSummaryBuilder.getVisibleMemberMap(i), liNav);
            }
            if (i < navLinkLabels.length - 1) {
                addNavGap(liNav);
            }
            ulNav.addContent(liNav);
        }
        return ulNav;
    }

    protected Content getNavDetailLinks() throws Exception {
        Content li = HtmlTree.LI(detailLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        String[] navLinkLabels = new String[]{
                "doclet.navNested", "doclet.navEnum", "doclet.navField", "doclet.navConstructor",
                "doclet.navMethod"
        };
        for (int i = 1; i < navLinkLabels.length; i++) {
            Content liNav = new HtmlTree(HtmlTag.LI);
            AbstractMemberWriter writer =
                    ((AbstractMemberWriter) memberSummaryBuilder.
                            getMemberSummaryWriter(i));
            if (i == VisibleMemberMap.ENUM_CONSTANTS && !classDoc.isEnum()) {
                continue;
            }
            if (i == VisibleMemberMap.CONSTRUCTORS && classDoc.isEnum()) {
                continue;
            }
            if (writer == null) {
                liNav.addContent(getResource(navLinkLabels[i]));
            } else {
                writer.addNavDetailLink(memberSummaryBuilder.members(i), liNav);
            }
            if (i < navLinkLabels.length - 1) {
                addNavGap(liNav);
            }
            ulNav.addContent(liNav);
        }
        return ulNav;
    }

    protected void addNavGap(Content liNav) {
        liNav.addContent(getSpace());
        liNav.addContent("|");
        liNav.addContent(getSpace());
    }

    public ClassDoc getClassDoc() {
        return classDoc;
    }
}
