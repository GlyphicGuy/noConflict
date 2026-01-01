package com.scheduler.engine;

import com.scheduler.model.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class FitnessFunction {

    /**
     * Fitness = 1 / (1 + HardViolations * 100 + SoftViolations)
     */
    public double calculateFitness(Chromosome chromosome) {
        int hardViolations = 0;
        int softViolations = 0;
        List<Gene> genes = chromosome.getGenes();

        // --- HARD VIOLATIONS ---
        hardViolations += checkFacultyDoubleBooking(genes);
        hardViolations += checkSectionDoubleBooking(genes);
        hardViolations += checkFacultyWorkload(genes); // Credit limit
        hardViolations += checkLabConsecutiveness(genes); // Labs must be pairs

        // --- SOFT VIOLATIONS ---
        softViolations += checkFacultyClumping(genes); // 3-hour gaps
        softViolations += checkMorningBalance(genes); // Std Dev of 8 AM
        softViolations += checkStudentFatigue(genes); // >3 consecutive hours
        softViolations += checkSectionGaps(genes); // General gaps (Student Friendly)
        softViolations += checkSubjectDistribution(genes); // 2 high credit/1 low credit rule

        return 1.0 / (1.0 + (hardViolations * 100) + softViolations);
    }

    public int calculateHardViolations(Chromosome chromosome) {
        List<Gene> genes = chromosome.getGenes();
        return checkFacultyDoubleBooking(genes) +
                checkSectionDoubleBooking(genes) +
                checkFacultyWorkload(genes) +
                checkLabConsecutiveness(genes);
    }

    // --- HEURISTICS ---

    private int checkFacultyClumping(List<Gene> genes) {
        // "Penalize any schedule where a faculty member has a 3-hour gap between two
        // 1-hour classes."
        int penalty = 0;
        Map<Faculty, List<Slot>> facultySlots = new HashMap<>();

        for (Gene g : genes) {
            for (Faculty f : g.getFaculty()) {
                facultySlots.putIfAbsent(f, new ArrayList<>());
                facultySlots.get(f).add(g.getSlot());
            }
        }

        for (List<Slot> slots : facultySlots.values()) {
            Map<DayOfWeek, List<Slot>> daySlots = slots.stream().collect(Collectors.groupingBy(Slot::getDay));
            for (List<Slot> ds : daySlots.values()) {
                ds.sort(Comparator.comparing(Slot::getStartTime));
                for (int i = 0; i < ds.size() - 1; i++) {
                    long gapMinutes = java.time.Duration.between(ds.get(i).getEndTime(), ds.get(i + 1).getStartTime())
                            .toMinutes();
                    // 3 hours gap approx 180 mins.
                    // Lets say if gap is around 160-200 mins.
                    // Or "Last class ended 10:00, Next starts 13:00" -> 3 hours.
                    if (gapMinutes >= 160) {
                        penalty += 1;
                    }
                }
            }
        }
        return penalty;
    }

    private int checkStudentFatigue(List<Gene> genes) {
        // "More than 3 consecutive hours for a student section."
        int penalty = 0;
        Map<String, List<Slot>> sectionSlots = new HashMap<>();
        for (Gene g : genes) {
            sectionSlots.putIfAbsent(g.getSection().getId(), new ArrayList<>());
            sectionSlots.get(g.getSection().getId()).add(g.getSlot());
        }

        for (List<Slot> slots : sectionSlots.values()) {
            Map<DayOfWeek, List<Slot>> daySlots = slots.stream().collect(Collectors.groupingBy(Slot::getDay));
            for (List<Slot> ds : daySlots.values()) {
                ds.sort(Comparator.comparing(Slot::getStartTime));
                int consecutive = 1;
                for (int i = 0; i < ds.size() - 1; i++) {
                    // Check strictly adjacent (ignoring break if we consider break resets fatigue?
                    // Usually break DOES reset fatigue. So consecutive means NO breaks.
                    // But user said "3 consecutive hours".

                    // If End(i) == Start(i+1) -> Consecutive
                    if (ds.get(i).getEndTime().equals(ds.get(i + 1).getStartTime())) {
                        consecutive++;
                    } else {
                        // Gap/Break found
                        if (consecutive > 3)
                            penalty += (consecutive - 3);
                        consecutive = 1;
                    }
                }
                if (consecutive > 3)
                    penalty += (consecutive - 3);
            }
        }
        return penalty;
    }

    private int checkMorningBalance(List<Gene> genes) {
        // "Assign a higher fitness score to schedules where the 8 AM slots are
        // distributed standard-deviation-wise across the faculty list."
        // We want LOW StdDev.
        Map<Faculty, Integer> morningCounts = new HashMap<>();
        for (Gene gene : genes) {
            if (gene.getSlot().getStartTime().getHour() == 8) { // 8 AM slot
                for (Faculty f : gene.getFaculty()) {
                    morningCounts.put(f, morningCounts.getOrDefault(f, 0) + 1);
                }
            }
        }

        if (morningCounts.isEmpty())
            return 0;
        double avg = morningCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = 0;
        for (int count : morningCounts.values()) {
            variance += Math.pow(count - avg, 2);
        }
        // Return variance as penalty (minimize variance)
        return (int) variance;
    }

    // --- EXISTING CONSTRAINTS RE-MAPPED ---

    private int checkFacultyDoubleBooking(List<Gene> genes) {
        int violations = 0;
        Map<Faculty, Set<Slot>> facultySchedule = new HashMap<>();
        for (Gene gene : genes) {
            for (Faculty f : gene.getFaculty()) {
                facultySchedule.putIfAbsent(f, new HashSet<>());
                if (facultySchedule.get(f).contains(gene.getSlot())) {
                    violations++;
                } else {
                    facultySchedule.get(f).add(gene.getSlot());
                }
            }
        }
        return violations;
    }

    private int checkSectionDoubleBooking(List<Gene> genes) {
        int violations = 0;
        Map<String, Set<Slot>> sectionSchedule = new HashMap<>();
        for (Gene gene : genes) {
            String sid = gene.getSection().getId();
            sectionSchedule.putIfAbsent(sid, new HashSet<>());
            if (sectionSchedule.get(sid).contains(gene.getSlot())) {
                violations++;
            } else {
                sectionSchedule.get(sid).add(gene.getSlot());
            }
        }
        return violations;
    }

    private int checkFacultyWorkload(List<Gene> genes) {
        int violations = 0;
        Map<Faculty, Double> facultyCredits = new HashMap<>();
        for (Gene gene : genes) {
            double creditValue = gene.getSubject().isLab() ? 0.5 : 1.0;
            for (Faculty f : gene.getFaculty()) {
                facultyCredits.put(f, facultyCredits.getOrDefault(f, 0.0) + creditValue);
            }
        }
        for (Map.Entry<Faculty, Double> entry : facultyCredits.entrySet()) {
            if (entry.getValue() > entry.getKey().getMaxTeachingCredits()) {
                violations++;
            }
        }
        return violations;
    }

    private int checkLabConsecutiveness(List<Gene> genes) {
        int violations = 0;
        Map<String, List<Gene>> labGenes = genes.stream()
                .filter(g -> g.getSubject().isLab())
                .collect(Collectors.groupingBy(g -> g.getSection().getId() + "_" + g.getSubject().getCode()));

        for (List<Gene> session : labGenes.values()) {
            if (session.size() < 2)
                continue;
            DayOfWeek day = session.get(0).getSlot().getDay();
            boolean sameDay = session.stream().allMatch(g -> g.getSlot().getDay().equals(day));
            if (!sameDay) {
                violations++;
                continue;
            }
            List<Slot> slots = session.stream().map(Gene::getSlot).sorted(Comparator.comparing(Slot::getStartTime))
                    .collect(Collectors.toList());
            for (int i = 0; i < slots.size() - 1; i++) {
                if (!slots.get(i).getEndTime().equals(slots.get(i + 1).getStartTime())) {
                    violations++;
                }
            }
        }
        return violations;
    }

    private int checkSectionGaps(List<Gene> genes) {
        // Reuse optimized strict student friendly logic but return as soft violation
        // count
        int violations = 0;
        Map<String, Map<DayOfWeek, List<Slot>>> map = new HashMap<>();
        for (Gene g : genes) {
            map.putIfAbsent(g.getSection().getId(), new HashMap<>());
            map.get(g.getSection().getId()).putIfAbsent(g.getSlot().getDay(), new ArrayList<>());
            map.get(g.getSection().getId()).get(g.getSlot().getDay()).add(g.getSlot());
        }

        for (Map<DayOfWeek, List<Slot>> days : map.values()) {
            for (List<Slot> slots : days.values()) {
                slots.sort(Comparator.comparing(Slot::getStartTime));
                if (slots.isEmpty())
                    continue;
                Slot first = slots.get(0);
                Slot last = slots.get(slots.size() - 1);
                long totalTime = java.time.Duration.between(first.getStartTime(), last.getEndTime()).toMinutes();

                // Breaks
                boolean hasMorningBreak = first.getStartTime().isBefore(LocalTime.of(9, 50))
                        && last.getEndTime().isAfter(LocalTime.of(10, 20));
                boolean hasLunchBreak = first.getStartTime().isBefore(LocalTime.of(13, 5))
                        && last.getEndTime().isAfter(LocalTime.of(14, 0));
                long breaks = (hasMorningBreak ? 30 : 0) + (hasLunchBreak ? 55 : 0);

                long classTime = slots.size() * 55;
                long freeTime = totalTime - classTime - breaks;

                if (freeTime > 10) {
                    violations += (freeTime / 55) + 1;
                }
            }
        }
        return violations;
    }

    private int checkSubjectDistribution(List<Gene> genes) {
        int violations = 0;
        Map<Faculty, Set<Subject>> facultySubjects = new HashMap<>();
        for (Gene gene : genes) {
            for (Faculty f : gene.getFaculty()) {
                facultySubjects.putIfAbsent(f, new HashSet<>());
                facultySubjects.get(f).add(gene.getSubject());
            }
        }
        for (Set<Subject> subjects : facultySubjects.values()) {
            long high = subjects.stream().filter(s -> s.getCredits() >= 3).count();
            long low = subjects.stream().filter(s -> s.getCredits() == 1).count();
            if (high > 2)
                violations++;
            if (low > 1)
                violations++;
        }
        return violations;
    }
}
