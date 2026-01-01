package com.scheduler.util;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.scheduler.model.Chromosome;
import com.scheduler.model.Gene;
import com.scheduler.model.Slot;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataExporter {

    public void exportToExcel(Chromosome chromosome, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            // Group genes by Section
            Map<String, List<Gene>> genesBySection = chromosome.getGenes().stream()
                    .collect(Collectors.groupingBy(g -> g.getSection().getName()));

            for (Map.Entry<String, List<Gene>> entry : genesBySection.entrySet()) {
                String sectionName = entry.getKey();
                List<Gene> genes = entry.getValue();

                Sheet sheet = workbook.createSheet(sectionName);

                // Header Row (Slots)
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Day");

                // Collect unique slots sorted by time
                List<Slot> sortedSlots = genes.stream()
                        .map(Gene::getSlot)
                        .distinct()
                        .sorted(Comparator.comparing(Slot::getDay).thenComparing(Slot::getStartTime))
                        .collect(Collectors.toList());

                // Extract unique Start Times for columns
                List<String> timeHeaders = sortedSlots.stream()
                        .map(s -> s.getStartTime().toString())
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());

                int colNum = 1;
                for (String time : timeHeaders) {
                    header.createCell(colNum++).setCellValue(time);
                }

                String[] days = { "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY" };
                int rowNum = 1;
                for (String day : days) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(day);

                    for (int i = 0; i < timeHeaders.size(); i++) {
                        String time = timeHeaders.get(i);
                        // Find gene for this day + time
                        Gene gene = genes.stream()
                                .filter(g -> g.getSlot().getDay().toString().equals(day)
                                        && g.getSlot().getStartTime().toString().equals(time))
                                .findFirst()
                                .orElse(null);

                        if (gene != null) {
                            String cellValue = gene.getSubject().getName() + "\n(" + gene.getFaculty().get(0).getName()
                                    + ")";
                            if (gene.getFaculty().size() > 1) {
                                cellValue += " + " + (gene.getFaculty().size() - 1) + " others";
                            }
                            row.createCell(i + 1).setCellValue(cellValue);
                        }
                    }
                }

                // Auto size columns
                for (int i = 0; i <= timeHeaders.size(); i++)
                    sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
            }
        }
    }

    public void exportToPdf(Chromosome chromosome, String filePath) {
        try {
            // Landscape mode for better width
            Document document = new Document(com.lowagie.text.PageSize.A4.rotate());
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            // Font styles
            com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 16,
                    com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10,
                    com.lowagie.text.Font.BOLD);
            com.lowagie.text.Font cellFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8,
                    com.lowagie.text.Font.NORMAL);

            // Highlight Style for Break/Lunch
            com.lowagie.text.Font breakFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8,
                    com.lowagie.text.Font.BOLDITALIC, java.awt.Color.GRAY);

            Map<String, List<Gene>> genesBySection = chromosome.getGenes().stream()
                    .collect(Collectors.groupingBy(g -> g.getSection().getName()));

            // Sort sections
            List<String> sortedSections = genesBySection.keySet().stream().sorted().collect(Collectors.toList());

            for (String sectionName : sortedSections) {
                Paragraph title = new Paragraph("Timetable for Section: " + sectionName, titleFont);
                title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                document.add(title);
                document.add(new Paragraph(" ")); // Spacer

                // Define Columns including breaks
                String[] timeHeaders = {
                        "08:00\n08:55", "08:55\n09:50", "BREAK\n09:50-10:20",
                        "10:20\n11:15", "11:15\n12:10", "12:10\n13:05", "LUNCH\n13:05-14:00",
                        "14:00\n14:55", "14:55\n15:50", "15:50\n16:45", "16:45\n17:40"
                };

                // Map StartTime string to Column Index (0-based)
                // Note: Breaks are at index 2 and 6
                Map<String, Integer> timeToIndex = new java.util.HashMap<>();
                timeToIndex.put("08:00", 0);
                timeToIndex.put("08:55", 1);
                // Break is 2
                timeToIndex.put("10:20", 3);
                timeToIndex.put("11:15", 4);
                timeToIndex.put("12:10", 5);
                // Lunch is 6
                timeToIndex.put("14:00", 7);
                timeToIndex.put("14:55", 8);
                timeToIndex.put("15:50", 9);
                timeToIndex.put("16:45", 10);

                PdfPTable table = new PdfPTable(timeHeaders.length + 1); // +1 for Day column
                table.setWidthPercentage(100);

                // Add Headers
                table.addCell(new com.lowagie.text.Phrase("Day/Time", headerFont));
                for (String t : timeHeaders) {
                    table.addCell(new com.lowagie.text.Phrase(t, headerFont));
                }

                String[] days = { "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY" };

                for (String day : days) {
                    // Day Cell
                    table.addCell(new com.lowagie.text.Phrase(day, headerFont));

                    // Cells for each time slot
                    for (int i = 0; i < timeHeaders.length; i++) {
                        // Check if this column is a Break or Lunch
                        if (i == 2) {
                            table.addCell(new com.lowagie.text.Phrase("BREAK", breakFont));
                            continue;
                        }
                        if (i == 6) {
                            table.addCell(new com.lowagie.text.Phrase("LUNCH", breakFont));
                            continue;
                        }

                        int currentSlotIdx = i;
                        // Find gene
                        Gene gene = genesBySection.get(sectionName).stream()
                                .filter(g -> g.getSlot().getDay().toString().equals(day))
                                .filter(g -> {
                                    Integer idx = timeToIndex.get(g.getSlot().getStartTime().toString());
                                    return idx != null && idx == currentSlotIdx;
                                })
                                .findFirst()
                                .orElse(null);

                        if (gene != null) {
                            String cellText = gene.getSubject().getName() + "\n(" + gene.getFaculty().get(0).getName();
                            if (gene.getFaculty().size() > 1) {
                                cellText += " +" + (gene.getFaculty().size() - 1);
                            }
                            cellText += ")";
                            table.addCell(new com.lowagie.text.Phrase(cellText, cellFont));
                        } else {
                            table.addCell(""); // Empty cell
                        }
                    }
                }

                document.add(table);
                document.newPage();
            }

            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
