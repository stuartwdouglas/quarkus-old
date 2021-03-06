# Quarkus

[![Build Status](https://dev.azure.com/quarkus-ci/Quarkus/_apis/build/status/jbossas.quarkus)](https://dev.azure.com/quarkus-ci/Quarkus/_build/latest?definitionId=4)

> Quarkus is a Cloud Native, Container First framework for writing Java applications.


* **Container First**: 
Minimal footprint Java applications optimal for running in containers
* **Cloud Native**:
Embraces [12 factor architecture](https://12factor.net) in environments like Kubernetes.
* **Unify imperative and reactive**:
Brings under one programming model non blocking and imperative styles of development.
* **Standards-based**:
Based on the standards and frameworks you love and use (RESTEasy, Hibernate, Netty, Eclipse Vert.x, Apache Camel...)
* **Microservice First**:
Brings lightning fast startup time and code turn around to Java apps
* **Developer Joy**:
Development centric experience without compromise to bring your amazing apps to life in no time

_All under ONE framework._

## Getting Started

* [Documentation](http://10.0.144.40/nfs/quarkus/)
* [Getting Started](http://10.0.144.40/nfs/quarkus/getting-started-guide.html)

---

## Quarkus

Quarkus, aka the core of Quarkus, is a framework that allows you to process Java EE and Eclipse MicroProfile metadata at build time,
and use it to create low overhead jar files, as well as native images using Graal/Substrate VM.

At the moment it has the following features:

- Clean build/runtime separation of components
- Bytecode recorders to allow for the generation of bytecode without knowledge of the class file format
- An API to easily enable reflection, resources, resource bundles and lazy clazz init support in Substrate
- Support for injection into build time processors
- Support for build and runtime config through MP config
- 'Instant Start' support on Graal through the use of static init to perform boot
- A lightweight CDI implementation called Arc
- A user friendly method for generating custom bytecode called Gizmo
- Various levels of support for:
    - JAX-RS (Resteasy)
    - Servlet (Undertow) 
    - CDI (Weld/Arc)
    - Microprofile Config (SmallRye)
    - Microprofile Health Check (SmallRye)
    - Microprofile OpenAPI (SmallRye)
    - Microprofile Metrics (SmallRye)
    - Microprofile Reactive Streams Operators (SmallRye)
    - Bean Validation (Hibernate Validator)
    - Transactions (Narayana)
    - Datasources (Agroal)
    - Eclipse Vert.x
- A Maven plugin to run the build, and create native images
- A JUnit runner that can run tests, and supports IDE usage
- A JUnit runner that can test a native image produced by the Maven plugin

### How to build Quarkus

The build instructions are available in the [contribution guide](CONTRIBUTING.md).

### Architecture Overview

Quarkus runs in two distinct phases. The first phase is build time processing phase called augmentation. In this phase
we process all metadata such as annotations and descriptors and use this information to determine which services we
need to start at runtime. The output of this phase is generated bytecode that will configure and start all the
services required at runtime.

The second phase is the actual application startup. In this phase the generated bytecode is run (usually in a
completely different JVM instance, however for development or testing this may happen in the same JVM). Running this
bytecode will configure and start all the runtime services that your application requires, without needing to do any
deployment time scanning or processing.

The main advantage of this two phase approach is that your application only contains code to launch the features that
it is actually using. All the code to process annotations and parse descriptors does not end up in the final application,
resulting in lower memory usage, faster startup times and more opportunities for SubstrateVM to eliminate dead code.

For more in depth information about the architecture including information on how to write an extension see the
[Extension Authors Guide](docs/src/main/asciidoc/extension-authors-guide.adoc).


