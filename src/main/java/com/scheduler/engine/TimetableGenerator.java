package com.scheduler.engine;

import com.scheduler.model.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class TimetableGenerator {

    public Chromosome generateTimetable(List<Faculty> facultyList, List<Subject> subjectList,
            List<Section> sectionList) {
        // 1. Define Slots (Hardcoded for now as per requirements)
        List<Slot> slots = createSlots();

        // 2. Create Genes (The classes to be scheduled)
        List<Gene> templateGenes = createGenes(facultyList, subjectList, sectionList);

        // 3. Validate Inputs (Simple check)
        if (templateGenes.isEmpty()) {
            throw new IllegalStateException("No classes to schedule. Check inputs.");
        }

        // 4. Run GA
        GeneticAlgorithm ga = new GeneticAlgorithm(slots);
        return ga.evolve(templateGenes);
    }

    private List<Slot> createSlots() {
        List<Slot> slots = new ArrayList<>();
        // Mon-Sat (Saturday half day)
        DayOfWeek[] days = { DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY };

        for (DayOfWeek day : days) {
            // Morning: 08:00 – 09:50 (2 slots).
            slots.add(new Slot(day, LocalTime.of(8, 0), LocalTime.of(8, 55), Slot.Type.MORNING));
            slots.add(new Slot(day, LocalTime.of(8, 55), LocalTime.of(9, 50), Slot.Type.MORNING));

            // Mid-Morning: 10:20 – 13:05 (3 slots).
            slots.add(new Slot(day, LocalTime.of(10, 20), LocalTime.of(11, 15), Slot.Type.MID_MORNING));
            slots.add(new Slot(day, LocalTime.of(11, 15), LocalTime.of(12, 10), Slot.Type.MID_MORNING));
            slots.add(new Slot(day, LocalTime.of(12, 10), LocalTime.of(13, 5), Slot.Type.MID_MORNING));

            // Afternoon: 14:00 – 17:40 (4 slots). NOT for Saturday.
            if (day != DayOfWeek.SATURDAY) {
                slots.add(new Slot(day, LocalTime.of(14, 0), LocalTime.of(14, 55), Slot.Type.AFTERNOON));
                slots.add(new Slot(day, LocalTime.of(14, 55), LocalTime.of(15, 50), Slot.Type.AFTERNOON));
                slots.add(new Slot(day, LocalTime.of(15, 50), LocalTime.of(16, 45), Slot.Type.AFTERNOON));
                slots.add(new Slot(day, LocalTime.of(16, 45), LocalTime.of(17, 40), Slot.Type.AFTERNOON));
            }
        }
        return slots;
    }

    private List<Gene> createGenes(List<Faculty> facultyList, List<Subject> subjectList, List<Section> sectionList) {
        List<Gene> genes = new ArrayList<>();

        // Track assigned load in CREDITS (Double)
        Map<Faculty, Double> currentLoad = new HashMap<>();
        for (Faculty f : facultyList)
            currentLoad.put(f, 0.0);

        // Map to store Theory assignments: Section.id_Subject.code -> AssignedFaculty
        Map<String, Faculty> sectionTheoryAssignments = new HashMap<>();

        // PASS 1: Assign Theory Subjects First
        for (Section section : sectionList) {
            for (Subject subject : subjectList) {
                if (subject.isLab())
                    continue;

                int slotsNeeded = subject.getHoursRequired();
                double cost = subject.getCredits(); // 1 Credit = 1 Hour for Theory

                List<Faculty> eligibleFaculty = facultyList.stream()
                        .filter(f -> f.getPreferredSubjectCodes().contains(subject.getCode()))
                        .collect(Collectors.toList());

                if (eligibleFaculty.isEmpty()) {
                    throw new IllegalStateException("No faculty found for " + subject.getName());
                }

                // Least Loaded Heuristic
                eligibleFaculty.sort(Comparator.comparingDouble(f -> currentLoad.getOrDefault(f, 0.0)));

                Faculty assigned = null;
                for (Faculty f : eligibleFaculty) {
                    if (currentLoad.get(f) + cost <= f.getMaxTeachingCredits()) {
                        assigned = f;
                        break;
                    }
                }

                if (assigned == null) {
                    throw new IllegalStateException("All eligible faculty for " + subject.getName() + " ("
                            + section.getName() + ") are overloaded!");
                }

                currentLoad.put(assigned, currentLoad.get(assigned) + cost);
                sectionTheoryAssignments.put(section.getId() + "_" + subject.getCode(), assigned);

                for (int i = 0; i < slotsNeeded; i++) {
                    genes.add(new Gene(null, section, subject, Collections.singletonList(assigned)));
                }
            }
        }

        // PASS 2: Assign Lab Subjects
        for (Section section : sectionList) {
            for (Subject subject : subjectList) {
                if (!subject.isLab())
                    continue;

                int slotsNeeded = subject.getHoursRequired();
                double cost = subject.getCredits(); // 1 Credit = 2 Hours for Lab

                List<Faculty> eligibleFaculty = facultyList.stream()
                        .filter(f -> f.getPreferredSubjectCodes().contains(subject.getCode()))
                        .collect(Collectors.toList());

                if (eligibleFaculty.isEmpty()) {
                    throw new IllegalStateException("No faculty found for " + subject.getName());
                }

                List<Faculty> assigned = new ArrayList<>();

                // 1. Identify Mandatory Theory Professor
                String potentialTheoryCode = subject.getCode().replace("_L", "");
                String key = section.getId() + "_" + potentialTheoryCode;
                Faculty theoryProf = sectionTheoryAssignments.get(key);

                if (theoryProf != null) {
                    // Check if they are eligible AND have space
                    // We prioritize the "Must be in lab" rule, but strict workload limit rules all.
                    // If overloaded, we CANNOT assign.
                    if (currentLoad.get(theoryProf) + cost <= theoryProf.getMaxTeachingCredits()) {
                        if (!assigned.contains(theoryProf)) { // Avoid duplicates if already in list?
                            assigned.add(theoryProf);
                            currentLoad.put(theoryProf, currentLoad.get(theoryProf) + cost);
                        }
                    } else {
                        System.err.println("Warning: Theory Prof " + theoryProf.getName()
                                + " is overloaded. Skipping for Lab " + subject.getName());
                    }
                }

                // 2. Determine required faculty count
                // User Request: Unix and Web Dev labs need only 1 faculty. Others need 4.
                int requiredFacultyCount = 4;
                if (subject.getCode().equalsIgnoreCase("UNIX_L") || subject.getCode().equalsIgnoreCase("WEB_L")) {
                    requiredFacultyCount = 1;
                }

                // 3. Fill remaining spots
                eligibleFaculty.sort(Comparator.comparingDouble(f -> currentLoad.getOrDefault(f, 0.0)));

                for (Faculty f : eligibleFaculty) {
                    if (assigned.size() >= requiredFacultyCount)
                        break;
                    if (!assigned.contains(f)) {
                        if (currentLoad.get(f) + cost <= f.getMaxTeachingCredits()) {
                            assigned.add(f);
                            currentLoad.put(f, currentLoad.get(f) + cost);
                        }
                    }
                }

                if (assigned.size() < requiredFacultyCount) {
                    throw new IllegalStateException("Not enough available faculty for Lab " + subject.getName() + " ("
                            + section.getName() + "). Needed " + requiredFacultyCount + ", got " + assigned.size());
                }

                // Create Genes
                for (int i = 0; i < slotsNeeded; i++) {
                    genes.add(new Gene(null, section, subject, assigned));
                }
            }
        }
        return genes;
    }
}
