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
import javafx.scene.Scene;

import java.io.File;
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

    // Navigation Controls
    @FXML
    private ComboBox<String> sectionSelector;
    @FXML
    private Button btnPrevSection;
    @FXML
    private Button btnNextSection;

    private List<Faculty> facultyList = new ArrayList<>();
    private List<Subject> subjectList = new ArrayList<>();
    private List<Section> sectionList = new ArrayList<>();

    private Chromosome currentTimetable;

    private List<String> sortedSections = new ArrayList<>();
    private String currentSectionName = null;

    @FXML
    public void initialize() {
        loadDefaultData();

        // Listener for Dropdown
        sectionSelector.setOnAction(e -> {
            String selected = sectionSelector.getValue();
            if (selected != null && !selected.equals(currentSectionName)) {
                currentSectionName = selected;
                if (currentTimetable != null) {
                    renderTimetable(currentTimetable);
                }
                updateButtonStates();
            }
        });
    }

    private void loadDefaultData() {
        try {
            // Hardcoded paths for development convenience
            String projectDir = System.getProperty("user.dir");
            String sectionsPath = projectDir + "/data/sections.csv";
            String facultyPath = projectDir + "/data/faculty.csv";
            String subjectsPath = projectDir + "/data/subjects.csv";

            File fSections = new File(sectionsPath);
            File fFaculty = new File(facultyPath);
            File fSubjects = new File(subjectsPath);

            if (fSections.exists() && fFaculty.exists() && fSubjects.exists()) {
                sectionList = new CsvParser().loadSections(sectionsPath);
                facultyList = new CsvParser().loadFaculty(facultyPath);
                subjectList = new CsvParser().loadSubjects(subjectsPath);

                updateStatus("Auto-loaded: " + sectionList.size() + " Sections, " +
                        facultyList.size() + " Faculty, " +
                        subjectList.size() + " Subjects.");
                updateWorkloadView();
            } else {
                updateStatus("Data files not found in /data folder. Please import manually.");
            }
        } catch (Exception e) {
            System.err.println("Failed to auto-load data: " + e.getMessage());
            updateStatus("Auto-load failed. Import manually.");
        }
    }

    @FXML
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

    @FXML
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

    @FXML
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
                    // Update Sections List
                    Map<String, List<Gene>> genesBySection = currentTimetable.getGenes().stream()
                            .collect(Collectors.groupingBy(g -> g.getSection().getName()));
                    sortedSections = new ArrayList<>(genesBySection.keySet());
                    Collections.sort(sortedSections);
                    sectionSelector.getItems().setAll(sortedSections);

                    if (!sortedSections.isEmpty()) {
                        currentSectionName = sortedSections.get(0);
                        sectionSelector.setValue(currentSectionName);
                    }

                    renderTimetable(currentTimetable);
                    updateWorkloadView(); // Update with actual assigned hours
                    btnExportExcel.setDisable(false);
                    btnExportPdf.setDisable(false);
                    updateButtonStates();

                    updateStatus(
                            "Generation Complete! Fitness: " + String.format("%.4f", currentTimetable.getFitness()));
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Generation Failed", e));
            }
        }).start();
    }

    @FXML
    public void showPreviousSection() {
        if (sortedSections.isEmpty() || currentSectionName == null)
            return;
        int idx = sortedSections.indexOf(currentSectionName);
        if (idx > 0) {
            sectionSelector.setValue(sortedSections.get(idx - 1));
        }
    }

    @FXML
    public void showNextSection() {
        if (sortedSections.isEmpty() || currentSectionName == null)
            return;
        int idx = sortedSections.indexOf(currentSectionName);
        if (idx >= 0 && idx < sortedSections.size() - 1) {
            sectionSelector.setValue(sortedSections.get(idx + 1));
        }
    }

    private void updateButtonStates() {
        if (sortedSections.isEmpty() || currentSectionName == null) {
            btnPrevSection.setDisable(true);
            btnNextSection.setDisable(true);
            return;
        }
        int idx = sortedSections.indexOf(currentSectionName);
        btnPrevSection.setDisable(idx <= 0);
        btnNextSection.setDisable(idx >= sortedSections.size() - 1);
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

        if (currentSectionName == null) {
            Label placeholder = new Label("Select a section/Generate to view timetable.");
            placeholder.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
            timetableContainer.getChildren().add(placeholder);
            return;
        }

        Map<String, List<Gene>> genesBySection = chromosome.getGenes().stream()
                .collect(Collectors.groupingBy(g -> g.getSection().getName()));

        if (!genesBySection.containsKey(currentSectionName))
            return;

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

        VBox sectionBox = new VBox(5);
        sectionBox.getStyleClass().add("card");
        Label lbl = new Label("Section: " + currentSectionName);
        lbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #00bcd4;");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("timetable-grid");

        // Make columns responsive
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setPercentWidth(5);
        grid.getColumnConstraints().add(col1);

        for (int i = 0; i < 11; i++) {
            javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
            col.setPercentWidth(95.0 / 11.0);
            grid.getColumnConstraints().add(col);
        }

        // Render Headers
        String[] headers = {
                "Time", "08:00\n08:55", "08:55\n09:50", "BREAK\n09:50-10:20",
                "10:20\n11:15", "11:15\n12:10", "12:10\n13:05", "LUNCH\n13:05-14:00",
                "14:00\n14:55", "14:55\n15:50", "15:50\n16:45", "16:45\n17:40"
        };

        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("timetable-header");
            h.setMaxWidth(Double.MAX_VALUE);
            h.setAlignment(Pos.CENTER);
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
            d.setMaxWidth(Double.MAX_VALUE);
            d.setMinHeight(50);
            grid.add(d, 0, r + 1);

            // Fill Breaks/Lunch for this row (Cols 3 and 7)
            addBreakCell(grid, 3, r + 1, "BREAK");
            addBreakCell(grid, 7, r + 1, "LUNCH");

            // Fill Slots
            List<Gene> dayGenes = genesBySection.get(currentSectionName).stream()
                    .filter(g -> g.getSlot().getDay().toString().equals(dayFull))
                    .collect(Collectors.toList());

            for (Gene g : dayGenes) {
                Integer col = timeToCol.get(g.getSlot().getStartTime().toString());
                if (col != null) {
                    VBox cell = new VBox(2);
                    cell.getStyleClass().add("timetable-cell");
                    cell.getStyleClass().add(g.getSubject().isLab() ? "cell-lab" : "cell-theory");

                    Label subj = new Label(g.getSubject().getName());
                    subj.getStyleClass().add("text-subject");
                    subj.setWrapText(true);

                    String facName = g.getFaculty().isEmpty() ? "?" : g.getFaculty().get(0).getName();
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
                        Tooltip.install(cell, tip);
                    }

                    cell.getChildren().addAll(subj, fac);
                    cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                    grid.add(cell, col, r + 1);
                }
            }
        }

        sectionBox.getChildren().addAll(lbl, grid);
        timetableContainer.getChildren().add(sectionBox);
    }

    private void addBreakCell(GridPane grid, int col, int row, String text) {
        Label l = new Label(text);
        l.getStyleClass().add("cell-break");
        l.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        grid.add(l, col, row);
    }

    private void updateWorkloadView() {
        workloadContainer.getChildren().clear();

        // Helper class for aggregation
        class FacStats {
            int theoryHours = 0;
            int labHours = 0;
            double usedCredits = 0.0;
        }

        Map<Faculty, FacStats> statsMap = new LinkedHashMap<>(); // Use LinkedHashMap to keep order
        for (Faculty f : facultyList) {
            statsMap.put(f, new FacStats());
        }

        if (currentTimetable != null) {
            for (Gene g : currentTimetable.getGenes()) {
                boolean isLab = g.getSubject().isLab();
                double creditPerSlot = isLab ? 0.5 : 1.0;

                for (Faculty f : g.getFaculty()) {
                    FacStats s = statsMap.get(f);
                    if (s != null) {
                        if (isLab) {
                            s.labHours++;
                        } else {
                            s.theoryHours++;
                        }
                        s.usedCredits += creditPerSlot;
                    }
                }
            }
        }

        for (Faculty f : facultyList) {
            FacStats s = statsMap.get(f);

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card");
            row.setStyle("-fx-padding: 10; -fx-cursor: hand;"); // Add cursor hand style

            // Add click listener
            row.setOnMouseClicked(e -> showFacultyTimetable(f));

            VBox infoBox = new VBox(5);
            Label name = new Label(f.getName());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

            String creditText = String.format("Credits: %.1f / %d", s.usedCredits, f.getMaxTeachingCredits());
            String hoursText = String.format("Theory: %d hrs | Lab: %d hrs", s.theoryHours, s.labHours);

            Label lblCredits = new Label(creditText);
            Label lblHours = new Label(hoursText);
            lblHours.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");

            infoBox.getChildren().addAll(name, lblCredits, lblHours);
            infoBox.setPrefWidth(250);

            double progress = s.usedCredits / Math.max(1, f.getMaxTeachingCredits());
            ProgressBar pb = new ProgressBar(progress);
            pb.setPrefWidth(200);

            if (progress > 1.0) {
                pb.setStyle("-fx-accent: red;");
            } else if (progress > 0.9) {
                pb.setStyle("-fx-accent: orange;");
            } else {
                pb.setStyle("-fx-accent: #00bcd4;");
            }

            row.getChildren().addAll(infoBox, pb);
            workloadContainer.getChildren().add(row);
        }
    }

    private void showFacultyTimetable(Faculty fac) {
        if (currentTimetable == null)
            return;

        Stage stage = new Stage();
        stage.setTitle("Timetable: " + fac.getName());

        VBox root = new VBox(10);
        root.setStyle("-fx-background-color: #121212; -fx-padding: 20;");
        // Load stylesheet
        Scene scene = new Scene(root, 1000, 600);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        Label title = new Label(fac.getName() + " - Timetable");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #00bcd4;");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("timetable-grid");

        // --- Same Grid Construction as Main Timetable ---
        // Headers
        String[] headers = {
                "Time", "08:00\n08:55", "08:55\n09:50", "BREAK\n09:50-10:20",
                "10:20\n11:15", "11:15\n12:10", "12:10\n13:05", "LUNCH\n13:05-14:00",
                "14:00\n14:55", "14:55\n15:50", "15:50\n16:45", "16:45\n17:40"
        };

        // Columns setup
        javafx.scene.layout.ColumnConstraints col1 = new javafx.scene.layout.ColumnConstraints();
        col1.setPercentWidth(5);
        grid.getColumnConstraints().add(col1);
        for (int i = 0; i < 11; i++) {
            javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
            col.setPercentWidth(95.0 / 11.0);
            grid.getColumnConstraints().add(col);
        }

        for (int c = 0; c < headers.length; c++) {
            Label h = new Label(headers[c]);
            h.getStyleClass().add("timetable-header");
            h.setMaxWidth(Double.MAX_VALUE);
            h.setAlignment(Pos.CENTER);
            h.setMinHeight(35);
            grid.add(h, c, 0);
        }

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

        String[] days = { "MON", "TUE", "WED", "THU", "FRI", "SAT" };
        String[] fullDayNames = { "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY" };

        for (int r = 0; r < days.length; r++) {
            String dayShort = days[r];
            String dayFull = fullDayNames[r];

            Label d = new Label(dayShort);
            d.getStyleClass().add("timetable-day");
            d.setMaxWidth(Double.MAX_VALUE);
            d.setMinHeight(50);
            grid.add(d, 0, r + 1);

            addBreakCell(grid, 3, r + 1, "BREAK");
            addBreakCell(grid, 7, r + 1, "LUNCH");

            // Filter genes explicitly for this faculty
            List<Gene> myGenes = currentTimetable.getGenes().stream()
                    .filter(g -> g.getFaculty().contains(fac))
                    .filter(g -> g.getSlot().getDay().toString().equals(dayFull))
                    .collect(Collectors.toList());

            for (Gene g : myGenes) {
                Integer col = timeToCol.get(g.getSlot().getStartTime().toString());
                if (col != null) {
                    VBox cell = new VBox(2);
                    cell.getStyleClass().add("timetable-cell");
                    cell.getStyleClass().add("cell-theory"); // Just use one color for simplicity or reuse logic

                    Label sectionLbl = new Label(g.getSection().getName());
                    sectionLbl.getStyleClass().add("text-subject"); // Recycle style

                    Label subjLbl = new Label(g.getSubject().getName());
                    subjLbl.getStyleClass().add("text-faculty"); // Recycle style

                    cell.getChildren().addAll(sectionLbl, subjLbl);
                    cell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                    grid.add(cell, col, r + 1);
                }
            }
        }

        root.getChildren().addAll(title, grid);
        stage.setScene(scene);
        stage.show();
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
