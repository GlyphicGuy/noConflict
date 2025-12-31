
package com.scheduler.engine;

import com.scheduler.model.Chromosome;
import com.scheduler.model.Gene;
import com.scheduler.model.Slot;
import java.time.Duration;
import java.util.*;

public class GeneticAlgorithm {

    private int populationSize = 50;
    private double mutationRate = 0.05;
    private int crossoverRate = 80; // percent
    private int elitismCount = 2;
    private int maxGenerations = 1000;

    private List<Slot> availableSlots;
    private FitnessFunction fitnessFunction;

    // Groups of indices in the chromosome that must be scheduled together
    private List<List<Integer>> geneGroups;

    public GeneticAlgorithm(List<Slot> availableSlots) {
        this.availableSlots = availableSlots;
        this.fitnessFunction = new FitnessFunction();
    }

    public Chromosome evolve(List<Gene> initialGenesTemplate) {
        // 1. Analyze template to build groups
        buildGeneGroups(initialGenesTemplate);

        // 2. Initialize
        List<Chromosome> population = initializePopulation(initialGenesTemplate);
        int generation = 0;

        while (generation < maxGenerations) {
            // Calculate Fitness
            for (Chromosome c : population) {
                c.setFitness(fitnessFunction.calculateFitness(c));
            }

            // Sort by fitness (descending)
            population.sort((c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));

            // Check termination
            if (population.get(0).getFitness() > 0.999) {
                break;
            }

            List<Chromosome> newPopulation = new ArrayList<>();

            // Elitism
            for (int i = 0; i < elitismCount; i++) {
                newPopulation.add(population.get(i)); // Keep best
            }

            // Crossover and Mutation
            Random rand = new Random();
            while (newPopulation.size() < populationSize) {
                Chromosome p1 = population.get(rand.nextInt(population.size() / 2));
                Chromosome p2 = population.get(rand.nextInt(population.size() / 2));

                Chromosome child = crossover(p1, p2);
                mutate(child);
                newPopulation.add(child);
            }

            population = newPopulation;
            generation++;
        }

        // Return best
        population.sort((c1, c2) -> Double.compare(c2.getFitness(), c1.getFitness()));
        return population.get(0);
    }

    private void buildGeneGroups(List<Gene> template) {
        geneGroups = new ArrayList<>();
        int n = template.size();
        boolean[] visited = new boolean[n];

        // We assume createGenes produces consecutive genes for a subject allocation.
        // e.g., if Lab needs 2 hours, it created Gene A, Gene B consecutively.
        // We verify this by checking Subject, Section and Type.

        for (int i = 0; i < n; i++) {
            if (visited[i])
                continue;

            List<Integer> group = new ArrayList<>();
            group.add(i);
            visited[i] = true;

            Gene current = template.get(i);
            // If it's a lab, check next genes
            if (current.getSubject().isLab()) {
                // How many slots?
                // We don't have explicit "slotsNeeded" here easily, but we know how many were
                // generated.
                // Or we can rely on grouping by Section+Subject in sequence.

                // Look ahead for same Section & Subject
                for (int j = i + 1; j < n; j++) {
                    if (visited[j])
                        break;
                    Gene next = template.get(j);
                    if (next.getSection().equals(current.getSection()) &&
                            next.getSubject().equals(current.getSubject())) {
                        group.add(j);
                        visited[j] = true;
                    } else {
                        break; // Sequence broken or different subject
                    }
                }
            }
            geneGroups.add(group);
        }
    }

    private List<Chromosome> initializePopulation(List<Gene> template) {
        List<Chromosome> pop = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            // Create blank genes list with null slots first to preserve order
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

    // Picking slots logic
    private List<Slot> pickRandomSlotsForGroup(int size, boolean isLab) {
        Random rand = new Random();
        if (size == 1) {
            // Just one slot
            return Collections.singletonList(availableSlots.get(rand.nextInt(availableSlots.size())));
        }

        // Find valid "blocks" of 'size' length
        // A block is valid if all slots are on same day and consecutive
        // AND if isLab=true, they must be STRICTLY adjacent (endTime == startTime).

        List<Integer> validStartIndices = new ArrayList<>();

        for (int i = 0; i <= availableSlots.size() - size; i++) {
            if (isValidBlock(i, size, isLab)) {
                validStartIndices.add(i);
            }
        }

        if (validStartIndices.isEmpty()) {
            // Fallback (should not happen if slots defined correctly)
            // Just pick random
            return Collections.singletonList(availableSlots.get(rand.nextInt(availableSlots.size())));
        }

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

            // Same Day check
            if (curr.getDay() != first.getDay())
                return false;

            // Strict Adjacency for Labs
            // User requirement: "2 consecutive periods ALWAYS".
            // This means EndTime of Prev == StartTime of Curr.
            if (isLab) {
                if (!prev.getEndTime().equals(curr.getStartTime())) {
                    return false;
                }
            } else {
                // For Theory blocks (if any exist > 1), maybe loose adjacency is fine?
                // But we usually schedule theory as singletons.
                // If we had a 3-hour theory workshop, we'd probably want adjacency too.
                // let's stick to strict for any block just to be safe/neat.
                if (!prev.getEndTime().equals(curr.getStartTime())) {
                    return false;
                }
            }
        }
        return true;
    }

    private Chromosome crossover(Chromosome p1, Chromosome p2) {
        // Crossover by Groups to preserve blocks
        List<Gene> childGenes = new ArrayList<>(Collections.nCopies(p1.getGenes().size(), null));
        List<Gene> genes1 = p1.getGenes();
        List<Gene> genes2 = p2.getGenes();

        Random rand = new Random();
        for (List<Integer> group : geneGroups) {
            boolean fromP1 = rand.nextBoolean();
            for (int index : group) {
                Gene source = fromP1 ? genes1.get(index) : genes2.get(index);
                childGenes.set(index, cloneGene(source));
            }
        }
        return new Chromosome(childGenes);
    }

    private void mutate(Chromosome c) {
        Random rand = new Random();
        List<Gene> genes = c.getGenes();

        // Mutate groups
        for (List<Integer> group : geneGroups) {
            if (rand.nextDouble() < mutationRate) {
                // Re-roll slots for this group
                boolean isLab = genes.get(group.get(0)).getSubject().isLab();
                List<Slot> newSlots = pickRandomSlotsForGroup(group.size(), isLab);

                for (int k = 0; k < group.size(); k++) {
                    int index = group.get(k);
                    genes.get(index).setSlot(newSlots.get(k));
                }
            }
        }
    }

    private Gene cloneGene(Gene g) {
        return new Gene(g.getSlot(), g.getSection(), g.getSubject(), new ArrayList<>(g.getFaculty()));
    }
}
