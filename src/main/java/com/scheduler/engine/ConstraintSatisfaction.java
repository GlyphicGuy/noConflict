package com.scheduler.engine;

import com.scheduler.model.*;
import java.util.*;

public class ConstraintSatisfaction {

    private final List<Slot> allSlots;
    private final FitnessFunction fitnessFunction;
    private static final int TABU_TENURE = 10;
    private static final int MAX_ITERATIONS = 50;

    public ConstraintSatisfaction(List<Slot> allSlots) {
        this.allSlots = allSlots;
        this.fitnessFunction = new FitnessFunction();
    }

    /**
     * Tries to repair a chromosome using Tabu Search if it has hard violations.
     */
    public Chromosome optimize(Chromosome chromosome) {
        if (fitnessFunction.calculateHardViolations(chromosome) == 0) {
            return chromosome;
        }

        // Clone to avoid modifying original if we want to be safe,
        // though in GA we usually just modify the child.
        // Assuming we can modify valid genes in place.
        Chromosome current = chromosome.deepClone();
        Chromosome best = current.deepClone();
        int bestViolations = fitnessFunction.calculateHardViolations(best);

        // Tabu List: Stores "GeneIndex|SlotToString" hash or similar
        Queue<String> tabuList = new LinkedList<>();
        Set<String> tabuSet = new HashSet<>();

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            if (bestViolations == 0)
                break;

            // Generate Neighborhood: focus on genes causing conflicts
            // For simplicity in this wrapper, we pick a random gene involved in conflict
            // and try swapping its slot.

            // Identifying conflicting genes is complex without feedback from Fitness.
            // Let's iterate all genes, find one with conflict?
            // Or just random swap of ANY gene?
            // "Heuristic Local Search ... swap slots locally".

            // Let's try to swap a random gene's slot to a random new slot.

            Chromosome candidate = current.deepClone();
            int geneIdx = new Random().nextInt(candidate.getGenes().size());
            Gene g = candidate.getGenes().get(geneIdx);

            Slot oldSlot = g.getSlot();
            Slot newSlot = allSlots.get(new Random().nextInt(allSlots.size()));

            // Move move = new Move(geneIdx, newSlot);
            String moveKey = geneIdx + "_" + newSlot.toString();

            // Apply move
            g.setSlot(newSlot);

            // Eval
            int violations = fitnessFunction.calculateHardViolations(candidate);

            // Tabu Check
            boolean isTabu = tabuSet.contains(moveKey);
            // Aspiration: if better than best, ignore Tabu
            if (!isTabu || violations < bestViolations) {
                current = candidate;
                // Update Tabu
                tabuList.add(moveKey);
                tabuSet.add(moveKey);
                if (tabuList.size() > TABU_TENURE) {
                    tabuSet.remove(tabuList.poll());
                }

                if (violations < bestViolations) {
                    best = candidate.deepClone();
                    bestViolations = violations;
                }
            }
        }

        return best;
    }
}
