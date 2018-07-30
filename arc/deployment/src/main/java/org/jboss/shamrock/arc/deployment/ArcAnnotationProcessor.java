package org.jboss.shamrock.arc.deployment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jboss.shamrock.arc.runtime.ArcDeploymentTemplate;
import org.jboss.shamrock.deployment.ArchiveContext;
import org.jboss.shamrock.deployment.BeanDeployment;
import org.jboss.shamrock.deployment.ProcessorContext;
import org.jboss.shamrock.deployment.ResourceProcessor;
import org.jboss.shamrock.deployment.RuntimePriority;
import org.jboss.shamrock.deployment.codegen.BytecodeRecorder;
import org.jboss.weld.arc.ArcContainer;
import org.jboss.weld.arc.processor.BeanProcessor;
import org.jboss.weld.arc.processor.BeanProcessor.SourceOutput;

import io.smallrye.config.inject.ConfigProducer;

public class ArcAnnotationProcessor implements ResourceProcessor {

    @Inject
    BeanDeployment beanDeployment;

    @Override
    public void process(ArchiveContext archiveContext, ProcessorContext processorContext) throws Exception {
        try (BytecodeRecorder recorder = processorContext.addStaticInitTask(RuntimePriority.ARC_DEPLOYMENT)) {

            ArcDeploymentTemplate template = recorder.getRecordingProxy(ArcDeploymentTemplate.class);

            List<DotName> additionalBeanDefiningAnnotations = new ArrayList<>();
            additionalBeanDefiningAnnotations.add(DotName.createSimple("javax.servlet.annotation.WebServlet"));
            additionalBeanDefiningAnnotations.add(DotName.createSimple("javax.ws.rs.Path"));

            // TODO MP config
            beanDeployment.addAdditionalBean(ConfigProducer.class);

            // Index bean classes registered by shamrock
            Indexer indexer = new Indexer();
            Set<DotName> indexed = new HashSet<>();
            for (Class<?> beanClass : beanDeployment.getAdditionalBeans()) {
                indexBeanClass(beanClass, indexer, archiveContext.getIndex(), indexed);
            }
            CompositeIndex index = CompositeIndex.create(indexer.complete(), archiveContext.getIndex());

            // Generate beans
            BeanProcessor beanProcessor = new BeanProcessor();
            File tmpDir = File.createTempFile("arc", null);
            tmpDir.delete();
            tmpDir.mkdir();
            File generatedSourcesDirectory = new File(tmpDir, "arc/java");
            File generatedResourcesDirectory = new File(tmpDir, "arc/resources");
            List<File> javaFiles = new ArrayList<>();

            beanProcessor.process(index, additionalBeanDefiningAnnotations, new SourceOutput() {

                @Override
                public void writeClass(String className, String classPackage, byte[] data) throws IOException {
                    String location = nameToPath(classPackage);
                    File file = new File(generatedSourcesDirectory, location + "/" + className + ".java");
                    javaFiles.add(file);
                    file.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(data);
                    }
                }

                @Override
                public void writeServiceProvider(String name, byte[] data) throws IOException {
                    File file = new File(generatedResourcesDirectory + "/META-INF/services", name);
                    file.getParentFile().mkdirs();
                    try (FileOutputStream out = new FileOutputStream(file)) {
                        out.write(data);
                    }
                }
            });

            // Compile the generated classes
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);) {

                fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(generatedSourcesDirectory));

                Iterable<? extends JavaFileObject> sources = fileManager.getJavaFileObjectsFromFiles(javaFiles);
                CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, sources);
                task.call();

                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    System.out.format("[%s] %s, line %d in %s \n", diagnostic.getKind(), diagnostic.getMessage(null), diagnostic.getLineNumber(),
                            diagnostic.getSource().getName());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot close file manager", e);
            }

            // Add generated resources
            Files.walkFileTree(generatedSourcesDirectory.toPath(), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".class")) {
                        // TODO bleeee
                        String className = file.toAbsolutePath().toString().substring(generatedSourcesDirectory.getAbsolutePath().length() + 1,
                                file.toAbsolutePath().toString().length());
                        className = className.replace("/", ".");
                        className = className.substring(0, className.indexOf(".class"));
                        // TODO a better way to identify app classes
                        boolean isAppClass = true;
                        for (DotName additional : indexed) {
                            if (className.startsWith(additional.toString())) {
                                isAppClass = false;
                            }
                        }
                        System.out.println("Add " + (isAppClass ? "APP" : "FWK") + " class: " + className);
                        processorContext.addGeneratedClass(isAppClass, className, Files.readAllBytes(file.toAbsolutePath()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });

            Files.walkFileTree(generatedResourcesDirectory.toPath(), new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith("org.jboss.weld.arc.BeanProvider")) {
                        // TODO bleeee
                        processorContext.addResource("META-INF/services/" + file.getFileName().toString(), Files.readAllBytes(file.toAbsolutePath()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    return FileVisitResult.CONTINUE;
                }
            });

            ArcContainer container = template.getContainer();
            template.initBeanContainer(container);
            template.setupInjection(container);
        }
    }

    @Override
    public int getPriority() {
        return RuntimePriority.ARC_DEPLOYMENT;
    }

    private String nameToPath(String packName) {
        return packName.replace('.', '/');
    }

    private void indexBeanClass(Class<?> beanClass, Indexer indexer, IndexView shamrockIndex, Set<DotName> indexed) {
        System.out.println("Index bean class: " + beanClass);
        try (InputStream stream = ArcAnnotationProcessor.class.getClassLoader().getResourceAsStream(beanClass.getName().replace('.', '/') + ".class")) {
            ClassInfo beanInfo = indexer.index(stream);
            indexed.add(beanInfo.name());
            for (DotName annotationName : beanInfo.annotations().keySet()) {
                if (!indexed.contains(annotationName) && shamrockIndex.getClassByName(annotationName) == null) {
                    try (InputStream annotationStream = ArcAnnotationProcessor.class.getClassLoader()
                            .getResourceAsStream(annotationName.toString().replace('.', '/') + ".class")) {
                        System.out.println("Index annotation: " + annotationName);
                        indexer.index(annotationStream);
                        indexed.add(annotationName);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to index: " + beanClass);
        }
    }

}
