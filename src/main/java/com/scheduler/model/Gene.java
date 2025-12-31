package com.scheduler.model;

import java.util.List;

public class Gene {
    private Slot slot;
    private Section section;
    private Subject subject;
    private List<Faculty> faculty; // List because Labs have multiple teachers (1 per batch)
    private String roomId; // Optional for now

    public Gene(Slot slot, Section section, Subject subject, List<Faculty> faculty) {
        this.slot = slot;
        this.section = section;
        this.subject = subject;
        this.faculty = faculty;
    }

    public Slot getSlot() {
        return slot;
    }

    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public Section getSection() {
        return section;
    }

    public Subject getSubject() {
        return subject;
    }

    public List<Faculty> getFaculty() {
        return faculty;
    }

    public void setFaculty(List<Faculty> faculty) {
        this.faculty = faculty;
    }

    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s", slot, section.getName(), subject.getCode(), faculty);
    }
}
