package org.jboss.shamrock.deployment;

import java.nio.file.Path;

import org.jboss.builder.item.SimpleBuildItem;

public class ArchiveRoot extends SimpleBuildItem {

    private final Path path;

    public ArchiveRoot(Path path) {
        this.path = path;
    }

    public Path getPath() {
        return path;
    }
}
