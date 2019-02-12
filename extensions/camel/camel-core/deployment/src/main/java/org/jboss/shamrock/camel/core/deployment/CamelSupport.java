package org.jboss.shamrock.camel.core.deployment;

import java.io.IOError;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.jboss.jandex.ClassInfo;

public final class CamelSupport {
    private CamelSupport() {
    }

    public static boolean isConcrete(ClassInfo ci) {
        return (ci.flags() & Modifier.ABSTRACT) == 0;
    }

    public static boolean isPublic(ClassInfo ci) {
        return (ci.flags() & Modifier.PUBLIC) != 0;
    }

    public static Stream<Path> safeWalk(Path p) {
        try {
            return Files.walk(p);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }
}
