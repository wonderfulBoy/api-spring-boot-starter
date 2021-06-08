package com.github.api.sun.tools.javac.code;

import com.github.api.sun.tools.javac.comp.Annotate;
import com.github.api.sun.tools.javac.comp.AttrContext;
import com.github.api.sun.tools.javac.comp.Env;
import com.github.api.sun.tools.javac.util.*;

import javax.tools.JavaFileObject;
import java.util.Map;

import static com.github.api.sun.tools.javac.code.Kinds.PCK;

public class SymbolMetadata {
    private static final List<Attribute.Compound> DECL_NOT_STARTED = List.of(null);
    private static final List<Attribute.Compound> DECL_IN_PROGRESS = List.of(null);
    private final Symbol sym;
    private List<Attribute.Compound> attributes = DECL_NOT_STARTED;
    private List<Attribute.TypeCompound> type_attributes = List.nil();
    private List<Attribute.TypeCompound> init_type_attributes = List.nil();
    private List<Attribute.TypeCompound> clinit_type_attributes = List.nil();

    public SymbolMetadata(Symbol sym) {
        this.sym = sym;
    }

    public List<Attribute.Compound> getDeclarationAttributes() {
        return filterDeclSentinels(attributes);
    }

    public void setDeclarationAttributes(List<Attribute.Compound> a) {
        Assert.check(pendingCompletion() || !isStarted());
        if (a == null) {
            throw new NullPointerException();
        }
        attributes = a;
    }

    public List<Attribute.TypeCompound> getTypeAttributes() {
        return type_attributes;
    }

    public void setTypeAttributes(List<Attribute.TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        type_attributes = a;
    }

    public List<Attribute.TypeCompound> getInitTypeAttributes() {
        return init_type_attributes;
    }

    public void setInitTypeAttributes(List<Attribute.TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        init_type_attributes = a;
    }

    public List<Attribute.TypeCompound> getClassInitTypeAttributes() {
        return clinit_type_attributes;
    }

    public void setClassInitTypeAttributes(List<Attribute.TypeCompound> a) {
        if (a == null) {
            throw new NullPointerException();
        }
        clinit_type_attributes = a;
    }

    public void setAttributes(SymbolMetadata other) {
        if (other == null) {
            throw new NullPointerException();
        }
        setDeclarationAttributes(other.getDeclarationAttributes());
        setTypeAttributes(other.getTypeAttributes());
        setInitTypeAttributes(other.getInitTypeAttributes());
        setClassInitTypeAttributes(other.getClassInitTypeAttributes());
    }

    public void setDeclarationAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.Compound> ctx) {
        Assert.check(pendingCompletion() || (!isStarted() && sym.kind == PCK));
        this.setDeclarationAttributes(getAttributesForCompletion(ctx));
    }

    public void appendTypeAttributesWithCompletion(final Annotate.AnnotateRepeatedContext<Attribute.TypeCompound> ctx) {
        this.appendUniqueTypes(getAttributesForCompletion(ctx));
    }

    private <T extends Attribute.Compound> List<T> getAttributesForCompletion(
            final Annotate.AnnotateRepeatedContext<T> ctx) {
        Map<Symbol.TypeSymbol, ListBuffer<T>> annotated = ctx.annotated;
        boolean atLeastOneRepeated = false;
        List<T> buf = List.nil();
        for (ListBuffer<T> lb : annotated.values()) {
            if (lb.size() == 1) {
                buf = buf.prepend(lb.first());
            } else {


                T res;
                @SuppressWarnings("unchecked")
                T ph = (T) new Placeholder<T>(ctx, lb.toList(), sym);
                res = ph;
                buf = buf.prepend(res);
                atLeastOneRepeated = true;
            }
        }
        if (atLeastOneRepeated) {


            ctx.annotateRepeated(new Annotate.Worker() {
                @Override
                public String toString() {
                    return "repeated annotation pass of: " + sym + " in: " + sym.owner;
                }

                @Override
                public void run() {
                    complete(ctx);
                }
            });
        }

        return buf.reverse();
    }

    public SymbolMetadata reset() {
        attributes = DECL_IN_PROGRESS;
        return this;
    }

    public boolean isEmpty() {
        return !isStarted()
                || pendingCompletion()
                || attributes.isEmpty();
    }

    public boolean isTypesEmpty() {
        return type_attributes.isEmpty();
    }

    public boolean pendingCompletion() {
        return attributes == DECL_IN_PROGRESS;
    }

    public SymbolMetadata append(List<Attribute.Compound> l) {
        attributes = filterDeclSentinels(attributes);
        if (l.isEmpty()) {
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.appendList(l);
        }
        return this;
    }

    public SymbolMetadata appendUniqueTypes(List<Attribute.TypeCompound> l) {
        if (l.isEmpty()) {
        } else if (type_attributes.isEmpty()) {
            type_attributes = l;
        } else {


            for (Attribute.TypeCompound tc : l) {
                if (!type_attributes.contains(tc))
                    type_attributes = type_attributes.append(tc);
            }
        }
        return this;
    }

    public SymbolMetadata appendInitTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.isEmpty()) {
        } else if (init_type_attributes.isEmpty()) {
            init_type_attributes = l;
        } else {
            init_type_attributes = init_type_attributes.appendList(l);
        }
        return this;
    }

    public SymbolMetadata appendClassInitTypeAttributes(List<Attribute.TypeCompound> l) {
        if (l.isEmpty()) {
        } else if (clinit_type_attributes.isEmpty()) {
            clinit_type_attributes = l;
        } else {
            clinit_type_attributes = clinit_type_attributes.appendList(l);
        }
        return this;
    }

    public SymbolMetadata prepend(List<Attribute.Compound> l) {
        attributes = filterDeclSentinels(attributes);
        if (l.isEmpty()) {
        } else if (attributes.isEmpty()) {
            attributes = l;
        } else {
            attributes = attributes.prependList(l);
        }
        return this;
    }

    private List<Attribute.Compound> filterDeclSentinels(List<Attribute.Compound> a) {
        return (a == DECL_IN_PROGRESS || a == DECL_NOT_STARTED)
                ? List.nil()
                : a;
    }

    private boolean isStarted() {
        return attributes != DECL_NOT_STARTED;
    }

    private List<Attribute.Compound> getPlaceholders() {
        List<Attribute.Compound> res = List.nil();
        for (Attribute.Compound a : filterDeclSentinels(attributes)) {
            if (a instanceof Placeholder) {
                res = res.prepend(a);
            }
        }
        return res.reverse();
    }

    private List<Attribute.TypeCompound> getTypePlaceholders() {
        List<Attribute.TypeCompound> res = List.nil();
        for (Attribute.TypeCompound a : type_attributes) {
            if (a instanceof Placeholder) {
                res = res.prepend(a);
            }
        }
        return res.reverse();
    }

    private <T extends Attribute.Compound> void complete(Annotate.AnnotateRepeatedContext<T> ctx) {
        Log log = ctx.log;
        Env<AttrContext> env = ctx.env;
        JavaFileObject oldSource = log.useSource(env.toplevel.sourcefile);
        try {

            if (ctx.isTypeCompound) {
                Assert.check(!isTypesEmpty());
                if (isTypesEmpty()) {
                    return;
                }
                List<Attribute.TypeCompound> result = List.nil();
                for (Attribute.TypeCompound a : getTypeAttributes()) {
                    if (a instanceof Placeholder) {
                        @SuppressWarnings("unchecked")
                        Placeholder<Attribute.TypeCompound> ph = (Placeholder<Attribute.TypeCompound>) a;
                        Attribute.TypeCompound replacement = replaceOne(ph, ph.getRepeatedContext());
                        if (null != replacement) {
                            result = result.prepend(replacement);
                        }
                    } else {
                        result = result.prepend(a);
                    }
                }
                type_attributes = result.reverse();
                Assert.check(SymbolMetadata.this.getTypePlaceholders().isEmpty());
            } else {
                Assert.check(!pendingCompletion());
                if (isEmpty()) {
                    return;
                }
                List<Attribute.Compound> result = List.nil();
                for (Attribute.Compound a : getDeclarationAttributes()) {
                    if (a instanceof Placeholder) {
                        @SuppressWarnings("unchecked")
                        Attribute.Compound replacement = replaceOne((Placeholder<T>) a, ctx);
                        if (null != replacement) {
                            result = result.prepend(replacement);
                        }
                    } else {
                        result = result.prepend(a);
                    }
                }
                attributes = result.reverse();
                Assert.check(SymbolMetadata.this.getPlaceholders().isEmpty());
            }
        } finally {
            log.useSource(oldSource);
        }
    }

    private <T extends Attribute.Compound> T replaceOne(Placeholder<T> placeholder, Annotate.AnnotateRepeatedContext<T> ctx) {
        Log log = ctx.log;

        T validRepeated = ctx.processRepeatedAnnotations(placeholder.getPlaceholderFor(), sym);
        if (validRepeated != null) {


            ListBuffer<T> manualContainer = ctx.annotated.get(validRepeated.type.tsym);
            if (manualContainer != null) {
                log.error(ctx.pos.get(manualContainer.first()), "invalid.repeatable.annotation.repeated.and.container.present",
                        manualContainer.first().type.tsym);
            }
        }

        return validRepeated;
    }

    private static class Placeholder<T extends Attribute.Compound> extends Attribute.TypeCompound {
        private final Annotate.AnnotateRepeatedContext<T> ctx;
        private final List<T> placeholderFor;
        private final Symbol on;

        public Placeholder(Annotate.AnnotateRepeatedContext<T> ctx, List<T> placeholderFor, Symbol on) {
            super(on.type, List.nil(),
                    ctx.isTypeCompound ?
                            ((TypeCompound) placeholderFor.head).position :
                            new TypeAnnotationPosition());
            this.ctx = ctx;
            this.placeholderFor = placeholderFor;
            this.on = on;
        }

        @Override
        public String toString() {
            return "<placeholder: " + placeholderFor + " on: " + on + ">";
        }

        public List<T> getPlaceholderFor() {
            return placeholderFor;
        }

        public Annotate.AnnotateRepeatedContext<T> getRepeatedContext() {
            return ctx;
        }
    }
}
