package com.scheduler.model;

import java.util.Objects;

public class Section {
    private String id;
    private String name; // e.g., "A", "B", "CSE-A"
    private int batchCount; // e.g., 4 batches for labs

    public Section(String id, String name, int batchCount) {
        this.id = id;
        this.name = name;
        this.batchCount = batchCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getBatchCount() {
        return batchCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Section section = (Section) o;
        return Objects.equals(id, section.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
