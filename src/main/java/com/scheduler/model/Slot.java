package com.scheduler.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public class Slot {
    private DayOfWeek day;
    private LocalTime startTime;
    private LocalTime endTime;
    private Type type; // MORNING, MID_MORNING, AFTERNOON

    public enum Type {
        MORNING, MID_MORNING, AFTERNOON
    }

    public Slot(DayOfWeek day, LocalTime startTime, LocalTime endTime, Type type) {
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
    }

    public DayOfWeek getDay() {
        return day;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Type getType() {
        return type;
    }

    public long getDurationMinutes() {
        return java.time.Duration.between(startTime, endTime).toMinutes();
    }

    @Override
    public String toString() {
        return day + " " + startTime + "-" + endTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Slot slot = (Slot) o;
        return day == slot.day && Objects.equals(startTime, slot.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(day, startTime);
    }
}
