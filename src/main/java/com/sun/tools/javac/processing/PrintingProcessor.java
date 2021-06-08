package com.sun.tools.javac.processing;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor7;
import javax.lang.model.util.SimpleElementVisitor8;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;

import static javax.lang.model.element.ElementKind.*;
import static javax.lang.model.element.NestingKind.TOP_LEVEL;

@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PrintingProcessor extends AbstractProcessor {
    PrintWriter writer;

    public PrintingProcessor() {
        super();
        writer = new PrintWriter(System.out);
    }

    public void setWriter(Writer w) {
        writer = new PrintWriter(w);
    }

    @Override
    public boolean process(Set<? extends TypeElement> tes,
                           RoundEnvironment renv) {
        for (Element element : renv.getRootElements()) {
            print(element);
        }

        return true;
    }

    void print(Element element) {
        new PrintingElementVisitor(writer, processingEnv.getElementUtils()).
                visit(element).flush();
    }

    public static class PrintingElementVisitor
            extends SimpleElementVisitor8<PrintingElementVisitor, Boolean> {
        private static final String[] spaces = {
                "",
                "  ",
                "    ",
                "      ",
                "        ",
                "          ",
                "            ",
                "              ",
                "                ",
                "                  ",
                "                    "
        };
        final PrintWriter writer;
        final Elements elementUtils;
        int indentation;

        public PrintingElementVisitor(Writer w, Elements elementUtils) {
            super();
            this.writer = new PrintWriter(w);
            this.elementUtils = elementUtils;
            indentation = 0;
        }

        @Override
        protected PrintingElementVisitor defaultAction(Element e, Boolean newLine) {
            if (newLine != null && newLine)
                writer.println();
            printDocComment(e);
            printModifiers(e);
            return this;
        }

        @Override
        public PrintingElementVisitor visitExecutable(ExecutableElement e, Boolean p) {
            ElementKind kind = e.getKind();
            if (kind != STATIC_INIT &&
                    kind != INSTANCE_INIT) {
                Element enclosing = e.getEnclosingElement();

                if (kind == CONSTRUCTOR &&
                        enclosing != null &&
                        NestingKind.ANONYMOUS ==

                                (new SimpleElementVisitor7<NestingKind, Void>() {
                                    @Override
                                    public NestingKind visitType(TypeElement e, Void p) {
                                        return e.getNestingKind();
                                    }
                                }).visit(enclosing))
                    return this;
                defaultAction(e, true);
                printFormalTypeParameters(e, true);
                switch (kind) {
                    case CONSTRUCTOR:

                        writer.print(e.getEnclosingElement().getSimpleName());
                        break;
                    case METHOD:
                        writer.print(e.getReturnType().toString());
                        writer.print(" ");
                        writer.print(e.getSimpleName().toString());
                        break;
                }
                writer.print("(");
                printParameters(e);
                writer.print(")");
                AnnotationValue defaultValue = e.getDefaultValue();
                if (defaultValue != null)
                    writer.print(" default " + defaultValue);
                printThrows(e);
                writer.println(";");
            }
            return this;
        }

        @Override
        public PrintingElementVisitor visitType(TypeElement e, Boolean p) {
            ElementKind kind = e.getKind();
            NestingKind nestingKind = e.getNestingKind();
            if (NestingKind.ANONYMOUS == nestingKind) {


                writer.print("new ");


                List<? extends TypeMirror> interfaces = e.getInterfaces();
                if (!interfaces.isEmpty())
                    writer.print(interfaces.get(0));
                else
                    writer.print(e.getSuperclass());
                writer.print("(");


                if (interfaces.isEmpty()) {


                    List<? extends ExecutableElement> constructors =
                            ElementFilter.constructorsIn(e.getEnclosedElements());
                    if (!constructors.isEmpty())
                        printParameters(constructors.get(0));
                }
                writer.print(")");
            } else {
                if (nestingKind == TOP_LEVEL) {
                    PackageElement pkg = elementUtils.getPackageOf(e);
                    if (!pkg.isUnnamed())
                        writer.print("package " + pkg.getQualifiedName() + ";\n");
                }
                defaultAction(e, true);
                switch (kind) {
                    case ANNOTATION_TYPE:
                        writer.print("@interface");
                        break;
                    default:
                        writer.print(kind.toString().toLowerCase());
                }
                writer.print(" ");
                writer.print(e.getSimpleName());
                printFormalTypeParameters(e, false);

                if (kind == CLASS) {
                    TypeMirror supertype = e.getSuperclass();
                    if (supertype.getKind() != TypeKind.NONE) {
                        TypeElement e2 = (TypeElement)
                                ((DeclaredType) supertype).asElement();
                        if (e2.getSuperclass().getKind() != TypeKind.NONE)
                            writer.print(" extends " + supertype);
                    }
                }
                printInterfaces(e);
            }
            writer.println(" {");
            indentation++;
            if (kind == ENUM) {
                List<Element> enclosedElements =
                        new ArrayList<Element>(e.getEnclosedElements());

                List<Element> enumConstants = new ArrayList<Element>();
                for (Element element : enclosedElements) {
                    if (element.getKind() == ENUM_CONSTANT)
                        enumConstants.add(element);
                }
                if (!enumConstants.isEmpty()) {
                    int i;
                    for (i = 0; i < enumConstants.size() - 1; i++) {
                        this.visit(enumConstants.get(i), true);
                        writer.print(",");
                    }
                    this.visit(enumConstants.get(i), true);
                    writer.println(";\n");
                    enclosedElements.removeAll(enumConstants);
                }
                for (Element element : enclosedElements)
                    this.visit(element);
            } else {
                for (Element element : e.getEnclosedElements())
                    this.visit(element);
            }
            indentation--;
            indent();
            writer.println("}");
            return this;
        }

        @Override
        public PrintingElementVisitor visitVariable(VariableElement e, Boolean newLine) {
            ElementKind kind = e.getKind();
            defaultAction(e, newLine);
            if (kind == ENUM_CONSTANT)
                writer.print(e.getSimpleName());
            else {
                writer.print(e.asType().toString() + " " + e.getSimpleName());
                Object constantValue = e.getConstantValue();
                if (constantValue != null) {
                    writer.print(" = ");
                    writer.print(elementUtils.getConstantExpression(constantValue));
                }
                writer.println(";");
            }
            return this;
        }

        @Override
        public PrintingElementVisitor visitTypeParameter(TypeParameterElement e, Boolean p) {
            writer.print(e.getSimpleName());
            return this;
        }

        @Override
        public PrintingElementVisitor visitPackage(PackageElement e, Boolean p) {
            defaultAction(e, false);
            if (!e.isUnnamed())
                writer.println("package " + e.getQualifiedName() + ";");
            else
                writer.println("// Unnamed package");
            return this;
        }

        public void flush() {
            writer.flush();
        }

        private void printDocComment(Element e) {
            String docComment = elementUtils.getDocComment(e);
            if (docComment != null) {

                StringTokenizer st = new StringTokenizer(docComment,
                        "\n\r");
                indent();
                writer.println("/**");
                while (st.hasMoreTokens()) {
                    indent();
                    writer.print(" *");
                    writer.println(st.nextToken());
                }
                indent();
                writer.println(" */");
            }
        }

        private void printModifiers(Element e) {
            ElementKind kind = e.getKind();
            if (kind == PARAMETER) {
                printAnnotationsInline(e);
            } else {
                printAnnotations(e);
                indent();
            }
            if (kind == ENUM_CONSTANT)
                return;
            Set<Modifier> modifiers = new LinkedHashSet<Modifier>();
            modifiers.addAll(e.getModifiers());
            switch (kind) {
                case ANNOTATION_TYPE:
                case INTERFACE:
                    modifiers.remove(Modifier.ABSTRACT);
                    break;
                case ENUM:
                    modifiers.remove(Modifier.FINAL);
                    modifiers.remove(Modifier.ABSTRACT);
                    break;
                case METHOD:
                case FIELD:
                    Element enclosingElement = e.getEnclosingElement();
                    if (enclosingElement != null &&
                            enclosingElement.getKind().isInterface()) {
                        modifiers.remove(Modifier.PUBLIC);
                        modifiers.remove(Modifier.ABSTRACT);
                        modifiers.remove(Modifier.STATIC);
                        modifiers.remove(Modifier.FINAL);
                    }
                    break;
            }
            for (Modifier m : modifiers) {
                writer.print(m.toString() + " ");
            }
        }

        private void printFormalTypeParameters(Parameterizable e,
                                               boolean pad) {
            List<? extends TypeParameterElement> typeParams = e.getTypeParameters();
            if (typeParams.size() > 0) {
                writer.print("<");
                boolean first = true;
                for (TypeParameterElement tpe : typeParams) {
                    if (!first)
                        writer.print(", ");
                    printAnnotationsInline(tpe);
                    writer.print(tpe.toString());
                    first = false;
                }
                writer.print(">");
                if (pad)
                    writer.print(" ");
            }
        }

        private void printAnnotationsInline(Element e) {
            List<? extends AnnotationMirror> annots = e.getAnnotationMirrors();
            for (AnnotationMirror annotationMirror : annots) {
                writer.print(annotationMirror);
                writer.print(" ");
            }
        }

        private void printAnnotations(Element e) {
            List<? extends AnnotationMirror> annots = e.getAnnotationMirrors();
            for (AnnotationMirror annotationMirror : annots) {
                indent();
                writer.println(annotationMirror);
            }
        }

        private void printParameters(ExecutableElement e) {
            List<? extends VariableElement> parameters = e.getParameters();
            int size = parameters.size();
            switch (size) {
                case 0:
                    break;
                case 1:
                    for (VariableElement parameter : parameters) {
                        printModifiers(parameter);
                        if (e.isVarArgs()) {
                            TypeMirror tm = parameter.asType();
                            if (tm.getKind() != TypeKind.ARRAY)
                                throw new AssertionError("Var-args parameter is not an array type: " + tm);
                            writer.print(((ArrayType) tm).getComponentType());
                            writer.print("...");
                        } else
                            writer.print(parameter.asType());
                        writer.print(" " + parameter.getSimpleName());
                    }
                    break;
                default: {
                    int i = 1;
                    for (VariableElement parameter : parameters) {
                        if (i == 2)
                            indentation++;
                        if (i > 1)
                            indent();
                        printModifiers(parameter);
                        if (i == size && e.isVarArgs()) {
                            TypeMirror tm = parameter.asType();
                            if (tm.getKind() != TypeKind.ARRAY)
                                throw new AssertionError("Var-args parameter is not an array type: " + tm);
                            writer.print(((ArrayType) tm).getComponentType());
                            writer.print("...");
                        } else
                            writer.print(parameter.asType());
                        writer.print(" " + parameter.getSimpleName());
                        if (i < size)
                            writer.println(",");
                        i++;
                    }
                    if (parameters.size() >= 2)
                        indentation--;
                }
                break;
            }
        }

        private void printInterfaces(TypeElement e) {
            ElementKind kind = e.getKind();
            if (kind != ANNOTATION_TYPE) {
                List<? extends TypeMirror> interfaces = e.getInterfaces();
                if (interfaces.size() > 0) {
                    writer.print((kind.isClass() ? " implements" : " extends"));
                    boolean first = true;
                    for (TypeMirror interf : interfaces) {
                        if (!first)
                            writer.print(",");
                        writer.print(" ");
                        writer.print(interf.toString());
                        first = false;
                    }
                }
            }
        }

        private void printThrows(ExecutableElement e) {
            List<? extends TypeMirror> thrownTypes = e.getThrownTypes();
            final int size = thrownTypes.size();
            if (size != 0) {
                writer.print(" throws");
                int i = 1;
                for (TypeMirror thrownType : thrownTypes) {
                    if (i == 1)
                        writer.print(" ");
                    if (i == 2)
                        indentation++;
                    if (i >= 2)
                        indent();
                    writer.print(thrownType);
                    if (i != size)
                        writer.println(", ");
                    i++;
                }
                if (size >= 2)
                    indentation--;
            }
        }

        private void indent() {
            int indentation = this.indentation;
            if (indentation < 0)
                return;
            final int maxIndex = spaces.length - 1;
            while (indentation > maxIndex) {
                writer.print(spaces[maxIndex]);
                indentation -= maxIndex;
            }
            writer.print(spaces[indentation]);
        }
    }
}
