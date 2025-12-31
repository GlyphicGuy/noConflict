package com.scheduler.ui;

import com.scheduler.engine.TimetableGenerator;
import com.scheduler.model.*;
import com.scheduler.util.CsvParser;
import com.scheduler.util.DataExporter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DashboardController {

    @FXML
    private VBox timetableContainer;
    @FXML
    private VBox workloadContainer;
    @FXML
    private Button btnGenerate;
    @FXML
    private Button btnExportExcel;
    @FXML
    private Button btnExportPdf;
    @FXML
    private Label statusLabel;
    @FXML
    private TabPane mainTabPane;

    private List<Faculty> facultyList = new ArrayList<>();
    private List<Subject> subjectList = new ArrayList<>();
    private List<Section> sectionList = new ArrayList<>();

    private Chromosome currentTimetable;

    // File Choosers
    public void importFaculty() {
        File file = chooseFile("Import Faculty CSV");
        if (file != null) {
            try {
                facultyList = new CsvParser().loadFaculty(file.getAbsolutePath());
                updateStatus("Loaded " + facultyList.size() + " faculty members.");
                updateWorkloadView();
            } catch (Exception e) {
                showError("Error loading faculty", e);
            }
        }
    }

    public void importSubjects() {
        File file = chooseFile("Import Subjects CSV");
        if (file != null) {
            try {
                subjectList = new CsvParser().loadSubjects(file.getAbsolutePath());
                updateStatus("Loaded " + subjectList.size() + " subjects.");
            } catch (Exception e) {
                showError("Error loading subjects", e);
            }
        }
    }

    public void importSections() {
        File file = chooseFile("Import Sections CSV");
        if (file != null) {
            try {
                sectionList = new CsvParser().loadSections(file.getAbsolutePath());
                updateStatus("Loaded " + sectionList.size() + " sections.");
            } catch (Exception e) {
                showError("Error loading sections", e);
            }
        }
    }

    @FXML
    public void generateTimetable() {
        if (facultyList.isEmpty() || subjectList.isEmpty() || sectionList.isEmpty()) {
            showError("Missing Data", new Exception("Please import all CSV files first."));
            return;
        }

        updateStatus("Generating timetable... please wait.");

        new Thread(() -> {
            try {
                TimetableGenerator generator = new TimetableGenerator();
                currentTimetable = generator.generateTimetable(facultyList, subjectList, sectionList);

                Platform.runLater(() -> {
                    renderTimetable(currentTimetable);
                    updateWorkloadView(); // Update with actual assigned hours
                    btnExportExcel.setDisable(false);
                    btnExportPdf.setDisable(false);
                    updateStatus(
                            "Generation Complete! Fitness: " + String.format("%.4f", currentTimetable.getFitness()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Generation Failed", e));
            }
        }).start();
    }

    @FXML
    public void exportExcel() {
        if (currentTimetable == null)
            return;
        File file = saveFile("Export Excel", "*.xlsx");
        if (file != null) {
            try {
                new DataExporter().exportToExcel(currentTimetable, file.getAbsolutePath());
                updateStatus("Exported to Excel successfully.");
            } catch (Exception e) {
                showError("Export Failed", e);
            }
        }
    }

    @FXML
    public void exportPdf() {
        if (currentTimetable == null)
            return;
        File file = saveFile("Export PDF", "*.pdf");
        if (file != null) {
            try {
                new DataExporter().exportToPdf(currentTimetable, file.getAbsolutePath());
                updateStatus("Exported to PDF successfully.");
            } catch (Exception e) {
                showError("Export Failed", e);
            }
        }
    }

    private void renderTimetable(Chromosome chromosome) {
        timetableContainer.getChildren().clear();

        Map<String, List<Gene>> genesBySection = chromosome.getGenes().stream()
                .collect(Collectors.groupingBy(g -> g.getSection().getName()));

        // Defined headers including breaks
        // 08:00, 08:55, Breaks, etc.
        // We will build a static grid model:
        // Col 1: Day
        // Col 2: 08:00-08:55
        // Col 3: 08:55-09:50
        // Col 4: BREAK (09:50-10:20)
        // Col 5: 10:20-11:15
        // Col 6: 11:15-12:10
        // Col 7: 12:10-13:05
        // Col 8: LUNCH (13:05-14:00)
        // Col 9: 14:00-14:55
        // Col 10: 14:55-15:50
        // Col 11: 15:50-16:45
        // Col 12: 16:45-17:40

        // Slot mapping to column index
        Map<String, Integer> timeToCol = new HashMap<>();
        timeToCol.put("08:00", 1);
        timeToCol.put("08:55", 2);
        // Break is 3
        timeToCol.put("10:20", 4);
        timeToCol.put("11:15", 5);
        timeToCol.put("12:10", 6);
        // Lunch is 7
        timeToCol.put("14:00", 8);
        timeToCol.put("14:55", 9);
        timeToCol.put("15:50", 10);
        timeToCol.put("16:45", 11);

        List<String> sortedSections = new ArrayList<>(genesBySection.keySet());
        Collections.sort(sortedSections);

        for (String sectionName : sortedSections) {
            VBox sectionBox = new VBox(5);
            sectionBox.getStyleClass().add("card");
            Label lbl = new Label("Section: " + sectionName);
            lbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #00bcd4;");

            GridPane grid = new GridPane();
            grid.getStyleClass().add("timetable-grid");

            // Render Headers
            String[] headers = {
                    "Time", "08:00\n08:55", "08:55\n09:50", "BREAK\n09:50-10:20",
                    "10:20\n11:15", "11:15\n12:10", "12:10\n13:05", "LUNCH\n13:05-14:00",
                    "14:00\n14:55", "14:55\n15:50", "15:50\n16:45", "16:45\n17:40"
            };

            for (int c = 0; c < headers.length; c++) {
                Label h = new Label(headers[c]);
                h.getStyleClass().add("timetable-header");
                h.setPrefWidth(90);
                h.setMinHeight(35);
                grid.add(h, c, 0);
            }

            String[] days = { "MON", "TUE", "WED", "THU", "FRI", "SAT" };
            String[] fullDayNames = { "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY" };

            for (int r = 0; r < days.length; r++) {
                String dayShort = days[r];
                String dayFull = fullDayNames[r];

                // Day Label
                Label d = new Label(dayShort);
                d.getStyleClass().add("timetable-day");
                d.setPrefWidth(60);
                d.setMinHeight(50); // Consistent row height
                grid.add(d, 0, r + 1);

                // Fill Breaks/Lunch for this row (Cols 3 and 7)
                addBreakCell(grid, 3, r + 1, "BREAK");
                addBreakCell(grid, 7, r + 1, "LUNCH");

                // Fill Slots
                List<Gene> dayGenes = genesBySection.get(sectionName).stream()
                        .filter(g -> g.getSlot().getDay().toString().equals(dayFull))
                        .collect(Collectors.toList());

                for (Gene g : dayGenes) {
                    Integer col = timeToCol.get(g.getSlot().getStartTime().toString());
                    if (col != null) {
                        VBox cell = new VBox(2);
                        cell.getStyleClass().add("timetable-cell");

                        if (g.getSubject().isLab()) {
                            cell.getStyleClass().add("cell-lab");
                        } else {
                            cell.getStyleClass().add("cell-theory");
                        }

                        Label subj = new Label(g.getSubject().getName());
                        subj.getStyleClass().add("text-subject");
                        subj.setWrapText(true);

                        String facName = g.getFaculty().get(0).getName();
                        if (g.getFaculty().size() > 1) {
                            facName += " (+" + (g.getFaculty().size() - 1) + ")";
                        }
                        Label fac = new Label(facName);
                        fac.getStyleClass().add("text-faculty");

                        // Tooltip
                        if (g.getFaculty().size() > 1) {
                            String allNames = g.getFaculty().stream().map(Faculty::getName)
                                    .collect(Collectors.joining("\n"));
                            Tooltip tip = new Tooltip(allNames);
                            tip.setShowDelay(javafx.util.Duration.millis(100));
                            Tooltip.install(cell, tip);
                            Tooltip.install(fac, tip);
                            Tooltip.install(subj, tip);
                        }

                        cell.getChildren().addAll(subj, fac);
                        cell.setPrefWidth(90);
                        cell.setPrefHeight(50);
                        grid.add(cell, col, r + 1);
                    }
                }
            }

            sectionBox.getChildren().addAll(lbl, grid);
            timetableContainer.getChildren().add(sectionBox);
        }
    }

    private void addBreakCell(GridPane grid, int col, int row, String text) {
        Label l = new Label(text);
        l.getStyleClass().add("cell-break");
        l.setPrefSize(90, 50);
        grid.add(l, col, row);
    }

    private void updateWorkloadView() {
        workloadContainer.getChildren().clear();

        // Calculate current loads if generated
        Map<Faculty, Integer> currentLoads = new HashMap<>();
        if (currentTimetable != null) {
            for (Gene g : currentTimetable.getGenes()) {
                // Approximate 1 slot = 1 hour load
                for (Faculty f : g.getFaculty()) {
                    currentLoads.put(f, currentLoads.getOrDefault(f, 0) + 1);
                }
            }
        }

        for (Faculty f : facultyList) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card");

            Label name = new Label(f.getName());
            name.setPrefWidth(150);

            int max = f.getMaxTeachingCredits() * 2; // Approx max hours?
            // Or just use total workload credits?
            // User said "1 Credit = 1 Theory Hour OR 2 Lab Hours".
            // Let's visualize Credits.
            // But we only tracked hours roughly.

            int current = currentLoads.getOrDefault(f, 0);

            ProgressBar pb = new ProgressBar((double) current / Math.max(1, f.getMaxTeachingCredits())); // Rough
            pb.setPrefWidth(200);

            Label stats = new Label(current + " / " + f.getMaxTeachingCredits() + " (Credits/Hours approx)");

            row.getChildren().addAll(name, pb, stats);
            workloadContainer.getChildren().add(row);
        }
    }

    private File chooseFile(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        return fc.showOpenDialog(btnGenerate.getScene().getWindow());
    }

    private File saveFile(String title, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Files", ext));
        return fc.showSaveDialog(btnGenerate.getScene().getWindow());
    }

    private void updateStatus(String msg) {
        statusLabel.setText(msg);
    }

    private void showError(String title, Exception e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }
}
