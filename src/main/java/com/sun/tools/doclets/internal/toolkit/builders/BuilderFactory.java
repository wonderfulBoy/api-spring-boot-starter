package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.ClassTree;
import com.sun.tools.javac.jvm.Profile;

import java.util.HashSet;
import java.util.Set;

public class BuilderFactory {

    private final Configuration configuration;

    private final WriterFactory writerFactory;
    private final AbstractBuilder.Context context;

    public BuilderFactory(Configuration configuration) {
        this.configuration = configuration;
        this.writerFactory = configuration.getWriterFactory();
        Set<String> containingPackagesSeen = new HashSet<String>();
        context = new AbstractBuilder.Context(configuration, containingPackagesSeen,
                LayoutParser.getInstance(configuration));
    }

    public AbstractBuilder getConstantsSummaryBuider() throws Exception {
        return ConstantsSummaryBuilder.getInstance(context,
                writerFactory.getConstantsSummaryWriter());
    }

    public AbstractBuilder getPackageSummaryBuilder(PackageDoc pkg, PackageDoc prevPkg,
                                                    PackageDoc nextPkg) throws Exception {
        return PackageSummaryBuilder.getInstance(context, pkg,
                writerFactory.getPackageSummaryWriter(pkg, prevPkg, nextPkg));
    }

    public AbstractBuilder getProfileSummaryBuilder(Profile profile, Profile prevProfile,
                                                    Profile nextProfile) throws Exception {
        return ProfileSummaryBuilder.getInstance(context, profile,
                writerFactory.getProfileSummaryWriter(profile, prevProfile, nextProfile));
    }

    public AbstractBuilder getProfilePackageSummaryBuilder(PackageDoc pkg, PackageDoc prevPkg,
                                                           PackageDoc nextPkg, Profile profile) throws Exception {
        return ProfilePackageSummaryBuilder.getInstance(context, pkg,
                writerFactory.getProfilePackageSummaryWriter(pkg, prevPkg, nextPkg,
                        profile), profile);
    }

    public AbstractBuilder getClassBuilder(ClassDoc classDoc,
                                           ClassDoc prevClass, ClassDoc nextClass, ClassTree classTree)
            throws Exception {
        return ClassBuilder.getInstance(context, classDoc,
                writerFactory.getClassWriter(classDoc, prevClass, nextClass,
                        classTree));
    }

    public AbstractBuilder getAnnotationTypeBuilder(
            AnnotationTypeDoc annotationType,
            Type prevType, Type nextType)
            throws Exception {
        return AnnotationTypeBuilder.getInstance(context, annotationType,
                writerFactory.getAnnotationTypeWriter(annotationType, prevType, nextType));
    }

    public AbstractBuilder getMethodBuilder(ClassWriter classWriter)
            throws Exception {
        return MethodBuilder.getInstance(context,
                classWriter.getClassDoc(),
                writerFactory.getMethodWriter(classWriter));
    }

    public AbstractBuilder getAnnotationTypeFieldsBuilder(
            AnnotationTypeWriter annotationTypeWriter)
            throws Exception {
        return AnnotationTypeFieldBuilder.getInstance(context,
                annotationTypeWriter.getAnnotationTypeDoc(),
                writerFactory.getAnnotationTypeFieldWriter(
                        annotationTypeWriter));
    }

    public AbstractBuilder getAnnotationTypeOptionalMemberBuilder(
            AnnotationTypeWriter annotationTypeWriter)
            throws Exception {
        return AnnotationTypeOptionalMemberBuilder.getInstance(context,
                annotationTypeWriter.getAnnotationTypeDoc(),
                writerFactory.getAnnotationTypeOptionalMemberWriter(
                        annotationTypeWriter));
    }

    public AbstractBuilder getAnnotationTypeRequiredMemberBuilder(
            AnnotationTypeWriter annotationTypeWriter)
            throws Exception {
        return AnnotationTypeRequiredMemberBuilder.getInstance(context,
                annotationTypeWriter.getAnnotationTypeDoc(),
                writerFactory.getAnnotationTypeRequiredMemberWriter(
                        annotationTypeWriter));
    }

    public AbstractBuilder getEnumConstantsBuilder(ClassWriter classWriter)
            throws Exception {
        return EnumConstantBuilder.getInstance(context, classWriter.getClassDoc(),
                writerFactory.getEnumConstantWriter(classWriter));
    }

    public AbstractBuilder getFieldBuilder(ClassWriter classWriter)
            throws Exception {
        return FieldBuilder.getInstance(context, classWriter.getClassDoc(),
                writerFactory.getFieldWriter(classWriter));
    }

    public AbstractBuilder getPropertyBuilder(ClassWriter classWriter) throws Exception {
        final PropertyWriter propertyWriter =
                writerFactory.getPropertyWriter(classWriter);
        return PropertyBuilder.getInstance(context,
                classWriter.getClassDoc(),
                propertyWriter);
    }

    public AbstractBuilder getConstructorBuilder(ClassWriter classWriter)
            throws Exception {
        return ConstructorBuilder.getInstance(context,
                classWriter.getClassDoc(),
                writerFactory.getConstructorWriter(classWriter));
    }

    public AbstractBuilder getMemberSummaryBuilder(ClassWriter classWriter)
            throws Exception {
        return MemberSummaryBuilder.getInstance(classWriter, context);
    }

    public AbstractBuilder getMemberSummaryBuilder(
            AnnotationTypeWriter annotationTypeWriter)
            throws Exception {
        return MemberSummaryBuilder.getInstance(annotationTypeWriter, context);
    }

    public AbstractBuilder getSerializedFormBuilder()
            throws Exception {
        return SerializedFormBuilder.getInstance(context);
    }
}
