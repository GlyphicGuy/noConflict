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

                // Actually, a timetable usually has Days as Rows and Times as Cols (or vice
                // versa).
                // Let's do Days (Rows) x Times (Cols).
                // Or Slot-based list?
                // Grid format:
                // | 08:00 | 09:00 | ...
                // Mon | Subj | ...
                // Tue | ...

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
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            document.open();

            Map<String, List<Gene>> genesBySection = chromosome.getGenes().stream()
                    .collect(Collectors.groupingBy(g -> g.getSection().getName()));

            for (Map.Entry<String, List<Gene>> entry : genesBySection.entrySet()) {
                String sectionName = entry.getKey();
                document.add(new Paragraph("Timetable for Section: " + sectionName));
                document.add(new Paragraph(" ")); // Spacer

                PdfPTable table = new PdfPTable(6); // Day + 5 slots? Need dynamic cols
                // Simplified List View for PDF (or complex grid if time permits)
                // Let's do a simple list: Day | Time | Subject | Faculty

                table.addCell("Day");
                table.addCell("Time");
                table.addCell("Subject");
                table.addCell("Faculty");

                List<Gene> sortedGenes = entry.getValue().stream()
                        .sorted(Comparator.comparing((Gene g) -> g.getSlot().getDay())
                                .thenComparing(g -> g.getSlot().getStartTime()))
                        .collect(Collectors.toList());

                for (Gene g : sortedGenes) {
                    table.addCell(g.getSlot().getDay().toString());
                    table.addCell(g.getSlot().getStartTime() + " - " + g.getSlot().getEndTime());
                    table.addCell(g.getSubject().getName());
                    table.addCell(g.getFaculty().toString());
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
