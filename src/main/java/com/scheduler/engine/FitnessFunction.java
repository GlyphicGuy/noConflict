package com.scheduler.engine;

import com.scheduler.model.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class FitnessFunction {

    // Penalties
    private static final int PENALTY_HARD_CONSTRAINT = 1000;
    private static final int PENALTY_SOFT_CONSTRAINT = 50;
    private static final int PENALTY_GAP_CONSTRAINT = 500; // High penalty for gaps

    /**
     * Calculates fitness score. Higher is better? Or lower penalty is better?
     * Standard GA often maximizes fitness.
     * Fitness = 1 / (1 + TotalPenalty)
     */
    public double calculateFitness(Chromosome chromosome) {
        int penalty = 0;
        List<Gene> genes = chromosome.getGenes();

        penalty += checkFacultyDoubleBooking(genes);
        penalty += checkSectionDoubleBooking(genes);
        penalty += checkFacultyWorkload(genes);
        penalty += checkSubjectDistribution(genes);
        penalty += checkMorningBalance(genes);
        penalty += checkGlobalWorkloadBalance(genes); // Spread work
        penalty += checkSectionGaps(genes); // Minimize gaps
        penalty += checkLabConsecutiveness(genes); // Labs must be consecutive pairs
        penalty += checkMaxLabsPerDay(genes); // Max 1 Lab per day per section

        return 1.0 / (1.0 + penalty);
    }

    private int checkFacultyDoubleBooking(List<Gene> genes) {
        int penalty = 0;
        Map<Faculty, Map<Slot, Integer>> facultySchedule = new HashMap<>();

        for (Gene gene : genes) {
            for (Faculty f : gene.getFaculty()) {
                facultySchedule.putIfAbsent(f, new HashMap<>());
                Map<Slot, Integer> slots = facultySchedule.get(f);
                if (slots.containsKey(gene.getSlot())) {
                    penalty += PENALTY_HARD_CONSTRAINT; // Clash found
                }
                slots.put(gene.getSlot(), 1);
            }
        }
        return penalty;
    }

    private int checkSectionDoubleBooking(List<Gene> genes) {
        int penalty = 0;
        Map<String, Set<Slot>> sectionSchedule = new HashMap<>();

        for (Gene gene : genes) {
            String sectionId = gene.getSection().getId();
            sectionSchedule.putIfAbsent(sectionId, new HashSet<>());
            if (sectionSchedule.get(sectionId).contains(gene.getSlot())) {
                penalty += PENALTY_HARD_CONSTRAINT;
            }
            sectionSchedule.get(sectionId).add(gene.getSlot());
        }
        return penalty;
    }

    private int checkFacultyWorkload(List<Gene> genes) {
        int penalty = 0;
        Map<Faculty, Double> facultyCredits = new HashMap<>();

        for (Gene gene : genes) {
            // New logic:
            // 1 Theory Slot = 1 Credit (1 Hour)
            // 1 Lab Slot = 0.5 Credit (since 2 Hours of Lab = 1 Credit)
            // Gene represents 1 Slot.

            double creditValue = gene.getSubject().isLab() ? 0.5 : 1.0;

            for (Faculty f : gene.getFaculty()) {
                facultyCredits.put(f, facultyCredits.getOrDefault(f, 0.0) + creditValue);
            }
        }

        // Check against limits
        for (Map.Entry<Faculty, Double> entry : facultyCredits.entrySet()) {
            Faculty f = entry.getKey();
            double consumed = entry.getValue();
            double allowed = f.getMaxTeachingCredits();

            if (consumed > allowed) {
                // Hard constraint: Cannot exceed max teaching credits
                // Penalty proportional to violation
                penalty += PENALTY_HARD_CONSTRAINT * (consumed - allowed);
            }
        }
        return penalty;
    }

    private int checkSubjectDistribution(List<Gene> genes) {
        // "A teacher can be assigned a maximum of two 3/4-credit subjects and one
        // 1-credit course."
        // This requires tracking unique subjects per faculty.
        int penalty = 0;
        Map<Faculty, Set<Subject>> facultySubjects = new HashMap<>();

        for (Gene gene : genes) {
            for (Faculty f : gene.getFaculty()) {
                facultySubjects.putIfAbsent(f, new HashSet<>());
                facultySubjects.get(f).add(gene.getSubject());
            }
        }

        for (Set<Subject> subjects : facultySubjects.values()) {
            long highCreditSubjects = subjects.stream().filter(s -> s.getCredits() >= 3).count();
            long lowCreditSubjects = subjects.stream().filter(s -> s.getCredits() == 1).count();

            if (highCreditSubjects > 2)
                penalty += PENALTY_HARD_CONSTRAINT;
            if (lowCreditSubjects > 1)
                penalty += PENALTY_HARD_CONSTRAINT;
        }
        return penalty;
    }

    private int checkMaxLabsPerDay(List<Gene> genes) {
        int penalty = 0;
        // Group by Section -> Day -> List of Lab Subjects
        Map<String, Map<DayOfWeek, Set<Subject>>> sectionDailyLabs = new HashMap<>();

        for (Gene gene : genes) {
            if (gene.getSubject().isLab()) {
                String sectionId = gene.getSection().getId();
                DayOfWeek day = gene.getSlot().getDay();

                sectionDailyLabs.putIfAbsent(sectionId, new HashMap<>());
                sectionDailyLabs.get(sectionId).putIfAbsent(day, new HashSet<>());
                sectionDailyLabs.get(sectionId).get(day).add(gene.getSubject());
            }
        }

        for (Map<DayOfWeek, Set<Subject>> dailyLabs : sectionDailyLabs.values()) {
            for (Set<Subject> labs : dailyLabs.values()) {
                if (labs.size() > 1) {
                    // More than 1 distinct lab subject on the same day for a section
                    // Soft constraint but highly preferred
                    penalty += PENALTY_SOFT_CONSTRAINT * 5 * (labs.size() - 1);
                }
            }
        }
        return penalty;
    }

    private int checkMorningBalance(List<Gene> genes) {
        // "Morning classes must be balanced equally among faculty"
        // Soft constraint.
        Map<Faculty, Integer> morningCounts = new HashMap<>();
        for (Gene gene : genes) {
            if (gene.getSlot().getType() == Slot.Type.MORNING) {
                for (Faculty f : gene.getFaculty()) {
                    morningCounts.put(f, morningCounts.getOrDefault(f, 0) + 1);
                }
            }
        }

        // Calculate variance or deviation from mean
        if (morningCounts.isEmpty())
            return 0;
        double avg = morningCounts.values().stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = 0;
        for (int count : morningCounts.values()) {
            variance += Math.pow(count - avg, 2);
        }
        return (int) (variance * PENALTY_SOFT_CONSTRAINT);
    }

    private int checkGlobalWorkloadBalance(List<Gene> genes) {
        // "See to it that no one faculty is over burdened try to spread out the work as
        // much as possible"
        // Calculate assigned hours for each faculty
        Map<Faculty, Integer> workload = new HashMap<>();
        for (Gene g : genes) {
            for (Faculty f : g.getFaculty()) {
                workload.put(f, workload.getOrDefault(f, 0) + 1);
            }
        }

        if (workload.isEmpty())
            return 0;

        // Calculate Mean
        double mean = workload.values().stream().mapToInt(Integer::intValue).average().orElse(0);

        // Calculate Variance (Standard Deviation^2)
        double varianceSum = 0;
        for (int load : workload.values()) {
            varianceSum += Math.pow(load - mean, 2);
        }

        // Penalty proportional to variance
        // If variance is high, work is not spread out.
        // We want to minimize variance.
        return (int) (varianceSum * PENALTY_SOFT_CONSTRAINT);
    }

    private int checkSectionGaps(List<Gene> genes) {
        int penalty = 0;
        // Group by Section
        Map<String, List<Gene>> genesBySection = genes.stream()
                .collect(Collectors.groupingBy(g -> g.getSection().getId()));

        for (List<Gene> sectionGenes : genesBySection.values()) {
            // Group by Day
            Map<DayOfWeek, List<Slot>> daySlots = new HashMap<>();

            for (Gene g : sectionGenes) {
                daySlots.putIfAbsent(g.getSlot().getDay(), new ArrayList<>());
                daySlots.get(g.getSlot().getDay()).add(g.getSlot());
            }

            for (List<Slot> slots : daySlots.values()) {
                // Sort by time
                slots.sort(Comparator.comparing(Slot::getStartTime));

                if (slots.isEmpty())
                    continue;

                // Calculate Compactness
                // We need to know the "index" of the slot in the daily sequence to calculate
                // span accurately
                // But simplified: Time Span.

                // Effective Slots calculation:
                // From First Class Start to Last Class End.
                // Count how many "schedulable blocks" are in this window.
                // Subtract actual assigned slots.
                // Result = Number of Gaps.

                Slot first = slots.get(0);
                Slot last = slots.get(slots.size() - 1);

                // Calculate time difference in minutes
                long totalTimeMinutes = java.time.Duration.between(first.getStartTime(), last.getEndTime()).toMinutes();

                // Subtract Break Times if they fall within this window?
                // Logic:
                // Morning Break: 09:50 - 10:20 (30 min)
                // Lunch Break: 13:05 - 14:00 (55 min)

                // Let's count "Free Minutes" within the span.
                // FreeMins = TotalTime - (NumClasses * 55) - (ValidBreaks)

                int numClasses = slots.size();
                long classMinutes = numClasses * 55; // Approx

                // Check if span covers break times
                boolean hasMorningBreak = first.getStartTime().isBefore(LocalTime.of(9, 50))
                        && last.getEndTime().isAfter(LocalTime.of(10, 20));

                boolean hasLunchBreak = first.getStartTime().isBefore(LocalTime.of(13, 5))
                        && last.getEndTime().isAfter(LocalTime.of(14, 0));

                long expectedBreakMinutes = 0;
                if (hasMorningBreak)
                    expectedBreakMinutes += 30;
                if (hasLunchBreak)
                    expectedBreakMinutes += 55;

                long freeMinutes = totalTimeMinutes - classMinutes - expectedBreakMinutes;

                // Optimization: Allow small buffer (e.g. 5 mins transition)
                // If freeMinutes > 50 (almost an hour), it's a gap.
                if (freeMinutes > 50) {
                    // Number of missing slots approx
                    int missingSlots = (int) (freeMinutes / 55);
                    penalty += (missingSlots * PENALTY_GAP_CONSTRAINT);
                    if (missingSlots == 0)
                        penalty += PENALTY_GAP_CONSTRAINT; // At least one gap
                }
            }
        }
        return penalty;
    }

    private int checkLabConsecutiveness(List<Gene> genes) {
        int penalty = 0;
        // Group by Section and Subject (only Labs)
        // We need to ensure that for a given Section + Subject(Lab), the assigned slots
        // are consecutive.

        Map<String, List<Gene>> labGenes = genes.stream()
                .filter(g -> g.getSubject().isLab())
                .collect(Collectors.groupingBy(g -> g.getSection().getId() + "_" + g.getSubject().getCode()));

        for (List<Gene> session : labGenes.values()) {
            if (session.size() < 2)
                continue; // If only 1 slot, trivial (or maybe wrong if lab needs 2)

            // Assuming 2 slots for a lab.
            // 1. Must be on same Day
            DayOfWeek day = session.get(0).getSlot().getDay();
            boolean sameDay = session.stream().allMatch(g -> g.getSlot().getDay().equals(day));

            if (!sameDay) {
                penalty += PENALTY_HARD_CONSTRAINT; // Split across days
                continue;
            }

            // 2. Must be Consecutive
            List<Slot> slots = session.stream().map(Gene::getSlot).sorted(Comparator.comparing(Slot::getStartTime))
                    .collect(Collectors.toList());

            for (int i = 0; i < slots.size() - 1; i++) {
                Slot current = slots.get(i);
                Slot next = slots.get(i + 1);

                // Diff between End of Current and Start of Next should be 0 (or small if just
                // minute precision)
                // Actually our slots are: 8:00-8:55, 8:55-9:50 -> Diff is 0.
                if (!current.getEndTime().equals(next.getStartTime())) {
                    // Check if it's just a break?
                    // Usually labs run THROUGH breaks or are strictly adjacent.
                    // User said: "2hrs of lab... should be consecutive only"
                    // If a break is in between, is it "consecutive"?
                    // Technically yes in terms of periods.

                    // Let's enable strict adjacency time-wise OR slot-index-wise?
                    // Given our slot definitions:
                    // 8:55 -> 8:55 ok.
                    // 9:50 -> 10:20 (Break).
                    // If Lab is 8:55-9:50 AND 10:20-11:15, is that consecutive?
                    // It is consecutive periods. But has a break.
                    // "2hrs of lab... consecutive ONLY" usually implies a block.
                    // If there is a break, students leave?
                    // Let's punish if gap > standard break (30min).

                    long gap = java.time.Duration.between(current.getEndTime(), next.getStartTime()).toMinutes();
                    if (gap > 35) { // Allow 30 min break?
                        // Check user intent "consecutive". Usually means Period 1, Period 2.
                        // If Period 2 and Period 3 are separated by break, they are still consecutive
                        // periods.
                        // Let's assume strict time adjacency is safer for "2hrs of lab".
                        // Usually labs are 8:00-10:00 or 10:00-12:00.
                        // 8:00-9:50 is 2 slots. (Consecutive).
                        // 10:20-12:10 is 2 slots. (Consecutive).
                        // 8:55-10:?? No 8:55-9:50 then 10:20...
                        // If we allow break, then fine. But if it spans LUNCH (55m), definitely bad.

                        if (gap > 40) { // Penalize if gap > small break
                            penalty += PENALTY_HARD_CONSTRAINT;
                        }
                    }
                }
            }
        }
        return penalty;
    }
}
