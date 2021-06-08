package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.internal.toolkit.util.ClassTree;
import com.sun.tools.javac.jvm.Profile;

public interface WriterFactory {

    ConstantsSummaryWriter getConstantsSummaryWriter()
            throws Exception;

    PackageSummaryWriter getPackageSummaryWriter(PackageDoc
                                                         packageDoc, PackageDoc prevPkg, PackageDoc nextPkg)
            throws Exception;

    ProfileSummaryWriter getProfileSummaryWriter(Profile
                                                         profile, Profile prevProfile, Profile nextProfile)
            throws Exception;

    ProfilePackageSummaryWriter getProfilePackageSummaryWriter(
            PackageDoc packageDoc, PackageDoc prevPkg, PackageDoc nextPkg,
            Profile profile) throws Exception;

    ClassWriter getClassWriter(ClassDoc classDoc,
                               ClassDoc prevClass, ClassDoc nextClass, ClassTree classTree)
            throws Exception;

    AnnotationTypeWriter getAnnotationTypeWriter(
            AnnotationTypeDoc annotationType, Type prevType, Type nextType)
            throws Exception;

    MethodWriter getMethodWriter(ClassWriter classWriter)
            throws Exception;

    AnnotationTypeFieldWriter
    getAnnotationTypeFieldWriter(
            AnnotationTypeWriter annotationTypeWriter) throws Exception;

    AnnotationTypeOptionalMemberWriter
    getAnnotationTypeOptionalMemberWriter(
            AnnotationTypeWriter annotationTypeWriter) throws Exception;

    AnnotationTypeRequiredMemberWriter
    getAnnotationTypeRequiredMemberWriter(
            AnnotationTypeWriter annotationTypeWriter) throws Exception;

    EnumConstantWriter getEnumConstantWriter(
            ClassWriter classWriter) throws Exception;

    FieldWriter getFieldWriter(ClassWriter classWriter)
            throws Exception;

    PropertyWriter getPropertyWriter(ClassWriter classWriter)
            throws Exception;

    ConstructorWriter getConstructorWriter(
            ClassWriter classWriter)
            throws Exception;

    MemberSummaryWriter getMemberSummaryWriter(
            ClassWriter classWriter, int memberType)
            throws Exception;

    MemberSummaryWriter getMemberSummaryWriter(
            AnnotationTypeWriter annotationTypeWriter, int memberType)
            throws Exception;

    SerializedFormWriter getSerializedFormWriter() throws Exception;
}
