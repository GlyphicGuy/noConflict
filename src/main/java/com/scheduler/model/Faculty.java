package com.scheduler.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Faculty {
    private String id;
    private String name;
    private int totalWorkloadCredits;
    private int researchCredits;
    private List<String> preferredSubjectCodes; // List of Subject Codes they can teach

    public Faculty(String id, String name, int totalWorkloadCredits, int researchCredits) {
        this.id = id;
        this.name = name;
        this.totalWorkloadCredits = totalWorkloadCredits;
        this.researchCredits = researchCredits;
        this.preferredSubjectCodes = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalWorkloadCredits() {
        return totalWorkloadCredits;
    }

    public int getResearchCredits() {
        return researchCredits;
    }

    // Derived property for the Scheduler
    public int getMaxTeachingCredits() {
        return totalWorkloadCredits - researchCredits;
    }

    public List<String> getPreferredSubjectCodes() {
        return preferredSubjectCodes;
    }

    public void addPreferredSubject(String subjectCode) {
        this.preferredSubjectCodes.add(subjectCode);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Faculty faculty = (Faculty) o;
        return Objects.equals(id, faculty.id);
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
