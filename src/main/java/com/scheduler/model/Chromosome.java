package com.scheduler.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Chromosome {
    private List<Gene> genes;
    private double fitness = -1;
    private boolean isFitnessChanged = true;

    public Chromosome(List<Gene> genes) {
        this.genes = genes;
    }

    public Chromosome() {
        this.genes = new ArrayList<>();
    }

    public List<Gene> getGenes() {
        isFitnessChanged = true; // Assume if we access genes, we might modify them (naive but safe)
        return genes;
    }

    public double getFitness() {
        if (isFitnessChanged) {
            // Fitness calculation will be handled externally or updated via setter
            // But for standard GA, usually the individual calculates its own fitness
            // or the population does. Let's keep a setter.
        }
        return fitness;
    }

    public void setFitness(double fitness) {
        this.fitness = fitness;
        this.isFitnessChanged = false;
    }

    public void addGene(Gene gene) {
        this.genes.add(gene);
        this.isFitnessChanged = true;
    }

    public Chromosome deepClone() {
        // Deep clone needed for genetic operations
        List<Gene> newGenes = new ArrayList<>(this.genes.size());
        for (Gene g : this.genes) {
            // Shallow copy of gene is usually okay IF we only change slots/faculty but not
            // the gene object itself?
            // Actually in GA we might swap genes or change attributes of a gene.
            // Better to create new Gene objects if we are mutating them.
            // For now, let's just copy the list. Mutation will replace Gene objects or
            // modify them.
            // If we modify Gene objects, we need deep copy.
            newGenes.add(new Gene(g.getSlot(), g.getSection(), g.getSubject(), new ArrayList<>(g.getFaculty())));
        }
        return new Chromosome(newGenes);
    }
}
