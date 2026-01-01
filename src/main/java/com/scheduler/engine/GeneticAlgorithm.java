
package com.scheduler.engine;

import com.scheduler.model.Chromosome;
import com.scheduler.model.Gene;
import com.scheduler.model.Slot;
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

public class GeneticAlgorithm {

    private int populationSize = 50;
    private double mutationRate = 0.5; // Higher mutation for steady state
    private int tournamentSize = 5;
    private int maxGenerationsWithoutImprovement = 50;
    private double targetFitness = 1.0;

    private List<Slot> availableSlots;
    private FitnessFunction fitnessFunction;
    private ConstraintSatisfaction constraintSatisfaction;
    private List<List<Integer>> geneGroups; // Groups of indices for lab blocks

    public GeneticAlgorithm(List<Slot> availableSlots) {
        this.availableSlots = availableSlots;
        this.fitnessFunction = new FitnessFunction();
        this.constraintSatisfaction = new ConstraintSatisfaction(availableSlots);
    }

    public Chromosome evolve(List<Gene> initialGenesTemplate) {
        buildGeneGroups(initialGenesTemplate);
        List<Chromosome> population = initializePopulation(initialGenesTemplate);

        // Evaluate initial fitness
        for (Chromosome c : population) {
            c.setFitness(fitnessFunction.calculateFitness(c));
        }

        Collections.sort(population, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));

        int generations = 0;
        int stableGenerations = 0;
        double bestInternalFitness = population.get(0).getFitness();

        // Steady-State Loop
        while (population.get(0).getFitness() < targetFitness && stableGenerations < maxGenerationsWithoutImprovement) {
            generations++;

            // Selection
            Chromosome p1 = tournamentSelection(population);
            Chromosome p2 = tournamentSelection(population);

            // Crossover
            Chromosome child = uniformCrossover(p1, p2);

            // Mutation
            mutate(child);

            // Constraint Satisfaction (Repair)
            child = constraintSatisfaction.optimize(child);

            // Evaluate
            child.setFitness(fitnessFunction.calculateFitness(child));

            // Elitism / Replacement: Replace worst if child is better
            // Find worst index
            int worstIndex = populationSize - 1; // Since sorted

            if (child.getFitness() > population.get(worstIndex).getFitness()) {
                population.set(worstIndex, child);
                // Re-sort to maintain order for easy elitism/worst finding
                Collections.sort(population, (c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));
            }

            // Check convergence
            if (population.get(0).getFitness() > bestInternalFitness) {
                bestInternalFitness = population.get(0).getFitness();
                stableGenerations = 0;
            } else {
                stableGenerations++;
            }

            // Safety break
            if (generations > 5000)
                break;
        }

        return population.get(0);
    }

    private Chromosome tournamentSelection(List<Chromosome> population) {
        Chromosome best = null;
        Random rand = new Random();
        for (int i = 0; i < tournamentSize; i++) {
            Chromosome random = population.get(rand.nextInt(population.size()));
            if (best == null || random.getFitness() > best.getFitness()) {
                best = random;
            }
        }
        return best;
    }

    private Chromosome uniformCrossover(Chromosome p1, Chromosome p2) {
        List<Gene> genes1 = p1.getGenes();
        List<Gene> genes2 = p2.getGenes();
        List<Gene> childGenes = new ArrayList<>(Collections.nCopies(genes1.size(), null));
        Random rand = new Random();

        // Uniform Crossover at Group Level (to preserve Lab blocks)
        for (List<Integer> group : geneGroups) {
            boolean fromP1 = rand.nextBoolean();
            for (int index : group) {
                Gene source = fromP1 ? genes1.get(index) : genes2.get(index);
                childGenes.set(index, new Gene(source.getSlot(), source.getSection(), source.getSubject(),
                        new ArrayList<>(source.getFaculty())));
            }
        }
        return new Chromosome(childGenes);
    }

    private void mutate(Chromosome c) {
        Random rand = new Random();

        // Swap Mutation: Swap two groups within same section
        if (rand.nextDouble() < mutationRate) {
            // Pick random group
            List<Integer> group1 = geneGroups.get(rand.nextInt(geneGroups.size()));
            Gene g1 = c.getGenes().get(group1.get(0));

            // Find another group in same section
            List<List<Integer>> sameSectionGroups = geneGroups.stream()
                    .filter(g -> c.getGenes().get(g.get(0)).getSection().getId().equals(g1.getSection().getId()))
                    .collect(Collectors.toList());

            if (sameSectionGroups.size() > 1) {
                List<Integer> group2 = sameSectionGroups.get(rand.nextInt(sameSectionGroups.size()));

                // Swap slots logic: Need to ensure structure validity if sizes differ?
                // Simple swap if sizes same, else re-assign random
                if (group1.size() == group2.size()) {
                    for (int i = 0; i < group1.size(); i++) {
                        Slot s1 = c.getGenes().get(group1.get(i)).getSlot();
                        Slot s2 = c.getGenes().get(group2.get(i)).getSlot();
                        c.getGenes().get(group1.get(i)).setSlot(s2);
                        c.getGenes().get(group2.get(i)).setSlot(s1);
                    }
                }
            }
        }

        // Guided / Random Re-roll Mutation
        if (rand.nextDouble() < mutationRate) {
            List<Integer> group = geneGroups.get(rand.nextInt(geneGroups.size()));
            boolean isLab = c.getGenes().get(group.get(0)).getSubject().isLab();
            List<Slot> newSlots = pickRandomSlotsForGroup(group.size(), isLab);
            for (int k = 0; k < group.size(); k++) {
                c.getGenes().get(group.get(k)).setSlot(newSlots.get(k));
            }
        }
    }

    private List<Chromosome> initializePopulation(List<Gene> template) {
        List<Chromosome> pop = new ArrayList<>();
        // Seed with a "Smart" greedy individual?
        // For now, random.
        for (int i = 0; i < populationSize; i++) {
            List<Gene> newGenes = new ArrayList<>(Collections.nCopies(template.size(), null));
            for (List<Integer> group : geneGroups) {
                Gene templateGene = template.get(group.get(0));
                List<Slot> assignedSlots = pickRandomSlotsForGroup(group.size(), templateGene.getSubject().isLab());
                for (int k = 0; k < group.size(); k++) {
                    int originalIndex = group.get(k);
                    Gene g = template.get(originalIndex);
                    newGenes.set(originalIndex, new Gene(assignedSlots.get(k), g.getSection(), g.getSubject(),
                            new ArrayList<>(g.getFaculty())));
                }
            }
            pop.add(new Chromosome(newGenes));
        }
        return pop;
    }

    private void buildGeneGroups(List<Gene> template) {
        geneGroups = new ArrayList<>();
        int n = template.size();
        boolean[] visited = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (visited[i])
                continue;
            List<Integer> group = new ArrayList<>();
            group.add(i);
            visited[i] = true;
            Gene current = template.get(i);
            if (current.getSubject().isLab()) {
                for (int j = i + 1; j < n; j++) {
                    if (visited[j])
                        break;
                    Gene next = template.get(j);
                    if (next.getSection().equals(current.getSection()) &&
                            next.getSubject().equals(current.getSubject())) {
                        group.add(j);
                        visited[j] = true;
                    } else {
                        break;
                    }
                }
            }
            geneGroups.add(group);
        }
    }

    private List<Slot> pickRandomSlotsForGroup(int size, boolean isLab) {
        Random rand = new Random();
        if (size == 1)
            return Collections.singletonList(availableSlots.get(rand.nextInt(availableSlots.size())));

        List<Integer> validStartIndices = new ArrayList<>();
        for (int i = 0; i <= availableSlots.size() - size; i++) {
            if (isValidBlock(i, size, isLab)) {
                validStartIndices.add(i);
            }
        }
        if (validStartIndices.isEmpty())
            return Collections.singletonList(availableSlots.get(rand.nextInt(availableSlots.size())));

        int startIndex = validStartIndices.get(rand.nextInt(validStartIndices.size()));
        List<Slot> result = new ArrayList<>();
        for (int k = 0; k < size; k++) {
            result.add(availableSlots.get(startIndex + k));
        }
        return result;
    }

    private boolean isValidBlock(int startIndex, int size, boolean isLab) {
        Slot first = availableSlots.get(startIndex);
        for (int k = 1; k < size; k++) {
            Slot prev = availableSlots.get(startIndex + k - 1);
            Slot curr = availableSlots.get(startIndex + k);
            if (curr.getDay() != first.getDay())
                return false;
            // Strict Adjacency
            if (!prev.getEndTime().equals(curr.getStartTime()))
                return false;
        }
        return true;
    }
}
