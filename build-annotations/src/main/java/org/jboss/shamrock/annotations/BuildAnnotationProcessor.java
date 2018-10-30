package org.jboss.shamrock.annotations;

import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.typesIn;
import static org.jboss.protean.gizmo.MethodDescriptor.ofConstructor;
import static org.jboss.protean.gizmo.MethodDescriptor.ofMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.jboss.builder.BuildChainBuilder;
import org.jboss.builder.BuildContext;
import org.jboss.builder.BuildProvider;
import org.jboss.builder.BuildStep;
import org.jboss.builder.BuildStepBuilder;
import org.jboss.builder.item.BuildItem;
import org.jboss.builder.item.MultiBuildItem;
import org.jboss.builder.item.SimpleBuildItem;
import org.jboss.protean.gizmo.CatchBlockCreator;
import org.jboss.protean.gizmo.ClassCreator;
import org.jboss.protean.gizmo.ClassOutput;
import org.jboss.protean.gizmo.ExceptionTable;
import org.jboss.protean.gizmo.FieldDescriptor;
import org.jboss.protean.gizmo.MethodCreator;
import org.jboss.protean.gizmo.MethodDescriptor;
import org.jboss.protean.gizmo.ResultHandle;

public class BuildAnnotationProcessor extends AbstractProcessor {

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> ret = new HashSet<>();
        ret.add(BuildResource.class.getName());
        ret.add(BuildProcessor.class.getName());
        return ret;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver() && !annotations.isEmpty()) {
            doProcess(annotations, roundEnv);
        }
        return true;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return null;
    }


    public void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<String> serviceNames = new HashSet<>();
        Set<TypeElement> processorElements = new HashSet<>();
        //Call jboss logging tools
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().toString().equals(BuildProcessor.class.getName())) {
                final Set<? extends TypeElement> processors = typesIn(roundEnv.getElementsAnnotatedWith(annotation));
                for (TypeElement processor : processors) {
                    List<BuildResourceField> fieldList = new ArrayList<>();
                    for (VariableElement field : fieldsIn(processor.getEnclosedElements())) {
                        try {
                            if (field.getAnnotation(BuildResource.class) != null) {
                                if (field.getModifiers().contains(Modifier.STATIC)) {
                                    throw new RuntimeException("@BuildResource fields cannot be static");
                                }
                                if (field.getModifiers().contains(Modifier.FINAL)) {
                                    throw new RuntimeException("@BuildResource fields cannot be final");
                                }
                                if (field.getModifiers().contains(Modifier.PRIVATE)) {
                                    throw new RuntimeException("@BuildResource fields cannot be private");
                                }
                                System.out.println(field.getSimpleName().toString());
                                if (field.asType().getKind() != TypeKind.DECLARED) {
                                    throw new RuntimeException("Unexpected field type: " + field.asType());
                                }
                                DeclaredType type = (DeclaredType) field.asType();
                                String simpleType = ((TypeElement) type.asElement()).getQualifiedName().toString();
                                FieldType ft;
                                String producedTypeName = null;
                                String consumedTypeName = null;

                                if (simpleType.equals(List.class.getName())) {
                                    ft = FieldType.LIST;
                                    if (type.getTypeArguments().size() != 1) {
                                        throw new RuntimeException("Cannot use @BuildResource on a list that does not include a generic type");
                                    }
                                    TypeMirror typeMirror = type.getTypeArguments().get(0);

                                    System.out.println("LIST " + typeMirror + " " + typeMirror.getKind() + " " + typeMirror.getClass());
                                    verifyType(typeMirror, MultiBuildItem.class);
                                    consumedTypeName = ((TypeElement) ((DeclaredType) typeMirror).asElement()).getQualifiedName().toString();

                                } else if (simpleType.equals(BuildProducer.class.getName())) {
                                    ft = FieldType.PRODUCER;
                                    if (type.getTypeArguments().size() != 1) {
                                        throw new RuntimeException("Cannot use @BuildResource on a BuildProducer that does not include a generic type");
                                    }
                                    TypeMirror typeMirror = type.getTypeArguments().get(0);
                                    verifyType(typeMirror, BuildItem.class);
                                    producedTypeName = ((TypeElement) ((DeclaredType) typeMirror).asElement()).getQualifiedName().toString();
                                } else {
                                    consumedTypeName = simpleType;
                                    verifyType(type, SimpleBuildItem.class);
                                    ft = FieldType.SIMPLE;
                                }
                                fieldList.add(new BuildResourceField(field, ft, producedTypeName, consumedTypeName));
                            }
                        } catch (RuntimeException e) {
                            throw new RuntimeException("Exception processing field " + field + " in type " + processor, e);
                        }
                    }

                    //now lets generate some stuff
                    //first we create a build provider
                    String processorClassName = processor.getQualifiedName().toString();
                    final String buildProviderName = processorClassName + "BuildProvider";
                    final String buildStepName = processorClassName + "BuildStep";
                    serviceNames.add(buildProviderName);
                    processorElements.add(processor);
                    try (ClassCreator creator = new ClassCreator(new ProcessorClassOutput(processor), buildProviderName, null, Object.class.getName(), BuildProvider.class.getName())) {
                        MethodCreator mc = creator.getMethodCreator("installInto", void.class, BuildChainBuilder.class);

                        ResultHandle step = mc.newInstance(ofConstructor(buildStepName));
                        ResultHandle builder = mc.invokeVirtualMethod(MethodDescriptor.ofMethod(BuildChainBuilder.class, "addBuildStep", BuildStepBuilder.class, BuildStep.class), mc.getMethodParam(0), step);

                        for (BuildResourceField field : fieldList) {
                            if (field.consumedTypeName != null) {
                                mc.invokeVirtualMethod(ofMethod(BuildStepBuilder.class, "consumes", BuildStepBuilder.class, Class.class), builder, mc.loadClass(field.consumedTypeName));
                            }
                            if (field.producedTypeName != null) {
                                mc.invokeVirtualMethod(ofMethod(BuildStepBuilder.class, "produces", BuildStepBuilder.class, Class.class), builder, mc.loadClass(field.producedTypeName));
                            }
                        }
                        mc.invokeVirtualMethod(ofMethod(BuildStepBuilder.class, "build", BuildChainBuilder.class), builder);
                        mc.returnValue(null);
                    }

                    try (ClassCreator creator = new ClassCreator(new ProcessorClassOutput(processor), buildStepName, null, Object.class.getName(), BuildStep.class.getName())) {
                        MethodCreator mc = creator.getMethodCreator("execute", void.class, BuildContext.class);

                        ResultHandle p = mc.newInstance(ofConstructor(processorClassName));

                        for (BuildResourceField field : fieldList) {
                            if (field.fieldType == FieldType.SIMPLE) {
                                ResultHandle val = mc.invokeVirtualMethod(ofMethod(BuildContext.class, "consume", SimpleBuildItem.class, Class.class), mc.getMethodParam(0), mc.loadClass(field.consumedTypeName));
                                mc.writeInstanceField(FieldDescriptor.of(processorClassName, field.element.getSimpleName().toString(), field.consumedTypeName), p, val);
                            } else if (field.fieldType == FieldType.LIST) {
                                ResultHandle val = mc.invokeVirtualMethod(ofMethod(BuildContext.class, "consumeMulti", List.class, Class.class), mc.getMethodParam(0), mc.loadClass(field.consumedTypeName));
                                mc.writeInstanceField(FieldDescriptor.of(processorClassName, field.element.getSimpleName().toString(), List.class), p, val);
                            } else {
                                ResultHandle val = mc.newInstance(ofConstructor(BuildProducerImpl.class, Class.class, BuildContext.class), mc.loadClass(field.producedTypeName), mc.getMethodParam(0));
                                mc.writeInstanceField(FieldDescriptor.of(processorClassName, field.element.getSimpleName().toString(), BuildProducer.class), p, val);
                            }
                        }
                        ExceptionTable table = mc.addTryCatch();
                        mc.invokeInterfaceMethod(ofMethod("org.jboss.shamrock.deployment.BuildProcessingStep", "build", void.class), p);
                        CatchBlockCreator catchBlockCreator = table.addCatchClause(Exception.class);
                        catchBlockCreator.throwException(RuntimeException.class, "Failed to process build step", catchBlockCreator.getCaughtException());
                        table.complete();
                        mc.returnValue(null);
                    }
                }
            }
        }

        if (!serviceNames.isEmpty()) {
            //we read them first, as if an IDE has processed this we may not have seen the full set of names
            try {

                FileObject res = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/" + BuildProvider.class.getName(), processorElements.toArray(new Element[0]));

                try (BufferedReader reader = new BufferedReader(res.openReader(true))) {
                    serviceNames.add(reader.readLine().trim());
                } catch (Exception ignored) {

                }

                try (Writer out = res.openWriter()) {
                    for (String service : serviceNames) {
                        out.write(service);
                        out.write("\n");
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void verifyType(TypeMirror type, Class expected) {
        if (!processingEnv.getTypeUtils().isSubtype(type, processingEnv.getElementUtils().getTypeElement(expected.getName()).asType())) {
            throw new RuntimeException(type + " is not an instance of " + expected);
        }
    }


    enum FieldType {
        SIMPLE,
        LIST,
        PRODUCER
    }

    static class BuildResourceField {
        final VariableElement element;
        final FieldType fieldType;
        final String producedTypeName;
        final String consumedTypeName;

        BuildResourceField(VariableElement element, FieldType fieldType, String producedTypeName, String consumedTypeName) {
            this.element = element;
            this.fieldType = fieldType;
            this.producedTypeName = producedTypeName;
            this.consumedTypeName = consumedTypeName;
        }
    }

    private class ProcessorClassOutput implements ClassOutput {
        private final TypeElement processor;

        public ProcessorClassOutput(TypeElement processor) {
            this.processor = processor;
        }

        @Override
        public void write(String name, byte[] data) {
            try (OutputStream out = processingEnv.getFiler().createClassFile(name.replace("/", "."), processor).openOutputStream()) {
                out.write(data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
