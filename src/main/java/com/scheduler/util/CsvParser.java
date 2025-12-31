package com.scheduler.util;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.scheduler.model.Faculty;
import com.scheduler.model.Section;
import com.scheduler.model.Subject;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CsvParser {

    public List<Faculty> loadFaculty(String filePath) throws IOException, CsvValidationException {
        List<Faculty> facultyList = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            reader.readNext(); // Skip header
            while ((line = reader.readNext()) != null) {
                // Format: ID, Name, TotalWorkload, ResearchCredits, PreferredSubjectCodes
                if (line.length < 5)
                    continue;

                String id = line[0].trim();
                String name = line[1].trim();
                int total = Integer.parseInt(line[2].trim());
                int research = Integer.parseInt(line[3].trim());
                String[] subjects = line[4].split(";"); // Semicolon separate subject codes

                Faculty f = new Faculty(id, name, total, research);
                for (String s : subjects) {
                    f.addPreferredSubject(s.trim());
                }
                facultyList.add(f);
            }
        }
        return facultyList;
    }

    public List<Subject> loadSubjects(String filePath) throws IOException, CsvValidationException {
        List<Subject> subjects = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            reader.readNext(); // Skip header
            while ((line = reader.readNext()) != null) {
                // Name, Code, Type (Theory/Lab), Credits
                if (line.length < 4)
                    continue;

                String name = line[0].trim();
                String code = line[1].trim();
                boolean isLab = line[2].trim().equalsIgnoreCase("Lab");
                int credits = Integer.parseInt(line[3].trim());

                subjects.add(new Subject(name, code, isLab, credits));
            }
        }
        return subjects;
    }

    public List<Section> loadSections(String filePath) throws IOException, CsvValidationException {
        List<Section> sections = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] line;
            reader.readNext(); // Skip header
            while ((line = reader.readNext()) != null) {
                // ID, Name, BatchCount
                if (line.length < 3)
                    continue;

                String id = line[0].trim();
                String name = line[1].trim();
                int batches = Integer.parseInt(line[2].trim());

                sections.add(new Section(id, name, batches));
            }
        }
        return sections;
    }
}
