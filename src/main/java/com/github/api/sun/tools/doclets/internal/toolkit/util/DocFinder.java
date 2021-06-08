package com.github.api.sun.tools.doclets.internal.toolkit.util;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.InheritableTaglet;

import java.util.ArrayList;
import java.util.List;

public class DocFinder {

    public static Output search(Input input) {
        Output output = new Output();
        if (input.isInheritDocTag) {


        } else if (input.taglet == null) {

            output.inlineTags = input.isFirstSentence ?
                    input.element.firstSentenceTags() :
                    input.element.inlineTags();
            output.holder = input.element;
        } else {
            input.taglet.inherit(input, output);
        }
        if (output.inlineTags != null && output.inlineTags.length > 0) {
            return output;
        }
        output.isValidInheritDocTag = false;
        Input inheritedSearchInput = input.copy();
        inheritedSearchInput.isInheritDocTag = false;
        if (input.element instanceof MethodDoc) {
            MethodDoc overriddenMethod = ((MethodDoc) input.element).overriddenMethod();
            if (overriddenMethod != null) {
                inheritedSearchInput.element = overriddenMethod;
                output = search(inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (output.inlineTags.length > 0) {
                    return output;
                }
            }


            MethodDoc[] implementedMethods =
                    (new ImplementedMethods((MethodDoc) input.element, null)).build(false);
            for (int i = 0; i < implementedMethods.length; i++) {
                inheritedSearchInput.element = implementedMethods[i];
                output = search(inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (output.inlineTags.length > 0) {
                    return output;
                }
            }
        } else if (input.element instanceof ClassDoc) {
            ProgramElementDoc superclass = ((ClassDoc) input.element).superclass();
            if (superclass != null) {
                inheritedSearchInput.element = superclass;
                output = search(inheritedSearchInput);
                output.isValidInheritDocTag = true;
                if (output.inlineTags.length > 0) {
                    return output;
                }
            }
        }
        return output;
    }

    public static class Input {

        public ProgramElementDoc element;

        public InheritableTaglet taglet = null;

        public String tagId = null;

        public Tag tag = null;

        public boolean isFirstSentence = false;

        public boolean isInheritDocTag = false;

        public boolean isTypeVariableParamTag = false;

        public Input(ProgramElementDoc element, InheritableTaglet taglet, Tag tag,
                     boolean isFirstSentence, boolean isInheritDocTag) {
            this(element);
            this.taglet = taglet;
            this.tag = tag;
            this.isFirstSentence = isFirstSentence;
            this.isInheritDocTag = isInheritDocTag;
        }

        public Input(ProgramElementDoc element, InheritableTaglet taglet, String tagId) {
            this(element);
            this.taglet = taglet;
            this.tagId = tagId;
        }

        public Input(ProgramElementDoc element, InheritableTaglet taglet, String tagId,
                     boolean isTypeVariableParamTag) {
            this(element);
            this.taglet = taglet;
            this.tagId = tagId;
            this.isTypeVariableParamTag = isTypeVariableParamTag;
        }

        public Input(ProgramElementDoc element, InheritableTaglet taglet) {
            this(element);
            this.taglet = taglet;
        }

        public Input(ProgramElementDoc element) {
            if (element == null)
                throw new NullPointerException();
            this.element = element;
        }

        public Input(ProgramElementDoc element, boolean isFirstSentence) {
            this(element);
            this.isFirstSentence = isFirstSentence;
        }

        public Input copy() {
            Input clone = new Input(this.element);
            clone.taglet = this.taglet;
            clone.tagId = this.tagId;
            clone.tag = this.tag;
            clone.isFirstSentence = this.isFirstSentence;
            clone.isInheritDocTag = this.isInheritDocTag;
            clone.isTypeVariableParamTag = this.isTypeVariableParamTag;
            if (clone.element == null)
                throw new NullPointerException();
            return clone;
        }
    }

    public static class Output {

        public Tag holderTag;

        public Doc holder;

        public Tag[] inlineTags = new Tag[]{};

        public boolean isValidInheritDocTag = true;

        public List<Tag> tagList = new ArrayList<Tag>();
    }
}
