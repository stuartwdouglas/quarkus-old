package org.jboss.shamrock;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;

public abstract class ShamrockTemplate {
    protected final Configuration cfg;

    public ShamrockTemplate() {
        cfg = new Configuration(Configuration.VERSION_2_3_23);
        cfg.setTemplateLoader(new ClassTemplateLoader(ShamrockTemplate.class, "/"));
    }

    public abstract String getName();

    public abstract void generate(final File projectRoot, Map<String, Object> parameters) throws IOException;
}
