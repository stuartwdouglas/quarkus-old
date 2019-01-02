package org.jboss.shamrock.runtime.configuration;

import java.util.NoSuchElementException;

import org.wildfly.common.Assert;

/**
 */
public final class NameIterator {
    private final String name;
    private String currentString;
    private int start = -1;
    private int end = -1;

    public NameIterator(final String name) {
        Assert.checkNotNullParam("name", name);
        this.name = name;
    }

    public int getStart() {
        final int start = this.start;
        if (start < 0) throw new NoSuchElementException();
        return start;
    }

    public int getEnd() {
        final int end = this.end;
        if (end < 0) throw new NoSuchElementException();
        return end;
    }

    public int getCurrentLength() {
        return getEnd() - start;
    }

    public boolean segmentEquals(String other) {
        return segmentEquals(other, 0, other.length());
    }

    public boolean segmentEquals(String other, int offs, int len) {
        return getCurrentLength() == len && name.regionMatches(start, other, offs, len);
    }

    public boolean hasNext() {
        return end + 1 < name.length();
    }

    public boolean hasPrevious() {
        return start - 1 >= 0;
    }

    public void next() {
        if (! hasNext()) throw new NoSuchElementException();
        currentString = null;
        start = end + 1;
        int idx = name.indexOf('.', start);
        end = idx == -1 ? name.length() : idx;
    }

    public void previous() {
        if (! hasPrevious()) throw new NoSuchElementException();
        currentString = null;
        end = start - 1;
        start = name.lastIndexOf('.', end) + 1;
    }

    public String currentString() {
        final String currentString = this.currentString;
        if (currentString == null) return this.currentString = name.substring(getStart(), end);
        return currentString;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        // generated code relies on this behavior
        return getName();
    }
}
