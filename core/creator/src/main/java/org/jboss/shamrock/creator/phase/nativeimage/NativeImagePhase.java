/*
 * Copyright 2018 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.shamrock.creator.phase.nativeimage;

import io.smallrye.config.SmallRyeConfigProviderResolver;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;
import org.jboss.shamrock.creator.AppCreationContext;
import org.jboss.shamrock.creator.AppCreationPhase;
import org.jboss.shamrock.creator.AppCreatorException;
import org.jboss.shamrock.creator.phase.augment.AugmentOutcome;
import org.jboss.shamrock.creator.phase.runnerjar.RunnerJarOutcome;
import org.jboss.shamrock.creator.util.IoUtils;

/**
 * The outcome of this phase is a native image
 *
 * @author Alexey Loubyansky
 */
public class NativeImagePhase implements AppCreationPhase {

  private static final Logger log = Logger.getLogger(NativeImagePhase.class);

  private static final String GRAALVM_HOME = "GRAALVM_HOME";

  private Path outputDir;

  private boolean reportErrorsAtRuntime;

  private boolean debugSymbols;

  private boolean debugBuildProcess;

  private boolean cleanupServer;

  private boolean enableHttpUrlHandler;

  private boolean enableHttpsUrlHandler;

  private boolean enableAllSecurityServices;

  private boolean enableRetainedHeapReporting;

  private boolean enableCodeSizeReporting;

  private boolean enableIsolates;

  private String graalvmHome;

  private boolean enableServer;

  private boolean enableJni;

  private boolean autoServiceLoaderRegistration;

  private boolean dumpProxies;

  private String nativeImageXmx;

  private boolean dockerBuild;

  private boolean enableVMInspection;

  private boolean fullStackTraces;

  private boolean disableReports;

  private List<String> additionalBuildArgs;

  public NativeImagePhase setOutputDir(Path outputDir) {
    this.outputDir = outputDir;
    return this;
  }

  public NativeImagePhase setReportErrorsAtRuntime(boolean reportErrorsAtRuntime) {
    this.reportErrorsAtRuntime = reportErrorsAtRuntime;
    return this;
  }

  public NativeImagePhase setDebugSymbols(boolean debugSymbols) {
    this.debugSymbols = debugSymbols;
    return this;
  }

  public NativeImagePhase setDebugBuildProcess(boolean debugBuildProcess) {
    this.debugBuildProcess = debugBuildProcess;
    return this;
  }

  public NativeImagePhase setCleanupServer(boolean cleanupServer) {
    this.cleanupServer = cleanupServer;
    return this;
  }

  public NativeImagePhase setEnableHttpUrlHandler(boolean enableHttpUrlHandler) {
    this.enableHttpUrlHandler = enableHttpUrlHandler;
    return this;
  }

  public NativeImagePhase setEnableHttpsUrlHandler(boolean enableHttpsUrlHandler) {
    this.enableHttpsUrlHandler = enableHttpsUrlHandler;
    return this;
  }

  public NativeImagePhase setEnableAllSecurityServices(boolean enableAllSecurityServices) {
    this.enableAllSecurityServices = enableAllSecurityServices;
    return this;
  }

  public NativeImagePhase setEnableRetainedHeapReporting(boolean enableRetainedHeapReporting) {
    this.enableRetainedHeapReporting = enableRetainedHeapReporting;
    return this;
  }

  public NativeImagePhase setEnableCodeSizeReporting(boolean enableCodeSizeReporting) {
    this.enableCodeSizeReporting = enableCodeSizeReporting;
    return this;
  }

  public NativeImagePhase setEnableIsolates(boolean enableIsolates) {
    this.enableIsolates = enableIsolates;
    return this;
  }

  public NativeImagePhase setGraalvmHome(String graalvmHome) {
    this.graalvmHome = graalvmHome;
    return this;
  }

  public NativeImagePhase setEnableServer(boolean enableServer) {
    this.enableServer = enableServer;
    return this;
  }

  public NativeImagePhase setEnableJni(boolean enableJni) {
    this.enableJni = enableJni;
    return this;
  }

  public NativeImagePhase setAutoServiceLoaderRegistration(boolean autoServiceLoaderRegistration) {
    this.autoServiceLoaderRegistration = autoServiceLoaderRegistration;
    return this;
  }

  public NativeImagePhase setDumpProxies(boolean dumpProxies) {
    this.dumpProxies = dumpProxies;
    return this;
  }

  public NativeImagePhase setNativeImageXmx(String nativeImageXmx) {
    this.nativeImageXmx = nativeImageXmx;
    return this;
  }

  public NativeImagePhase setDockerBuild(boolean dockerBuild) {
    this.dockerBuild = dockerBuild;
    return this;
  }

  public NativeImagePhase setEnableVMInspection(boolean enableVMInspection) {
    this.enableVMInspection = enableVMInspection;
    return this;
  }

  public NativeImagePhase setFullStackTraces(boolean fullStackTraces) {
    this.fullStackTraces = fullStackTraces;
    return this;
  }

  public NativeImagePhase setDisableReports(boolean disableReports) {
    this.disableReports = disableReports;
    return this;
  }

  public NativeImagePhase setAdditionalBuildArgs(List<String> additionalBuildArgs) {
    this.additionalBuildArgs = additionalBuildArgs;
    return this;
  }

  @Override
  public void process(AppCreationContext ctx) throws AppCreatorException {

    outputDir = outputDir == null ? ctx.getWorkPath() : IoUtils.mkdirs(outputDir);

    final RunnerJarOutcome runnerJarOutcome = ctx.getOutcome(RunnerJarOutcome.class);
    Path runnerJar = runnerJarOutcome.getRunnerJar();
    boolean runnerJarCopied = false;
    // this trick is here because docker needs the jar in the project dir
    if (!runnerJar.getParent().equals(outputDir)) {
      try {
        runnerJar = IoUtils.copy(runnerJar, outputDir.resolve(runnerJar.getFileName()));
      } catch (IOException e) {
        throw new AppCreatorException("Failed to copy the runnable jar to the output dir", e);
      }
      runnerJarCopied = true;
    }
    final String runnerJarName = runnerJar.getFileName().toString();

    Path outputLibDir = outputDir.resolve(runnerJarOutcome.getLibDir().getFileName());
    if (Files.exists(outputLibDir)) {
      outputLibDir = null;
    } else {
      try {
        IoUtils.copy(runnerJarOutcome.getLibDir(), outputLibDir);
      } catch (IOException e) {
        throw new AppCreatorException(
            "Failed to copy the runnable jar and the lib to the docker project dir", e);
      }
    }

    final Config config = SmallRyeConfigProviderResolver.instance().getConfig();

    boolean vmVersionOutOfDate = isThisGraalVMRCObsolete();

    HashMap<String, String> env = new HashMap<>(System.getenv());
    List<String> nativeImage;
    if (dockerBuild) {

      // E.g. "/usr/bin/docker run -v {{PROJECT_DIR}}:/project --rm protean/graalvm-native-image"
      nativeImage = new ArrayList<>();
      // TODO: use an 'official' image
      Collections.addAll(
          nativeImage,
          "docker",
          "run",
          "-v",
          outputDir.toAbsolutePath() + ":/project:z",
          "--rm",
          "swd847/centos-graal-native-image");
    } else {
      String graalvmHome = this.graalvmHome;
      if (graalvmHome != null) {
        env.put(GRAALVM_HOME, graalvmHome);
      } else {
        graalvmHome = env.get(GRAALVM_HOME);
        if (graalvmHome == null) {
          throw new AppCreatorException("GRAALVM_HOME was not set");
        }
      }
      nativeImage =
          Collections.singletonList(
              graalvmHome + File.separator + "bin" + File.separator + "native-image");
    }

    try {
      List<String> command = new ArrayList<>();
      command.addAll(nativeImage);
      if (cleanupServer) {
        List<String> cleanup = new ArrayList<>(nativeImage);
        cleanup.add("--server-shutdown");
        ProcessBuilder pb = new ProcessBuilder(cleanup.toArray(new String[0]));
        pb.directory(outputDir.toFile());
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        process.waitFor();
      }
      // TODO this is a temp hack
      final Path propsFile =
          ctx.getOutcome(AugmentOutcome.class)
              .getAppClassesDir()
              .resolve("native-image.properties");
      if (Files.exists(propsFile)) {
        final Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(propsFile, StandardCharsets.UTF_8)) {
          properties.load(reader);
        }
        for (String propertyName : properties.stringPropertyNames()) {
          final String propertyValue = properties.getProperty(propertyName);
          // todo maybe just -D is better than -J-D in this case
          if (propertyValue == null) {
            command.add("-J-D" + propertyName);
          } else {
            command.add("-J-D" + propertyName + "=" + propertyValue);
          }
        }
      }
      if (config != null) {
        if (config.getOptionalValue("shamrock.ssl.native", Boolean.class).orElse(false)) {
          enableHttpsUrlHandler = true;
          enableJni = true;
          enableAllSecurityServices = true;
        }
      }
      if (additionalBuildArgs != null) {
        additionalBuildArgs.forEach(command::add);
      }
      command.add(
          "-H:InitialCollectionPolicy=com.oracle.svm.core.genscavenge.CollectionPolicy$BySpaceAndTime"); // the default collection policy results in full GC's 50% of the time
      command.add("-jar");
      command.add(runnerJarName);
      // https://github.com/oracle/graal/issues/660
      command.add("-J-Djava.util.concurrent.ForkJoinPool.common.parallelism=1");
      if (reportErrorsAtRuntime) {
        command.add("-H:+ReportUnsupportedElementsAtRuntime");
      }
      if (debugSymbols) {
        command.add("-g");
      }
      if (debugBuildProcess) {
        command.add("-J-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y");
      }
      if (!disableReports) {
        command.add("-H:+PrintAnalysisCallTree");
      }
      if (dumpProxies) {
        command.add("-Dsun.misc.ProxyGenerator.saveGeneratedFiles=true");
        if (enableServer) {
          log.warn(
              "Options dumpProxies and enableServer are both enabled: this will get the proxies dumped in an unknown external working directory");
        }
      }
      if (nativeImageXmx != null) {
        command.add("-J-Xmx" + nativeImageXmx);
      }
      List<String> protocols = new ArrayList<>(2);
      if (enableHttpUrlHandler) {
        protocols.add("http");
      }
      if (enableHttpsUrlHandler) {
        protocols.add("https");
      }
      if (!protocols.isEmpty()) {
        command.add("-H:EnableURLProtocols=" + String.join(",", protocols));
      }
      if (enableAllSecurityServices) {
        command.add("--enable-all-security-services");
      }
      if (enableRetainedHeapReporting) {
        command.add("-H:+PrintRetainedHeapHistogram");
      }
      if (enableCodeSizeReporting) {
        command.add("-H:+PrintCodeSizeReport");
      }
      if (!enableIsolates) {
        command.add("-H:-SpawnIsolates");
      }
      if (enableJni) {
        command.add("-H:+JNI");
      } else {
        command.add("-H:-JNI");
      }
      if (!enableServer) {
        command.add("--no-server");
      }
      if (enableVMInspection) {
        command.add("-H:+AllowVMInspection");
      }
      if (autoServiceLoaderRegistration) {
        command.add("-H:+UseServiceLoaderFeature");
        // When enabling, at least print what exactly is being added:
        command.add("-H:+TraceServiceLoaderFeature");
      } else {
        command.add("-H:-UseServiceLoaderFeature");
      }
      if (fullStackTraces) {
        command.add("-H:+StackTrace");
      } else {
        command.add("-H:-StackTrace");
      }

      log.info(command.stream().collect(Collectors.joining(" ")));
      CountDownLatch errorReportLatch = new CountDownLatch(1);

      ProcessBuilder pb = new ProcessBuilder(command.toArray(new String[0]));
      pb.directory(outputDir.toFile());
      pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
      pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);

      Process process = pb.start();
      new Thread(
              new ErrorReplacingProcessReader(
                  process.getErrorStream(),
                  outputDir.resolve("reports").toFile(),
                  errorReportLatch))
          .start();
      errorReportLatch.await();
      if (process.waitFor() != 0) {
        throw new RuntimeException("Image generation failed");
      }
      System.setProperty(
          "native.image.path", runnerJarName.substring(0, runnerJarName.lastIndexOf('.')));

    } catch (Exception e) {
      throw new AppCreatorException("Failed to build native image", e);
    } finally {
      if (runnerJarCopied) {
        IoUtils.recursiveDelete(runnerJar);
      }
      if (outputLibDir != null) {
        IoUtils.recursiveDelete(outputLibDir);
      }
    }
  }

  // FIXME remove after transition period
  private boolean isThisGraalVMRCObsolete() {
    final String vmName = System.getProperty("java.vm.name");
    log.info("Running Shamrock native-image plugin on " + vmName);
    if (vmName.contains("-rc9") || vmName.contains("-rc10")) {
      log.error("Out of date RC build of GraalVM detected! Please upgrade to RC11");
      return true;
    }
    return false;
  }
}
