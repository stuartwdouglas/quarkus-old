package org.jboss.shamrock.maven.runner;

import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.EmbeddedConfigurator;
import org.jboss.logmanager.formatters.ColorPatternFormatter;
import org.jboss.logmanager.handlers.ConsoleHandler;

public class LoggingConfig implements EmbeddedConfigurator {

    @Override
    public Level getMinimumLevelOf(String loggerName) {
        return Level.FINE;
    }

    @Override
    public Level getLevelOf(String loggerName) {
        return Level.FINE;
    }

    @Override
    public Handler[] getHandlersOf(String loggerName) {
        return new Handler[]{new ConsoleHandler(new ColorPatternFormatter("%d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{1.}] (%t) %s%e%n"))};
    }
}
