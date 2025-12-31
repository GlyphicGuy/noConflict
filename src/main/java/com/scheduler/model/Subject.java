package com.scheduler.model;

import java.util.Objects;

public class Subject {
    private String name;
    private String code;
    private boolean isLab;
    private int credits; // 3 credits = 3 hours for theory, 3 credits = ? hours for lab?
                         // User said: "3 credits = 3 hours/week" for Theory.
                         // User said: "1 Credit = 1 Theory Hour OR 2 Lab Hours."
                         // So a 1 credit Lab = 2 hours.
                         // We should store 'hoursRequired' derived from credits and type.

    public Subject(String name, String code, boolean isLab, int credits) {
        this.name = name;
        this.code = code;
        this.isLab = isLab;
        this.credits = credits;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public boolean isLab() {
        return isLab;
    }

    public int getCredits() {
        return credits;
    }

    public int getHoursRequired() {
        if (isLab) {
            return credits * 2; // 1 Credit = 2 Lab Hours
        } else {
            return credits; // 1 Credit = 1 Theory Hour
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Subject subject = (Subject) o;
        return Objects.equals(code, subject.code);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code);
    }

    @Override
    public String toString() {
        return name + " (" + (isLab ? "Lab" : "Theory") + ")";
    }
}
