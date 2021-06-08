package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.internal.toolkit.*;
import com.sun.tools.doclets.internal.toolkit.util.ClassTree;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;
import com.sun.tools.javac.jvm.Profile;

import java.io.IOException;

public class WriterFactoryImpl implements WriterFactory {
    private final ConfigurationImpl configuration;

    public WriterFactoryImpl(ConfigurationImpl configuration) {
        this.configuration = configuration;
    }

    public ConstantsSummaryWriter getConstantsSummaryWriter() throws Exception {
        return new ConstantsSummaryWriterImpl(configuration);
    }

    public PackageSummaryWriter getPackageSummaryWriter(PackageDoc packageDoc,
                                                        PackageDoc prevPkg, PackageDoc nextPkg) throws Exception {
        return new PackageWriterImpl(configuration, packageDoc,
                prevPkg, nextPkg);
    }

    public ProfileSummaryWriter getProfileSummaryWriter(Profile profile,
                                                        Profile prevProfile, Profile nextProfile) throws Exception {
        return new ProfileWriterImpl(configuration, profile,
                prevProfile, nextProfile);
    }

    public ProfilePackageSummaryWriter getProfilePackageSummaryWriter(PackageDoc packageDoc,
                                                                      PackageDoc prevPkg, PackageDoc nextPkg, Profile profile) throws Exception {
        return new ProfilePackageWriterImpl(configuration, packageDoc,
                prevPkg, nextPkg, profile);
    }

    public ClassWriter getClassWriter(ClassDoc classDoc, ClassDoc prevClass,
                                      ClassDoc nextClass, ClassTree classTree) throws IOException {
        return new ClassWriterImpl(configuration, classDoc,
                prevClass, nextClass, classTree);
    }

    public AnnotationTypeWriter getAnnotationTypeWriter(
            AnnotationTypeDoc annotationType, Type prevType, Type nextType)
            throws Exception {
        return new AnnotationTypeWriterImpl(configuration,
                annotationType, prevType, nextType);
    }

    public AnnotationTypeFieldWriter
    getAnnotationTypeFieldWriter(AnnotationTypeWriter annotationTypeWriter) throws Exception {
        return new AnnotationTypeFieldWriterImpl(
                (SubWriterHolderWriter) annotationTypeWriter,
                annotationTypeWriter.getAnnotationTypeDoc());
    }

    public AnnotationTypeOptionalMemberWriter
    getAnnotationTypeOptionalMemberWriter(
            AnnotationTypeWriter annotationTypeWriter) throws Exception {
        return new AnnotationTypeOptionalMemberWriterImpl(
                (SubWriterHolderWriter) annotationTypeWriter,
                annotationTypeWriter.getAnnotationTypeDoc());
    }

    public AnnotationTypeRequiredMemberWriter
    getAnnotationTypeRequiredMemberWriter(AnnotationTypeWriter annotationTypeWriter) throws Exception {
        return new AnnotationTypeRequiredMemberWriterImpl(
                (SubWriterHolderWriter) annotationTypeWriter,
                annotationTypeWriter.getAnnotationTypeDoc());
    }

    public EnumConstantWriterImpl getEnumConstantWriter(ClassWriter classWriter)
            throws Exception {
        return new EnumConstantWriterImpl((SubWriterHolderWriter) classWriter,
                classWriter.getClassDoc());
    }

    public FieldWriterImpl getFieldWriter(ClassWriter classWriter)
            throws Exception {
        return new FieldWriterImpl((SubWriterHolderWriter) classWriter,
                classWriter.getClassDoc());
    }

    public PropertyWriterImpl getPropertyWriter(ClassWriter classWriter)
            throws Exception {
        return new PropertyWriterImpl((SubWriterHolderWriter) classWriter,
                classWriter.getClassDoc());
    }

    public MethodWriterImpl getMethodWriter(ClassWriter classWriter)
            throws Exception {
        return new MethodWriterImpl((SubWriterHolderWriter) classWriter,
                classWriter.getClassDoc());
    }

    public ConstructorWriterImpl getConstructorWriter(ClassWriter classWriter)
            throws Exception {
        return new ConstructorWriterImpl((SubWriterHolderWriter) classWriter,
                classWriter.getClassDoc());
    }

    public MemberSummaryWriter getMemberSummaryWriter(
            ClassWriter classWriter, int memberType)
            throws Exception {
        switch (memberType) {
            case VisibleMemberMap.CONSTRUCTORS:
                return getConstructorWriter(classWriter);
            case VisibleMemberMap.ENUM_CONSTANTS:
                return getEnumConstantWriter(classWriter);
            case VisibleMemberMap.FIELDS:
                return getFieldWriter(classWriter);
            case VisibleMemberMap.PROPERTIES:
                return getPropertyWriter(classWriter);
            case VisibleMemberMap.INNERCLASSES:
                return new NestedClassWriterImpl((SubWriterHolderWriter)
                        classWriter, classWriter.getClassDoc());
            case VisibleMemberMap.METHODS:
                return getMethodWriter(classWriter);
            default:
                return null;
        }
    }

    public MemberSummaryWriter getMemberSummaryWriter(
            AnnotationTypeWriter annotationTypeWriter, int memberType)
            throws Exception {
        switch (memberType) {
            case VisibleMemberMap.ANNOTATION_TYPE_FIELDS:
                return (AnnotationTypeFieldWriterImpl)
                        getAnnotationTypeFieldWriter(annotationTypeWriter);
            case VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL:
                return (AnnotationTypeOptionalMemberWriterImpl)
                        getAnnotationTypeOptionalMemberWriter(annotationTypeWriter);
            case VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED:
                return (AnnotationTypeRequiredMemberWriterImpl)
                        getAnnotationTypeRequiredMemberWriter(annotationTypeWriter);
            default:
                return null;
        }
    }

    public SerializedFormWriter getSerializedFormWriter() throws Exception {
        return new SerializedFormWriterImpl(configuration);
    }
}
