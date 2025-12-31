package com.scheduler.engine;

import com.scheduler.model.*;
import org.junit.jupiter.api.Test;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FitnessFunctionTest {

    @Test
    public void testDoubleBookingPenalty() {
        FitnessFunction ff = new FitnessFunction();

        Faculty f1 = new Faculty("F1", "Alice", 12, 0);
        Subject s1 = new Subject("Math", "M1", false, 3);
        Section sec1 = new Section("S1", "A", 0);

        Slot slot1 = new Slot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), Slot.Type.MORNING);

        // Two genes, same slot, same faculty -> Clash!
        Gene g1 = new Gene(slot1, sec1, s1, Collections.singletonList(f1));
        Gene g2 = new Gene(slot1, sec1, s1, Collections.singletonList(f1));

        Chromosome c = new Chromosome(Arrays.asList(g1, g2));

        double fitness = ff.calculateFitness(c);
        assertTrue(fitness < 0.5, "Fitness should be low due to double booking penalty");
    }

    @Test
    public void testNoClashHighFitness() {
        FitnessFunction ff = new FitnessFunction();

        Faculty f1 = new Faculty("F1", "Alice", 12, 0);
        Subject s1 = new Subject("Math", "M1", false, 3);
        Section sec1 = new Section("S1", "A", 0);

        Slot slot1 = new Slot(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0), Slot.Type.MORNING);
        Slot slot2 = new Slot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0), Slot.Type.MORNING);

        Gene g1 = new Gene(slot1, sec1, s1, Collections.singletonList(f1));
        Gene g2 = new Gene(slot2, sec1, s1, Collections.singletonList(f1));

        Chromosome c = new Chromosome(Arrays.asList(g1, g2));

        double fitness = ff.calculateFitness(c);
        double maxFitness = 1.0; // Ideal
        // Soft constraints might reduce it slightly, but should be high
        assertTrue(fitness > 0.8, "Fitness should be high for valid schedule. Got: " + fitness);
    }
}
