package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.FieldDoc;
import com.github.api.sun.javadoc.MethodDoc;
import com.github.api.sun.javadoc.SerialFieldTag;

import java.io.IOException;

public interface SerializedFormWriter {

    Content getHeader(String header);

    Content getSerializedSummariesHeader();

    Content getPackageSerializedHeader();

    Content getPackageHeader(String packageName);

    Content getClassSerializedHeader();

    Content getClassHeader(ClassDoc classDoc);

    Content getSerialUIDInfoHeader();

    void addSerialUIDInfo(String header, String serialUID,
                          Content serialUidTree);

    Content getClassContentHeader();

    SerialFieldWriter getSerialFieldWriter(ClassDoc classDoc);

    SerialMethodWriter getSerialMethodWriter(ClassDoc classDoc);

    void close() throws IOException;

    Content getSerializedContent(Content serializedTreeContent);

    void addFooter(Content serializedTree);

    void printDocument(Content serializedTree) throws IOException;

    interface SerialFieldWriter {

        Content getSerializableFieldsHeader();

        Content getFieldsContentHeader(boolean isLastContent);

        Content getSerializableFields(String heading, Content contentTree);

        void addMemberDeprecatedInfo(FieldDoc field, Content contentTree);

        void addMemberDescription(FieldDoc field, Content contentTree);

        void addMemberDescription(SerialFieldTag serialFieldTag, Content contentTree);

        void addMemberTags(FieldDoc field, Content contentTree);

        void addMemberHeader(ClassDoc fieldType, String fieldTypeStr,
                             String fieldDimensions, String fieldName, Content contentTree);

        boolean shouldPrintOverview(FieldDoc field);
    }

    interface SerialMethodWriter {

        Content getSerializableMethodsHeader();

        Content getMethodsContentHeader(boolean isLastContent);

        Content getSerializableMethods(String heading, Content serializableMethodTree);

        Content getNoCustomizationMsg(String msg);

        void addMemberHeader(MethodDoc member, Content methodsContentTree);

        void addDeprecatedMemberInfo(MethodDoc member, Content methodsContentTree);

        void addMemberDescription(MethodDoc member, Content methodsContentTree);

        void addMemberTags(MethodDoc member, Content methodsContentTree);
    }
}
