/*
 * Copyright 2018 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.shamrock.annotations;

import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
//import static org.jboss.protean.gizmo.MethodDescriptor.ofConstructor;
//import static org.jboss.protean.gizmo.MethodDescriptor.ofMethod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

public class BuildAnnotationProcessor extends AbstractProcessor {

    private static final String BUILD_PRODUCER = "org.jboss.shamrock.deployment.BuildProducerImpl";
    private static final String STATIC_RECORDER = "org.jboss.shamrock.deployment.builditem.StaticBytecodeRecorderBuildItem";
    private static final String MAIN_RECORDER = "org.jboss.shamrock.deployment.builditem.MainBytecodeRecorderBuildItem";

    private static final AtomicInteger classNameCounter = new AtomicInteger();
    private static final String TEMPLATE_ANNOTATION = "org.jboss.shamrock.runtime.Template";
    private static final String CONFIG_PROPERTY_ANNOTATION = "org.eclipse.microprofile.config.inject.ConfigProperty";
    private static final String CONFIG_GROUP_ANNOTATION = "org.jboss.shamrock.runtime.ConfigGroup";
    private static final String SHAMROCK_CONFIG = "org.jboss.shamrock.deployment.ShamrockConfig";

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> ret = new HashSet<>();
        ret.add(Inject.class.getName());
        ret.add(BuildStep.class.getName());
        ret.add(Record.class.getName());
        ret.add(CONFIG_GROUP_ANNOTATION);
        return ret;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver() && !annotations.isEmpty()) {
            try {
                doProcess(annotations, roundEnv);
            } catch (RuntimeException e) {
                throw e;
            }
        }
        return true;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return null;
    }


    public void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Properties configProperties = new Properties();
        Set<String> serviceNames = new HashSet<>();
        Set<TypeElement> processorElements = new HashSet<>();
        //Call jboss logging tools

        final Set<ExecutableElement> processorMethods = new HashSet<>();
        Set<TypeElement> processorClasses = new HashSet<>();
        Map<TypeElement, List<ExecutableElement>> methodMap = new HashMap<>();
        //create a set of classes, and map this to the build step methods
        for (TypeElement annotation : annotations) {
            if (annotation.getQualifiedName().toString().equals(BuildStep.class.getName())) {
                processorMethods.addAll(methodsIn(roundEnv.getElementsAnnotatedWith(annotation)));

                for (ExecutableElement i : processorMethods) {
                    TypeElement enclosingElement = (TypeElement) i.getEnclosingElement();
                    processorClasses.add(enclosingElement);
                    methodMap.computeIfAbsent(enclosingElement, (a) -> new ArrayList<>()).add(i);
                }
            }
        }

        //process each class individually, we only create a single instance of each even if it has multiple steps
        for (TypeElement processor : processorClasses) {
            List<InjectedBuildResource> fieldList = new ArrayList<>();
            List<VariableElement> configuredFields = new ArrayList<>();
            //resolve field injection
            for (VariableElement element : fieldsIn(processor.getEnclosedElements())) {
                try {
                    if (element.getAnnotation(Inject.class) != null) {
                        if (element.getModifiers().contains(Modifier.STATIC)) {
                            throw new RuntimeException("@Inject fields cannot be static");
                        }
                        if (element.getModifiers().contains(Modifier.FINAL)) {
                            throw new RuntimeException("@Inject fields cannot be final");
                        }
                        if (element.getModifiers().contains(Modifier.PRIVATE)) {
                            throw new RuntimeException("@Inject fields cannot be private");
                        }
                        InjectedBuildResource injectedBuildResource = createInjectionResource(element);
                        fieldList.add(injectedBuildResource);
                    } else {
                        if (isAnnotationPresent(element, CONFIG_PROPERTY_ANNOTATION)) {
                            configuredFields.add(element);
                        }
                    }
                } catch (RuntimeException e) {
                    throw new RuntimeException("Exception processing field " + element + " in type " + processor, e);
                }
            }


            //now lets generate some stuff
            //first we create a build provider, this registers the producers and consumers
            //we only create a single one for the the class, even if there are multiple steps
            String processorQualifiedName = processingEnv.getElementUtils().getBinaryName(processor).toString();
            String processorName = processor.getSimpleName().toString();
            PackageElement pkg = processingEnv.getElementUtils().getPackageOf(processor);
            final String buildProviderName = processorQualifiedName + "BuildProvider";
            serviceNames.add(buildProviderName);
            processorElements.add(processor);

            try {
                FileObject res = processingEnv.getFiler().createSourceFile(buildProviderName);
                try(Writer buildProviderWriter = res.openWriter()){
                    buildProviderWriter.append("package "+pkg.getQualifiedName()+";\n");
                    buildProviderWriter.append("public class "+processorName+"BuildProvider implements org.jboss.builder.BuildProvider {\n");

                    {
                        buildProviderWriter.append(" public void installInto(org.jboss.builder.BuildChainBuilder arg1){\n");
                        buildProviderWriter.append("   "+processorName+" theInstance = new "+processorName+"();\n");

                        for (VariableElement i : configuredFields) {
                            injectConfigField(buildProviderWriter, "theInstance", i, processorQualifiedName, "\"\"", configProperties, "");
                        }

                        for (ExecutableElement method : methodMap.get(processor)) {
                            try {
                                if (method.getModifiers().contains(Modifier.PRIVATE)) {
                                    throw new RuntimeException("@BuildStep methods cannot be private: " + processorQualifiedName + ":" + method);
                                }
                                List<InjectedBuildResource> methodInjection = new ArrayList<>();
                                List<String> methodParamTypes = new ArrayList<>();
                                boolean templatePresent = false;
                                boolean recorderContextPresent = false;
                                Record recordAnnotation = method.getAnnotation(Record.class);

                                //resolve method injection
                                for (VariableElement i : method.getParameters()) {
                                    InjectedBuildResource injection = createInjectionResource(i);
                                    if (injection.injectionType == InjectionType.TEMPLATE) {
                                        templatePresent = true;
                                    } else if (injection.injectionType == InjectionType.RECORDER_CONTEXT) {
                                        recorderContextPresent = true;
                                    }
                                    methodInjection.add(injection);

                                    DeclaredType type = (DeclaredType) i.asType();
                                    String simpleType = processingEnv.getElementUtils().getBinaryName(((TypeElement) type.asElement())).toString();
                                    methodParamTypes.add(simpleType);
                                }

                                //make sure that this is annotated with @Record if it is using templates
                                if (recordAnnotation == null && templatePresent) {
                                    throw new RuntimeException("Cannot inject @Template classes into methods that are not annotated @Record: " + method);
                                } else if (recordAnnotation != null && !templatePresent) {
                                    throw new RuntimeException("@Record method does not inject any template classes " + method);
                                } else if (recorderContextPresent && !templatePresent) {
                                    throw new RuntimeException("Cannot inject bean factory into a non @Record method");
                                }
                                MethodReturnInfo returnInfo = processMethodReturnType(method);

                                final String buildStepName = registerBuildStep(fieldList, pkg, processorName, buildProviderWriter, 
                                                                               returnInfo.producedReturnType, 
                                                                               methodInjection, templatePresent, recordAnnotation);

                                generateInvokerClass(pkg, processorName, buildStepName, method, fieldList, 
                                                     methodInjection, templatePresent, recordAnnotation, returnInfo);

                                registerCapabilitiesAndMarkers(buildProviderWriter, method);
                            } catch (Exception e) {

                                throw new RuntimeException("Failed to process " + processorQualifiedName + "." + method.getSimpleName(), e);
                            }
                        }

                        buildProviderWriter.append(" }\n");
                    }

                    buildProviderWriter.append("}\n");
                }
            }catch(IOException e) {
                throw new RuntimeException("Failed to process " + processorQualifiedName, e);
            }
        }

        if (!serviceNames.isEmpty()) {
            //we read them first, as if an IDE has processed this we may not have seen the full set of names
            try {
                String relativeName = "META-INF/services/org.jboss.builder.BuildProvider";
                try {
                    FileObject res = processingEnv.getFiler().getResource(StandardLocation.CLASS_OUTPUT, "", relativeName);
                    try (BufferedReader reader = new BufferedReader(res.openReader(true))) {
                        String r;
                        while ((r = reader.readLine()) != null) {
                            serviceNames.add(r.trim());
                        }
                    }
                } catch (IOException ignore) {
                }

                FileObject res = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", relativeName, processorElements.toArray(new Element[0]));

                try (Writer out = res.openWriter()) {
                    for (String service : serviceNames) {
                        out.write(service);
                        out.write("\n");
                    }
                }
                FileObject descriptions = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/shamrock-descriptions.properties");
                try (OutputStream out = descriptions.openOutputStream()) {
                    configProperties.store(out, "");
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void generateInvokerClass(PackageElement pkg, String processorName, String buildStepName, ExecutableElement method,
                                      List<InjectedBuildResource> fieldList, 
                                      List<InjectedBuildResource> methodInjection, boolean templatePresent, Record recordAnnotation, 
                                      MethodReturnInfo returnInfo)
                                              throws IOException {
        //now generate the actual invoker class that runs the build step
        FileObject res = processingEnv.getFiler().createSourceFile(pkg.getQualifiedName()+"."+buildStepName);
        try(Writer buildProviderWriter = res.openWriter()){
            buildProviderWriter.append("package "+pkg.getQualifiedName()+";\n");
            buildProviderWriter.append("public class "+buildStepName+" implements org.jboss.builder.BuildStep {\n");
            
            buildProviderWriter.append(" private "+processorName+" instance;\n");

            //the constructor, just sets the instance field
            {
                buildProviderWriter.append(" public "+buildStepName+"("+processorName+" arg1){\n");
                buildProviderWriter.append("   instance = arg1;\n");
                buildProviderWriter.append(" }\n");
            }

            //toString
            {
                buildProviderWriter.append(" public String toString(){\n");
                buildProviderWriter.append("   return \""+processorName+"."+method.getSimpleName()+"\";\n");
                buildProviderWriter.append(" }\n");
            }

            // execute
            {
                buildProviderWriter.append(" public void execute(org.jboss.builder.BuildContext arg1){\n");
                //do the field injection
                for (InjectedBuildResource field : fieldList) {
                    generateFieldInjection(buildProviderWriter, processorName, field, "this.instance");
                }

                List<String> args = new ArrayList<>();

                buildProviderWriter.append("    try{\n");
                
                String bytecodeRecorder = null;
                if (templatePresent) {
                    buildProviderWriter.write("    org.jboss.shamrock.deployment.recording.BytecodeRecorderImpl recorder = new org.jboss.shamrock.deployment.recording.BytecodeRecorderImpl("
                            +(recordAnnotation.value() == ExecutionTime.STATIC_INIT)+",\""
                            +method.getEnclosingElement().getSimpleName().toString()+"\","
                            +"\""+method.getSimpleName().toString()+"\");\n");
                    bytecodeRecorder = "recorder";
                }

                for (InjectedBuildResource i : methodInjection) {
                    String val = generateMethodInjection(bytecodeRecorder, i);
                    args.add(val);
                }

                if (returnInfo.producedReturnType != null) {
                    buildProviderWriter.append("    Object handle = ");
                }
                buildProviderWriter.append("this.instance."+method.getSimpleName().toString()+"("+String.join(",",args)+");\n");
                if (returnInfo.producedReturnType != null) {
                    buildProviderWriter.append("    if(handle != null){\n");
                    if (returnInfo.list) {
                        buildProviderWriter.append("      arg1.produce((java.util.List)handle);\n");
                    }else {
                        buildProviderWriter.append("      arg1.produce((org.jboss.builder.item.BuildItem)handle);\n");
                    }
                    buildProviderWriter.append("    }\n");
                }
                
                if (bytecodeRecorder != null) {
                    String buildItem;
                    if (recordAnnotation.value() == ExecutionTime.STATIC_INIT) {
                        buildItem = "new "+STATIC_RECORDER+"("+bytecodeRecorder+")";
                    } else {
                        buildItem = "new "+MAIN_RECORDER+"("+bytecodeRecorder+")";
                    }
                    buildProviderWriter.append("    arg1.produce("+buildItem+");\n");
                }

                buildProviderWriter.append("   }catch(RuntimeException x){\n");
                buildProviderWriter.append("     throw x;\n");
                buildProviderWriter.append("   }catch(Exception x){\n");
                buildProviderWriter.append("     throw new RuntimeException(\"Failed to process build step\", x);\n");
                buildProviderWriter.append("   }\n");

                buildProviderWriter.append(" }\n");
            }
            buildProviderWriter.append("}\n");
        }
    }

    private void injectConfigField(Writer writer, String instance, VariableElement field, String className, String keyPrefix, Properties configProperties, String currentKeyDesc) throws IOException {
        ConfigInjectionInfo val = readConfigAnnotation(field);
        writer.append("    "+instance+"."+field.getSimpleName().toString()+" = ");
        if (val.optional) {
            writer.append("java.util.Optional.ofNullable(");
            createConfigValue(writer, val, keyPrefix, configProperties, currentKeyDesc);
            writer.append(");\n");
        } else {
            createConfigValue(writer, val, keyPrefix, configProperties, currentKeyDesc);
            writer.append(";\n");
        }
    }

    private void createConfigValue(Writer writer, ConfigInjectionInfo val, String keyPrefix, Properties configProperties, String currentKeyDescPrefix) throws IOException {
        String currentKeyDesc = currentKeyDescPrefix.isEmpty() ? val.name : (currentKeyDescPrefix + "." + val.name);
        
        String keyName = keyPrefix+" + \""+val.name+"\"";

        if (val.type != ConfigType.MAP && val.type != ConfigType.CUSTOM_TYPE) {
            configProperties.put(currentKeyDesc, val.javadoc);
        }

        String defaultVal;
        if (val.defaultValue != null) {
            defaultVal = "\""+val.defaultValue+"\"";
        } else {
            defaultVal = "null";
        }
        switch (val.type) {
            case STRING:
                writer.append(SHAMROCK_CONFIG+".getString("+keyName+", "+defaultVal+", "+(val.optional)+")");
                return;
            case PRIMITIVE_BOOLEAN:
                writer.append(SHAMROCK_CONFIG+".getBoolean("+keyName+", "+defaultVal+")");
                return;
            case PRIMITIVE_INT:
                writer.append(SHAMROCK_CONFIG+".getInt("+keyName+", "+defaultVal+")");
                return;
            case INTEGER:
                writer.append(SHAMROCK_CONFIG+".getBoxedInt("+keyName+", "+defaultVal+", "+(val.optional)+")");
                return;
            case BOOLEAN:
                writer.append(SHAMROCK_CONFIG+".getBoxedBoolean("+keyName+", "+defaultVal+", "+(val.optional)+")");
                return;
            case CUSTOM_TYPE: {
                String instanceName = gensym("instance");
                writer.append("org.jboss.builder.BuildStepBuilder.let(new "+val.customTypeName+"(), "+instanceName+" -> {\n");
                Element element = processingEnv.getElementUtils().getTypeElement(val.customTypeName);
                if (element == null) {
                    throw new RuntimeException("Could not obtain class information for " + val.customTypeName);
                }

                String keyPrefixName = keyName + "+ \".\""; //the name + a .
                for (Element e : fieldsIn(element.getEnclosedElements())) {
                    if (isAnnotationPresent(e, CONFIG_PROPERTY_ANNOTATION)) {
                        injectConfigField(writer, instanceName, (VariableElement) e, val.customTypeName, keyPrefixName, configProperties, currentKeyDesc);
                        writer.append(";\n");
                    }
                }
                writer.append("return "+instanceName+";})\n");
                return;
            }
            case MAP:
                //the complex one
                String instanceName = gensym("instance");
                String valName = gensym("val");
                String miName = gensym("mi");
                writer.append("org.jboss.builder.BuildStepBuilder.let(new java.util.HashMap(), "+instanceName+" -> {\n");
                //we now need to set up func to create the objects and insert them into the map
                writer.append(SHAMROCK_CONFIG+".getNames("+keyName+").forEach("+valName+" -> {\n");
                writer.append(val.customTypeName+" "+miName+" = new "+val.customTypeName+"();\n");
                Element element = processingEnv.getElementUtils().getTypeElement(val.customTypeName);
                for (Element e : fieldsIn(element.getEnclosedElements())) {
                    if (isAnnotationPresent(e, CONFIG_PROPERTY_ANNOTATION)) {
                        String prefix = "new StringBuilder().append("+keyPrefix+").append(\""+val.name + "."+"\").append("+valName+").append(\".\").toString()";
                        injectConfigField(writer, miName, (VariableElement) e, val.customTypeName, prefix, configProperties, currentKeyDesc + ".*");
                    }
                }
                writer.append("  "+instanceName+".put("+valName+", "+miName+");\n");
                writer.append("});\n");
                writer.append("return "+instanceName+";})\n");
                return;
            default:
                throw new RuntimeException("unknown type " + val.type);
        }
    }

    private String gensym(String prefix) {
        return prefix+classNameCounter.incrementAndGet();
    }

    private ConfigInjectionInfo readConfigAnnotation(VariableElement field) {
        for (AnnotationMirror i : field.getAnnotationMirrors()) {
            if (((TypeElement) i.getAnnotationType().asElement()).getQualifiedName().toString().equals(CONFIG_PROPERTY_ANNOTATION)) {
                ConfigInjectionInfo ret = new ConfigInjectionInfo();
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> elem : i.getElementValues().entrySet()) {
                    switch (elem.getKey().getSimpleName().toString()) {
                        case "name":
                            ret.name = (String) elem.getValue().getValue();
                            break;
                        case "defaultValue":
                            ret.defaultValue = (String) elem.getValue().getValue();
                            break;
                        default:
                            throw new RuntimeException("Unknown annotation value " + elem.getKey());
                    }
                }
                if(ret.name == null) {
                    String msg = "@ConfigProperty with no name specified " + field.getEnclosingElement() + "." + field.getSimpleName();
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg);
                    throw new RuntimeException(msg);
                }
                if (field.asType() instanceof PrimitiveType) {
                    PrimitiveType type = (PrimitiveType) field.asType();
                    switch (type.getKind()) {
                        case INT:
                            ret.type = ConfigType.PRIMITIVE_INT;
                            break;
                        case BOOLEAN:
                            ret.type = ConfigType.PRIMITIVE_BOOLEAN;
                            break;
                        default:
                            throw new RuntimeException("Unable to inject config into primitive type of " + type + " this has not been implemented yet");
                    }
                } else if (field.asType() instanceof DeclaredType) {

                    DeclaredType type = (DeclaredType) field.asType();
                    String simpleType = processingEnv.getElementUtils().getBinaryName(((TypeElement) type.asElement())).toString();

                    if (simpleType.equals(Map.class.getName())) {
                        ret.type = ConfigType.MAP;
                        if (type.getTypeArguments().size() != 2) {
                            throw new RuntimeException("Cannot use @ConfigProperty on a Map that does not include a generic type " + field);
                        }
                        TypeMirror typeMirror = type.getTypeArguments().get(1);
                        ret.customTypeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) ((DeclaredType) typeMirror).asElement())).toString();
                    } else if (simpleType.equals(Optional.class.getName())) {
                        if (type.getTypeArguments().size() != 1) {
                            throw new RuntimeException("Cannot use @ConfigProperty on an Optional that does not include a generic type " + field);
                        }
                        ret.optional = true;
                        TypeMirror typeMirror = type.getTypeArguments().get(0);
                        String typeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) ((DeclaredType) typeMirror).asElement())).toString();
                        if (typeName.equals(String.class.getName())) {
                            ret.type = ConfigType.STRING;
                        } else if (typeName.equals(Integer.class.getName())) {
                            ret.type = ConfigType.INTEGER;
                        } else if (typeName.equals(Boolean.class.getName())) {
                            ret.type = ConfigType.BOOLEAN;
                        } else {
                            if (!isAnnotationPresent(processingEnv.getTypeUtils().asElement(typeMirror), CONFIG_GROUP_ANNOTATION)) {
                                throw new RuntimeException("Cannot inject a configured instance of " + typeName + " as it is not annotated with " + CONFIG_GROUP_ANNOTATION + " into " + field);
                            }
                            ret.type = ConfigType.CUSTOM_TYPE;
                            ret.customTypeName = typeName;
                        }
                    } else if (simpleType.equals(String.class.getName())) {
                        ret.type = ConfigType.STRING;
                    } else if (simpleType.equals(Integer.class.getName())) {
                        ret.type = ConfigType.INTEGER;
                    } else if (simpleType.equals(Boolean.class.getName())) {
                        ret.type = ConfigType.BOOLEAN;
                    } else {
                        ret.type = ConfigType.CUSTOM_TYPE;
                        if (!isAnnotationPresent(processingEnv.getTypeUtils().asElement(type), CONFIG_GROUP_ANNOTATION)) {
                            throw new RuntimeException("Cannot inject a configured instance of " + simpleType + " as it is not annotated with " + CONFIG_GROUP_ANNOTATION + " into " + field);
                        }
                        ret.customTypeName = simpleType;
                    }
                } else {
                    throw new RuntimeException("Unknown field type " + field);
                }
                if (ret.optional && ret.defaultValue != null) {
                    throw new RuntimeException(field + " is optional but has a default value. Default values must not be set for optional elements");
                }

                if(ret.type != ConfigType.MAP && ret.type != ConfigType.CUSTOM_TYPE) {
                    String rawJavadoc = processingEnv.getElementUtils().getDocComment(field);
                    if (rawJavadoc == null) {
                        rawJavadoc = tryLoadJavadocFromFile(field);
                        if (rawJavadoc == null) {
                            // FIXME: throw but not in Eclipse APT
                            rawJavadoc = "ECLIPSE BUG FOR JAVADOC IN "+field.getEnclosingElement() + "." + field;
//                            throw new RuntimeException("Field must include a javadoc description "  + field.getEnclosingElement() + "." + field);
                        }
                    }
                    ret.javadoc = rawJavadoc.trim();
                }
                return ret;
            }
        }
        throw new RuntimeException("Could not find Configured annotation, this should not happen");
    }

    private String tryLoadJavadocFromFile(VariableElement field) {
        TypeElement ownerClass = (TypeElement) field.getEnclosingElement();
        try {
            FileObject file = processingEnv.getFiler().getResource(StandardLocation.SOURCE_OUTPUT, "", ownerClass.getQualifiedName().toString().replace(".", "/") + ".confjavadoc");
            try (Reader reader = file.openReader(true)) {
                Properties properties = new Properties();
                properties.load(reader);
                return (String) properties.get(field.getSimpleName().toString());
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unable to load pre-saved javadoc", field);
        }

        return null;
    }

    private MethodReturnInfo processMethodReturnType(ExecutableElement method) {
        MethodReturnInfo returnInfo = new MethodReturnInfo();
        //handle the return type
        if (method.getReturnType().getKind() != TypeKind.VOID) {
            if (method.getReturnType().getKind().isPrimitive()) {
                throw new RuntimeException("@BuildStep method return type cannot be primitive: " + method);
            }
            DeclaredType returnTypeElement = (DeclaredType) method.getReturnType();
            String returnType = processingEnv.getElementUtils().getBinaryName(((TypeElement) returnTypeElement.asElement())).toString();

            if (returnType.equals(List.class.getName())) {
                returnInfo.list = true;

                if (returnTypeElement.getTypeArguments().size() != 1) {
                    throw new RuntimeException("Cannot use @BuildResource on a list that does not include a generic type");
                }
                TypeMirror typeMirror = returnTypeElement.getTypeArguments().get(0);

                verifyType(typeMirror, "org.jboss.builder.item.MultiBuildItem");
                returnInfo.producedReturnType = processingEnv.getElementUtils().getBinaryName(((TypeElement) ((DeclaredType) typeMirror).asElement())).toString();
                returnInfo.rawReturnType = returnType;
            } else {
                verifyType(returnTypeElement, "org.jboss.builder.item.BuildItem");
                returnInfo.rawReturnType = returnType;
                returnInfo.producedReturnType = returnType;
            }
        }
        return returnInfo;
    }

    private String registerBuildStep(List<InjectedBuildResource> fieldList, PackageElement pkg, String processorName, Writer writer, 
                                     String producedReturnType, List<InjectedBuildResource> methodInjection, 
                                     boolean templatePresent, Record recordAnnotation) throws IOException {
        int counter = classNameCounter.incrementAndGet();
        final String buildStepSimpleName = processorName + "BuildStep" + counter;
        String builderName = "builder"+counter;

        writer.append("    org.jboss.builder.BuildStepBuilder "+builderName+" = arg1.addBuildStep(new "+buildStepSimpleName+"(theInstance));\n");
        
        //register fields
        for (InjectedBuildResource field : fieldList) {
            if (field.consumedTypeName != null) {
                if (field.injectionType == InjectionType.OPTIONAL) {
                    writer.append("    "+builderName+".consumes("+field.consumedTypeName+".class, org.jboss.builder.ConsumeFlags.of(org.jboss.builder.ConsumeFlag.OPTIONAL));\n");
                }else {
                    writer.append("    "+builderName+".consumes("+field.consumedTypeName+".class);\n");
                }
            }
            if (field.producedTypeName != null) {
                writer.append("    "+builderName+".produces("+field.producedTypeName+".class);\n");
            }
        }
        //if it is using bytecode recording register the production of a new recorder
        if (templatePresent) {
            String type;
            if (recordAnnotation.value() == ExecutionTime.STATIC_INIT) {
                type = STATIC_RECORDER;
            } else {
                type = MAIN_RECORDER;
            }
            if (recordAnnotation.optional()) {
                writer.append("    "+builderName+".produces("+type+".class, org.jboss.builder.ProduceFlag.WEAK);\n");
            } else {
                writer.append("    "+builderName+".produces("+type+".class);\n");
            }

        }
        //register parameter injection
        for (InjectedBuildResource injection : methodInjection) {
            if (injection.injectionType != InjectionType.TEMPLATE && injection.injectionType != InjectionType.RECORDER_CONTEXT && injection.injectionType != InjectionType.EXECUTOR) {
                if (injection.consumedTypeName != null) {
                    if (injection.injectionType == InjectionType.OPTIONAL) {
                        writer.append("    "+builderName+".consumes("+injection.consumedTypeName+".class, org.jboss.builder.ConsumeFlags.of(org.jboss.builder.ConsumeFlag.OPTIONAL));\n");
                    }else {
                        writer.append("    "+builderName+".consumes("+injection.consumedTypeName+".class);\n");
                    }
                }
                if (injection.producedTypeName != null) {
                    writer.append("    "+builderName+".produces("+injection.producedTypeName+".class);\n");
                }
            }
        }

        //register the production of the return type
        if (producedReturnType != null) {
            writer.append("    "+builderName+".produces("+producedReturnType+".class);\n");
        }

        //install it
        writer.append("    "+builderName+".build();\n");
        return buildStepSimpleName;
    }

    private String generateMethodInjection(String bytecodeRecorder, InjectedBuildResource i) {
        String val;
        if (i.injectionType == InjectionType.RECORDER_CONTEXT) {
            val = bytecodeRecorder;
        } else if (i.injectionType == InjectionType.TEMPLATE) {
            val = bytecodeRecorder+".getRecordingProxy("+i.consumedTypeName+".class)";
        } else if (i.injectionType == InjectionType.EXECUTOR) {
            val = "arg1.getExecutor()";
        } else if (i.injectionType == InjectionType.SIMPLE) {
            val = "arg1.consume("+i.consumedTypeName+".class)";
        } else if (i.injectionType == InjectionType.LIST) {
            val = "arg1.consumeMulti("+i.consumedTypeName+".class)";
        } else if (i.injectionType == InjectionType.OPTIONAL) {
            val = "java.util.Optional.ofNullable(arg1.consume("+i.consumedTypeName+".class))";
        } else {
            val = "new "+BUILD_PRODUCER+"("+i.producedTypeName+".class, arg1)";
        }
        return val;
    }

    private void generateFieldInjection(Writer writer, String processorClassName, InjectedBuildResource field, String instanceField) throws IOException {
        if (field.injectionType == InjectionType.SIMPLE) {
            writer.append("    "+instanceField+"."+field.element.getSimpleName().toString()+" = arg1.consume("+field.consumedTypeName+".class);\n");
        } else if (field.injectionType == InjectionType.LIST) {
            writer.append("    "+instanceField+"."+field.element.getSimpleName().toString()+" = arg1.consumeMulti("+field.consumedTypeName+".class);\n");
        } else if (field.injectionType == InjectionType.OPTIONAL) {
            writer.append("    "+instanceField+"."+field.element.getSimpleName().toString()+" = java.util.Optional.ofNullable(arg1.consume("+field.consumedTypeName+".class));\n");
        } else if (field.injectionType == InjectionType.EXECUTOR) {
            writer.append("    "+instanceField+"."+field.element.getSimpleName().toString()+" = arg1.getExecutor();\n");
        } else if (field.injectionType == InjectionType.TEMPLATE || field.injectionType == InjectionType.RECORDER_CONTEXT) {
            throw new RuntimeException("Cannot inject @Template class into a field, only method parameter injection is supported for templates. Field: " + field.element);
        } else {
            writer.append("    "+instanceField+"."+field.element.getSimpleName().toString()+" = new "+BUILD_PRODUCER+"("+field.producedTypeName+".class, arg1);\n");
        }
    }

    private void registerCapabilitiesAndMarkers(Writer writer, ExecutableElement method) throws IOException {
        BuildStep annotation = method.getAnnotation(BuildStep.class);
        String[] capabilities = annotation.providesCapabilities();
        if (capabilities.length > 0) {
            for (String i : capabilities) {
                writer.append("    arg1.addBuildStep(\n");
                writer.append("      new org.jboss.shamrock.deployment.steps.CapabilityBuildStep(\""+i+"\"))\n");
                writer.append("     .produces(org.jboss.shamrock.deployment.builditem.CapabilityBuildItem.class)\n");
                writer.append("     .build();\n");
            }
        }
        String[] markers = annotation.applicationArchiveMarkers();
        if (markers.length > 0) {
            for (String i : markers) {
                writer.append("    arg1.addBuildStep(\n");
                writer.append("      new org.jboss.shamrock.deployment.steps.ApplicationArchiveMarkerBuildStep(\""+i+"\"))\n");
                writer.append("     .produces(org.jboss.shamrock.deployment.builditem.AdditionalApplicationArchiveMarkerBuildItem.class)\n");
                writer.append("     .build();\n");
            }
        }
    }

    private InjectedBuildResource createInjectionResource(Element element) {
        try {
            TypeMirror elementType = element.asType();
            if (elementType.getKind() != TypeKind.DECLARED) {
                throw new RuntimeException("Unexpected field type: " + elementType+" of kind: "+elementType.getKind());
            }
            return createInjectionResource(element, (DeclaredType) elementType);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process " + element, e);
        }
    }

    private InjectedBuildResource createInjectionResource(Element element, DeclaredType elementType) {
        DeclaredType type = elementType;
        String simpleType = processingEnv.getElementUtils().getBinaryName(((TypeElement) type.asElement())).toString();
        InjectionType ft;
        String producedTypeName = null;
        String consumedTypeName = null;

        if (simpleType.equals(List.class.getName())) {
            ft = InjectionType.LIST;
            if (type.getTypeArguments().size() != 1) {
                throw new RuntimeException("Cannot use @BuildResource on a list that does not include a generic type");
            }
            TypeMirror typeMirror = type.getTypeArguments().get(0);

            verifyType(typeMirror, "org.jboss.builder.item.MultiBuildItem");
            consumedTypeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) ((DeclaredType) typeMirror).asElement())).toString();

        } else if (simpleType.equals(Optional.class.getName())) {
            ft = InjectionType.OPTIONAL;
            if (type.getTypeArguments().size() != 1) {
                throw new RuntimeException("Cannot use @BuildResource on an optional that does not include a generic type");
            }
            TypeMirror typeMirror = type.getTypeArguments().get(0);

            verifyType(typeMirror, "org.jboss.builder.item.SimpleBuildItem");
            consumedTypeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) ((DeclaredType) typeMirror).asElement())).toString();

        } else if (simpleType.equals(BuildProducer.class.getName())) {
            ft = InjectionType.PRODUCER;
            if (type.getTypeArguments().size() != 1) {
                throw new RuntimeException("Cannot use @BuildResource on a BuildProducer that does not include a generic type");
            }
            TypeMirror typeMirror = type.getTypeArguments().get(0);
            verifyType(typeMirror, "org.jboss.builder.item.BuildItem");
            producedTypeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) ((DeclaredType) typeMirror).asElement())).toString();
        } else {
            consumedTypeName = simpleType;
            if (isAnnotationPresent(processingEnv.getTypeUtils().asElement(type), TEMPLATE_ANNOTATION)) {
                ft = InjectionType.TEMPLATE;
            } else if (simpleType.equals("org.jboss.shamrock.deployment.recording.RecorderContext")) {
                ft = InjectionType.RECORDER_CONTEXT;
            } else if (simpleType.equals("java.util.concurrent.Executor")) {
                ft = InjectionType.EXECUTOR;
            } else {
                verifyType(type, "org.jboss.builder.item.SimpleBuildItem");
                ft = InjectionType.SIMPLE;
            }
        }
        return new InjectedBuildResource(element, ft, producedTypeName, consumedTypeName);
    }

    private boolean isAnnotationPresent(Element element, String annotationName) {
        for (AnnotationMirror i : element.getAnnotationMirrors()) {
            if (((TypeElement) i.getAnnotationType().asElement()).getQualifiedName().toString().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    private void verifyType(TypeMirror type, String expectedFqn) {
        if (!processingEnv.getTypeUtils().isSubtype(type, processingEnv.getElementUtils().getTypeElement(expectedFqn).asType())) {
            throw new RuntimeException(type + " is not an instance of " + expectedFqn);
        }
    }

    enum InjectionType {
        SIMPLE,
        LIST,
        PRODUCER,
        TEMPLATE,
        RECORDER_CONTEXT,
        EXECUTOR,
        OPTIONAL
    }

    static class ConfigInjectionInfo {
        String name;
        String defaultValue;
        String javadoc;
        boolean optional;
        ConfigType type;
        String customTypeName;
    }

    enum ConfigType {
        STRING,
        PRIMITIVE_INT,
        PRIMITIVE_BOOLEAN,
        INTEGER,
        BOOLEAN,
        MAP,
        CUSTOM_TYPE
    }

    static class InjectedBuildResource {
        final Element element;
        final InjectionType injectionType;
        final String producedTypeName;
        final String consumedTypeName;

        InjectedBuildResource(Element element, InjectionType injectionType, String producedTypeName, String consumedTypeName) {
            this.element = element;
            this.injectionType = injectionType;
            this.producedTypeName = producedTypeName;
            this.consumedTypeName = consumedTypeName;
        }
    }

    static class MethodReturnInfo {

        String rawReturnType = "V";
        String producedReturnType = null;
        boolean list;
    }
}
