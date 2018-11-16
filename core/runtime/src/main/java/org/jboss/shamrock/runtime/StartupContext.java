package org.jboss.shamrock.runtime;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;

public class StartupContext implements Closeable {

    private static final Logger log = Logger.getLogger(StartupContext.class);

    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Long> timing = new HashMap<>();

    private final List<Closeable> resources = new ArrayList<>();

    public void putValue(String name, Object value) {
        values.put(name, value);
    }

    public Object getValue(String name) {
        return values.get(name);
    }

    public void addCloseable(Closeable resource) {
        resources.add(resource);
    }

    public void addTiming(String name, long start, long end) {
        long time = end - start;
        Long existing = timing.get(name);
        if (existing == null) {
            timing.put(name, time);
        } else {
            timing.put(name, existing);
        }
    }

    public void printTiming() {
        StringBuilder sb = new StringBuilder("Startup timing report\n");
        List<Map.Entry<String, Long>> vals = new ArrayList<>(timing.entrySet());
        Collections.sort(vals, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> o1, Map.Entry<String, Long> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });
        for (Map.Entry<String, Long> i : vals) {
            sb.append((i.getKey() + ":\t" + i.getValue() + "ms\n"));
        }
        log.info(sb.toString());
    }


    @Override
    public void close() {
        List<Closeable> toClose = new ArrayList<>(resources);
        Collections.reverse(toClose);
        for (Closeable r : toClose) {
            try {
                r.close();
            } catch (IOException e) {

                e.printStackTrace();
            }
        }
        resources.clear();
    }
}
