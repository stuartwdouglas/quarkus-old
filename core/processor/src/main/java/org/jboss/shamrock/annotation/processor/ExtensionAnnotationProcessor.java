/*
 * Copyright 2019 Red Hat, Inc.
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

package org.jboss.shamrock.annotation.processor;

import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static javax.lang.model.util.ElementFilter.typesIn;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Completion;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

public class ExtensionAnnotationProcessor extends AbstractProcessor {

    private static final String ANNOTATION_BUILD_STEP = "org.jboss.shamrock.deployment.annotations.BuildStep";
    private static final String ANNOTATION_CONFIG_GROUP = "org.jboss.shamrock.runtime.annotations.ConfigGroup";
    private static final String ANNOTATION_CONFIG_ITEM = "org.jboss.shamrock.runtime.annotations.ConfigItem";
    private static final String ANNOTATION_RECORD = "org.jboss.shamrock.deployment.annotations.Record";

    public ExtensionAnnotationProcessor() {
    }

    @Override
    public Set<String> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> ret = new HashSet<>();
        ret.add(ANNOTATION_BUILD_STEP);
        ret.add(ANNOTATION_RECORD);
        ret.add(ANNOTATION_CONFIG_GROUP);
        return ret;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        doProcess(annotations, roundEnv);
        if (roundEnv.processingOver()) {
            doFinish(roundEnv);
        }
        return true;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return Collections.emptySet();
    }

    public void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            switch(annotation.getQualifiedName().toString()) {
                case ANNOTATION_BUILD_STEP:
                    processBuildStep(roundEnv, annotation);
                    break;
                case ANNOTATION_CONFIG_GROUP:
                    processConfigGroup(roundEnv, annotation);
                    break;
            }
        }
    }

    void doFinish(RoundEnvironment roundEnv) {
        final Filer filer = processingEnv.getFiler();
        final FileObject tempResource;
        try {
            tempResource = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "ignore.tmp");
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to create temp output file: " + e);
            return;
        }
        final URI uri = tempResource.toUri();
        tempResource.delete();
        Path path;
        try {
            path = Paths.get(uri).getParent();
        } catch (RuntimeException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Resource path URI is invalid: " + uri);
            return;
        }
        Collection<String> bscListClasses = new TreeSet<>();
        Properties javaDocProperties = new Properties();
        try {
            Files.walkFileTree(path, new FileVisitor<Path>() {
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                    final String nameStr = file.getFileName().toString();
                    if (nameStr.endsWith(".bsc")) {
                        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                if (! line.isEmpty()) {
                                    bscListClasses.add(line);
                                }
                            }
                        } catch (IOException e) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to read file " + file + ": " + e);
                        }
                    } else if (nameStr.endsWith(".jdp")) {
                        final Properties p = new Properties();
                        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            p.load(br);
                        } catch (IOException e) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to read file " + file + ": " + e);
                        }
                        final Set<String> names = p.stringPropertyNames();
                        for (String name : names) {
                            javaDocProperties.setProperty(name, p.getProperty(name));
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to visit file " + file + ": " + exc);
                    return FileVisitResult.CONTINUE;
                }

                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "File walk failed: " + e);
        }
        try {
            final FileObject listResource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/shamrock-build-steps.list");
            try (OutputStream os = listResource.openOutputStream()) {
                try (BufferedOutputStream bos = new BufferedOutputStream(os)) {
                    try (OutputStreamWriter osw = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
                        try (BufferedWriter bw = new BufferedWriter(osw)) {
                            for (String item : bscListClasses) {
                                bw.write(item);
                                bw.newLine();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write build steps listing: " + e);
            return;
        }
        try {
            final FileObject listResource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/shamrock-javadoc.properties");
            try (OutputStream os = listResource.openOutputStream()) {
                try (BufferedOutputStream bos = new BufferedOutputStream(os)) {
                    try (OutputStreamWriter osw = new OutputStreamWriter(bos, StandardCharsets.UTF_8)) {
                        try (BufferedWriter bw = new BufferedWriter(osw)) {
                            javaDocProperties.store(bw, "");
                        }
                    }
                }
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to write javadoc properties: " + e);
            return;
        }
    }

    private void processBuildStep(RoundEnvironment roundEnv, TypeElement annotation) {
        final Set<String> processorClassNames = new HashSet<>();

        for (ExecutableElement i : methodsIn(roundEnv.getElementsAnnotatedWith(annotation))) {
            final TypeElement clazz = getClassOf(i);
            if (clazz == null) {
                continue;
            }
            final PackageElement pkg = processingEnv.getElementUtils().getPackageOf(clazz);
            if (pkg == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Element " + clazz + " has no enclosing package");
                continue;
            }
            final String binaryName = processingEnv.getElementUtils().getBinaryName(clazz).toString();
            if (processorClassNames.add(binaryName)) {
                // new class
                recordConfigJavadoc(clazz);
                final StringBuilder rbn = getRelativeBinaryName(clazz, new StringBuilder());
                try {
                    final FileObject itemResource = processingEnv.getFiler().createResource(
                        StandardLocation.SOURCE_OUTPUT,
                        pkg.getQualifiedName().toString(),
                        rbn.toString() + ".bsc",
                        clazz
                    );
                    try (OutputStream os = itemResource.openOutputStream()) {
                        try (BufferedOutputStream bos = new BufferedOutputStream(os)) {
                            try (OutputStreamWriter osw = new OutputStreamWriter(bos)) {
                                try (BufferedWriter bw = new BufferedWriter(osw)) {
                                    bw.write(binaryName);
                                    bw.newLine();
                                }
                            }
                        }
                    }
                } catch (IOException e1) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to create " + rbn + " in " + pkg + ": " + e1, clazz);
                }
            }
        }
    }

    private StringBuilder getRelativeBinaryName(TypeElement te, StringBuilder b) {
        final Element enclosing = te.getEnclosingElement();
        if (enclosing instanceof TypeElement) {
            getRelativeBinaryName((TypeElement) enclosing, b);
            b.append('$');
        }
        b.append(te.getSimpleName());
        return b;
    }

    private TypeElement getClassOf(Element e) {
        Element t = e;
        while (! (t instanceof TypeElement)) {
            if (t == null) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Element " + e + " has no enclosing class");
                return null;
            }
            t = t.getEnclosingElement();
        }
        return (TypeElement) t;
    }

    private void recordConfigJavadoc(TypeElement clazz) {
        final Properties javadocProps = new Properties();
        for (Element e : clazz.getEnclosedElements()) {
            switch (e.getKind()) {
                case FIELD: {
                    if (isAnnotationPresent(e, ANNOTATION_CONFIG_ITEM)) {
                        processFieldConfigItem((VariableElement) e, javadocProps);
                    }
                    break;
                }
                case CONSTRUCTOR: {
                    final ExecutableElement ex = (ExecutableElement) e;
                    if (hasParameterAnnotated(ex, ANNOTATION_CONFIG_ITEM)) {
                        processCtorConfigItem(ex, javadocProps);
                    }
                    break;
                }
                case METHOD: {
                    final ExecutableElement ex = (ExecutableElement) e;
                    if (hasParameterAnnotated(ex, ANNOTATION_CONFIG_ITEM)) {
                        processMethodConfigItem(ex, javadocProps);
                    }
                    break;
                }
                default:
            }
        }
        if (javadocProps.isEmpty()) return;
        final PackageElement pkg = processingEnv.getElementUtils().getPackageOf(clazz);
        final String rbn = getRelativeBinaryName(clazz, new StringBuilder()).append(".jdp").toString();
        try {
            FileObject file = processingEnv.getFiler().createResource(
                StandardLocation.SOURCE_OUTPUT,
                pkg.getQualifiedName().toString(),
                rbn,
                clazz
            );
            try (Writer writer = file.openWriter()) {
                javadocProps.store(writer, "");
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to persist resource " + rbn + ": " + e);
        }
    }

    private void processFieldConfigItem(VariableElement field, Properties javadocProps) {
        javadocProps.put(field.getSimpleName().toString(), getRequiredJavadoc(field));
    }

    private void processCtorConfigItem(ExecutableElement ctor, Properties javadocProps) {
        final String docComment = getRequiredJavadoc(ctor);
        final StringBuilder buf = new StringBuilder();
        appendParamTypes(ctor, buf);
        javadocProps.put(buf.toString(), docComment);
    }

    private void processMethodConfigItem(ExecutableElement method, Properties javadocProps) {
        final String docComment = getRequiredJavadoc(method);
        final StringBuilder buf = new StringBuilder();
        buf.append(method.getSimpleName().toString());
        appendParamTypes(method, buf);
        javadocProps.put(buf.toString(), docComment);
    }

    private void processConfigGroup(RoundEnvironment roundEnv, TypeElement annotation) {
        final Set<String> groupClassNames = new HashSet<>();
        for (TypeElement i : typesIn(roundEnv.getElementsAnnotatedWith(annotation))) {
            if (groupClassNames.add(i.getQualifiedName().toString())) {
                recordConfigJavadoc(i);
            }
        }
    }

    private void appendParamTypes(ExecutableElement ex, final StringBuilder buf) {
        final List<? extends VariableElement> params = ex.getParameters();
        if(params.isEmpty()) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Expected at least one parameter", ex);
            return;
        }
        VariableElement param = params.get(0);
        DeclaredType dt = (DeclaredType) param.asType();
        String typeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) dt.asElement())).toString();
        buf.append('(').append(typeName);
        for(int i = 1; i < params.size(); ++i) {
            param = params.get(i);
            dt = (DeclaredType) param.asType();
            typeName = processingEnv.getElementUtils().getBinaryName(((TypeElement) dt.asElement())).toString();
            buf.append(',').append(typeName);
        }
        buf.append(')');
    }

    private String getRequiredJavadoc(Element e) {
        final String docComment = processingEnv.getElementUtils().getDocComment(e);
        if(docComment == null) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to find javadoc for config item " + e, e);
            return "";
        }
        return docComment;
    }

    private static boolean hasParameterAnnotated(ExecutableElement ex, String annotationName) {
        for(VariableElement param : ex.getParameters()) {
            if(isAnnotationPresent(param, annotationName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnnotationPresent(Element element, String annotationName) {
        for (AnnotationMirror i : element.getAnnotationMirrors()) {
            if (((TypeElement) i.getAnnotationType().asElement()).getQualifiedName().toString().equals(annotationName)) {
                return true;
            }
        }
        return false;
    }
}
