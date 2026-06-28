package io.github.jean202.heicjpg;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class HeicJpgUi {
    private static final String PREF_INPUT = "inputPath";
    private static final String PREF_OUTPUT = "outputPath";
    private static final String PREF_USE_OUTPUT = "useOutputDir";
    private static final String PREF_OVERWRITE = "overwrite";
    private static final String PREF_LIMIT_DIMENSION = "limitDimension";
    private static final String PREF_MAX_DIMENSION = "maxDimension";
    private static final String PREF_DELETE_CONVERTED = "deleteConverted";
    private static final String PREF_RENAME_DIFFERENT = "renameDifferent";

    private final ConversionPlanner planner = new ConversionPlanner();
    private final ImageConverter converter = new SipsImageConverter();
    private final Preferences preferences = Preferences.userNodeForPackage(HeicJpgUi.class);

    private JFrame frame;
    private JTextField inputField;
    private JTextField outputField;
    private JButton chooseInputButton;
    private JButton chooseOutputButton;
    private JCheckBox useOutputDirCheck;
    private JCheckBox overwriteCheck;
    private JCheckBox limitDimensionCheck;
    private JCheckBox deleteConvertedCheck;
    private JCheckBox renameDifferentCheck;
    private JSpinner maxDimensionSpinner;
    private JButton previewButton;
    private JButton convertButton;
    private JButton cleanupButton;
    private JButton openOutputButton;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    private HeicJpgUi() {
    }

    public static void main(String[] args) {
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            HeicJpgUi app = new HeicJpgUi();
            app.createAndShow(args);
        });
    }

    private static void configureLookAndFeel() {
        System.setProperty("apple.awt.application.name", "HEIC JPG");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException
                 | InstantiationException
                 | IllegalAccessException
                 | UnsupportedLookAndFeelException ignored) {
            // The default Swing look and feel is usable if the system theme is unavailable.
        }
    }

    private void createAndShow(String[] args) {
        frame = new JFrame("HEIC JPG 변환기");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout(12, 12));

        frame.add(buildOptionsPanel(), BorderLayout.NORTH);
        frame.add(buildLogPanel(), BorderLayout.CENTER);
        frame.add(buildStatusPanel(), BorderLayout.SOUTH);

        restorePreferences(args);
        updateOutputControls();
        updateActions();

        frame.setMinimumSize(new Dimension(760, 540));
        frame.setSize(900, 620);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));

        inputField = new JTextField();
        chooseInputButton = new JButton("입력 선택");
        chooseInputButton.addActionListener(event -> chooseInput());

        useOutputDirCheck = new JCheckBox("다른 폴더에 저장");
        useOutputDirCheck.addActionListener(event -> {
            updateOutputControls();
            savePreferences();
        });

        outputField = new JTextField();
        chooseOutputButton = new JButton("저장 위치");
        chooseOutputButton.addActionListener(event -> chooseOutput());

        overwriteCheck = new JCheckBox("기존 JPG 덮어쓰기");
        overwriteCheck.addActionListener(event -> {
            if (overwriteCheck.isSelected()) {
                renameDifferentCheck.setSelected(false);
            }
            savePreferences();
        });

        limitDimensionCheck = new JCheckBox("긴 변 제한");
        limitDimensionCheck.addActionListener(event -> {
            maxDimensionSpinner.setEnabled(limitDimensionCheck.isSelected());
            savePreferences();
        });

        maxDimensionSpinner = new JSpinner(new SpinnerNumberModel(2048, 1, 20000, 128));
        maxDimensionSpinner.addChangeListener(event -> savePreferences());

        deleteConvertedCheck = new JCheckBox("변환 후 원본 HEIC 삭제");
        deleteConvertedCheck.setToolTipText("JPG가 정상 생성된 원본만 영구 삭제합니다. 삭제 전 확인합니다.");
        deleteConvertedCheck.addActionListener(event -> savePreferences());

        renameDifferentCheck = new JCheckBox("내용 다르면 새 이름(-1, -2)으로 저장");
        renameDifferentCheck.setToolTipText("같은 이름의 JPG가 있어도 사진 내용이 다르면 name-1.jpg로 저장합니다. "
                + "같은 사진이면 그대로 둡니다. '덮어쓰기'와 함께 쓸 수 없습니다.");
        renameDifferentCheck.addActionListener(event -> {
            if (renameDifferentCheck.isSelected()) {
                overwriteCheck.setSelected(false);
            }
            savePreferences();
        });

        previewButton = new JButton("미리보기");
        previewButton.addActionListener(event -> runPreview());

        convertButton = new JButton("변환 시작");
        convertButton.addActionListener(event -> runConversion());

        cleanupButton = new JButton("원본 정리");
        cleanupButton.setToolTipText("이미 JPG로 변환된 원본 HEIC만 찾아 삭제합니다. 변환은 하지 않습니다.");
        cleanupButton.addActionListener(event -> runCleanup());

        openOutputButton = new JButton("Finder 열기");
        openOutputButton.addActionListener(event -> openOutputLocation());

        addPathRow(panel, 0, "입력", inputField, chooseInputButton);
        addOutputRow(panel, chooseOutputButton);
        addOptionsRow(panel);
        addActionsRow(panel);

        DocumentListener listener = new SimpleDocumentListener(() -> {
            updateActions();
            savePreferences();
        });
        inputField.getDocument().addDocumentListener(listener);
        outputField.getDocument().addDocumentListener(listener);

        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 0, 16));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setRows(18);

        JScrollPane scrollPane = new JScrollPane(logArea);
        panel.add(new JLabel("작업 로그"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));

        statusLabel = new JLabel("입력 파일이나 폴더를 선택하세요.");
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("대기 중");

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.CENTER);
        return panel;
    }

    private void addPathRow(JPanel panel, int row, String label, JTextField field, JButton button) {
        GridBagConstraints constraints = baseConstraints(row);
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(button, constraints);
    }

    private void addOutputRow(JPanel panel, JButton chooseOutputButton) {
        GridBagConstraints constraints = baseConstraints(1);
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(useOutputDirCheck, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(outputField, constraints);

        constraints.gridx = 2;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(chooseOutputButton, constraints);
    }

    private void addOptionsRow(JPanel panel) {
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        options.add(overwriteCheck);
        options.add(limitDimensionCheck);
        options.add(maxDimensionSpinner);
        options.add(deleteConvertedCheck);
        options.add(renameDifferentCheck);

        GridBagConstraints constraints = baseConstraints(2);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(options, constraints);
    }

    private void addActionsRow(JPanel panel) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(openOutputButton);
        actions.add(cleanupButton);
        actions.add(previewButton);
        actions.add(convertButton);

        GridBagConstraints constraints = baseConstraints(3);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(actions, constraints);
    }

    private GridBagConstraints baseConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private void chooseInput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setDialogTitle("HEIC 파일이나 폴더 선택");
        setChooserDirectory(chooser, inputField.getText());

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            inputField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void chooseOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("JPG 저장 폴더 선택");
        setChooserDirectory(chooser, outputField.getText());

        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().toPath().toString());
            useOutputDirCheck.setSelected(true);
            updateOutputControls();
            savePreferences();
        }
    }

    private void setChooserDirectory(JFileChooser chooser, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }

        Path path = Path.of(rawPath.trim());
        Path directory = Files.isDirectory(path) ? path : path.getParent();
        if (directory != null && Files.isDirectory(directory)) {
            chooser.setCurrentDirectory(directory.toFile());
        }
    }

    private void runPreview() {
        CliOptions options = buildOptions(true);
        if (options == null) {
            return;
        }

        runWorker("계획 확인 중...", new PreviewWorker(options));
    }

    private void runConversion() {
        CliOptions options = buildOptions(false);
        if (options == null) {
            return;
        }

        runWorker("변환 중...", new ConversionWorker(options));
    }

    private void runCleanup() {
        CliOptions options = buildOptions(false);
        if (options == null) {
            return;
        }

        runWorker("원본 정리 중...", new CleanupWorker(options));
    }

    private void runWorker(String status, SwingWorker<WorkerResult, String> worker) {
        logArea.setText("");
        statusLabel.setText(status);
        progressBar.setValue(0);
        progressBar.setString(status);
        setRunning(true);

        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progressBar.setValue((Integer) event.getNewValue());
            }
        });
        worker.execute();
    }

    private CliOptions buildOptions(boolean dryRun) {
        String rawInput = inputField.getText().trim();
        if (rawInput.isEmpty()) {
            showError("입력 파일이나 폴더를 선택하세요.");
            return null;
        }

        Path input = Path.of(rawInput);
        Path outputDir = null;
        if (useOutputDirCheck.isSelected()) {
            String rawOutput = outputField.getText().trim();
            if (rawOutput.isEmpty()) {
                showError("저장 위치를 선택하거나 '다른 폴더에 저장'을 해제하세요.");
                return null;
            }
            outputDir = Path.of(rawOutput);
        }

        Integer maxDimension = null;
        if (limitDimensionCheck.isSelected()) {
            maxDimension = (Integer) maxDimensionSpinner.getValue();
        }

        savePreferences();
        return new CliOptions(
                List.of(input),
                outputDir,
                overwriteCheck.isSelected(),
                dryRun,
                maxDimension,
                !dryRun && deleteConvertedCheck.isSelected(),
                !dryRun && renameDifferentCheck.isSelected(),
                false
        );
    }

    private WorkerResult loadPlan(CliOptions options) {
        try {
            ConversionPlan plan = planner.plan(options);
            if (!plan.errors().isEmpty()) {
                return WorkerResult.failed(String.join(System.lineSeparator(), plan.errors()));
            }
            return WorkerResult.ready(plan);
        } catch (IOException exception) {
            return WorkerResult.failed(exception.getMessage());
        }
    }

    private void appendLog(String message) {
        logArea.append(message);
        logArea.append(System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "확인 필요", JOptionPane.WARNING_MESSAGE);
    }

    private void finishWorker(WorkerResult result) {
        setRunning(false);
        if (result.error() != null) {
            appendLog("ERROR " + result.error());
            statusLabel.setText("작업을 완료하지 못했습니다.");
            progressBar.setString("오류");
            JOptionPane.showMessageDialog(frame, result.error(), "작업 실패", JOptionPane.ERROR_MESSAGE);
            return;
        }

        statusLabel.setText(result.summary());
        progressBar.setValue(100);
        progressBar.setString("완료");
    }

    private void setRunning(boolean running) {
        previewButton.setEnabled(!running && hasInput());
        convertButton.setEnabled(!running && hasInput());
        cleanupButton.setEnabled(!running && hasInput());
        openOutputButton.setEnabled(!running && hasInput());
        inputField.setEnabled(!running);
        chooseInputButton.setEnabled(!running);
        outputField.setEnabled(!running && useOutputDirCheck.isSelected());
        chooseOutputButton.setEnabled(!running && useOutputDirCheck.isSelected());
        useOutputDirCheck.setEnabled(!running);
        overwriteCheck.setEnabled(!running);
        limitDimensionCheck.setEnabled(!running);
        deleteConvertedCheck.setEnabled(!running);
        renameDifferentCheck.setEnabled(!running);
        maxDimensionSpinner.setEnabled(!running && limitDimensionCheck.isSelected());
    }

    private void updateActions() {
        boolean enabled = hasInput();
        previewButton.setEnabled(enabled);
        convertButton.setEnabled(enabled);
        cleanupButton.setEnabled(enabled);
        openOutputButton.setEnabled(enabled);
    }

    private boolean hasInput() {
        return inputField != null && !inputField.getText().trim().isEmpty();
    }

    private void updateOutputControls() {
        boolean useOutputDir = useOutputDirCheck.isSelected();
        outputField.setEnabled(useOutputDir);
        chooseOutputButton.setEnabled(useOutputDir);
    }

    private void openOutputLocation() {
        try {
            Path path = outputLocation();
            if (path == null) {
                showError("열 위치를 찾을 수 없습니다.");
                return;
            }

            Files.createDirectories(path);
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException | UnsupportedOperationException exception) {
            JOptionPane.showMessageDialog(frame, exception.getMessage(), "Finder 열기 실패", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path outputLocation() {
        if (useOutputDirCheck.isSelected() && !outputField.getText().trim().isEmpty()) {
            return Path.of(outputField.getText().trim());
        }

        if (inputField.getText().trim().isEmpty()) {
            return null;
        }

        Path input = Path.of(inputField.getText().trim());
        if (Files.isDirectory(input)) {
            return input;
        }
        return input.getParent();
    }

    private void restorePreferences(String[] args) {
        String inputPath = args.length > 0 ? args[0] : preferences.get(PREF_INPUT, "");
        inputField.setText(inputPath);
        outputField.setText(preferences.get(PREF_OUTPUT, ""));
        useOutputDirCheck.setSelected(preferences.getBoolean(PREF_USE_OUTPUT, false));
        overwriteCheck.setSelected(preferences.getBoolean(PREF_OVERWRITE, false));
        limitDimensionCheck.setSelected(preferences.getBoolean(PREF_LIMIT_DIMENSION, false));
        deleteConvertedCheck.setSelected(preferences.getBoolean(PREF_DELETE_CONVERTED, false));
        renameDifferentCheck.setSelected(preferences.getBoolean(PREF_RENAME_DIFFERENT, false));
        maxDimensionSpinner.setValue(preferences.getInt(PREF_MAX_DIMENSION, 2048));
        maxDimensionSpinner.setEnabled(limitDimensionCheck.isSelected());
    }

    private void savePreferences() {
        if (inputField == null || outputField == null) {
            return;
        }

        preferences.put(PREF_INPUT, inputField.getText().trim());
        preferences.put(PREF_OUTPUT, outputField.getText().trim());
        preferences.putBoolean(PREF_USE_OUTPUT, useOutputDirCheck.isSelected());
        preferences.putBoolean(PREF_OVERWRITE, overwriteCheck.isSelected());
        preferences.putBoolean(PREF_LIMIT_DIMENSION, limitDimensionCheck.isSelected());
        preferences.putBoolean(PREF_DELETE_CONVERTED, deleteConvertedCheck.isSelected());
        preferences.putBoolean(PREF_RENAME_DIFFERENT, renameDifferentCheck.isSelected());
        preferences.putInt(PREF_MAX_DIMENSION, (Integer) maxDimensionSpinner.getValue());
    }

    private final class PreviewWorker extends SwingWorker<WorkerResult, String> {
        private final CliOptions options;

        private PreviewWorker(CliOptions options) {
            this.options = options;
        }

        @Override
        protected WorkerResult doInBackground() {
            WorkerResult loaded = loadPlan(options);
            if (loaded.error() != null) {
                return loaded;
            }

            ConversionPlan plan = Objects.requireNonNull(loaded.plan());
            if (plan.tasks().isEmpty()) {
                return WorkerResult.completed("변환할 HEIC/HEIF 파일이 없습니다.");
            }

            int planned = 0;
            int skipped = 0;
            for (ConversionTask task : plan.tasks()) {
                if (Files.exists(task.target()) && !options.overwrite()) {
                    publish("SKIP " + formatMapping(task) + " (target exists)");
                    skipped++;
                } else {
                    publish("PLAN " + formatMapping(task));
                    planned++;
                }
            }

            return WorkerResult.completed(
                    "변환 예정 " + planned + "개, 건너뜀 " + skipped + "개"
            );
        }

        @Override
        protected void process(List<String> chunks) {
            chunks.forEach(HeicJpgUi.this::appendLog);
        }

        @Override
        protected void done() {
            try {
                finishWorker(get());
            } catch (Exception exception) {
                finishWorker(WorkerResult.failed(exception.getMessage()));
            }
        }
    }

    private final class ConversionWorker extends SwingWorker<WorkerResult, String> {
        private final CliOptions options;

        private ConversionWorker(CliOptions options) {
            this.options = options;
        }

        @Override
        protected WorkerResult doInBackground() {
            WorkerResult loaded = loadPlan(options);
            if (loaded.error() != null) {
                return loaded;
            }

            ConversionPlan plan = Objects.requireNonNull(loaded.plan());
            if (plan.tasks().isEmpty()) {
                return WorkerResult.completed("변환할 HEIC/HEIF 파일이 없습니다.");
            }

            ContentAwareConverter contentConverter =
                    options.renameDifferent() ? new ContentAwareConverter(converter) : null;
            int converted = 0;
            int skipped = 0;
            int failed = 0;
            int total = plan.tasks().size();

            for (int index = 0; index < total; index++) {
                ConversionTask task = plan.tasks().get(index);
                try {
                    if (contentConverter != null) {
                        ContentAwareConverter.Result result = contentConverter.convert(task, options.maxDimension());
                        if (result.skipped()) {
                            publish("SAME " + task.source() + "  (이미 있음: " + result.skippedExisting() + ")");
                            skipped++;
                        } else {
                            publish("OK   " + task.source() + " -> " + result.written());
                            converted++;
                        }
                    } else if (Files.exists(task.target()) && !options.overwrite()) {
                        publish("SKIP " + formatMapping(task) + " (target exists)");
                        skipped++;
                    } else {
                        converter.convert(task, options.maxDimension());
                        publish("OK   " + formatMapping(task));
                        converted++;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    publish("FAIL " + formatMapping(task));
                    publish("  Conversion interrupted.");
                    failed++;
                    break;
                } catch (IOException exception) {
                    publish("FAIL " + formatMapping(task));
                    publish("  " + exception.getMessage());
                    failed++;
                }

                setProgress(Math.round(((index + 1) * 100f) / total));
            }

            String summary = "변환 " + converted + "개, 건너뜀 " + skipped + "개, 실패 " + failed + "개";
            if (options.deleteConverted()) {
                summary += cleanupConvertedSources(plan, this::publish);
            }
            return WorkerResult.completed(summary);
        }

        @Override
        protected void process(List<String> chunks) {
            chunks.forEach(HeicJpgUi.this::appendLog);
        }

        @Override
        protected void done() {
            try {
                finishWorker(get());
            } catch (Exception exception) {
                finishWorker(WorkerResult.failed(exception.getMessage()));
            }
        }
    }

    private final class CleanupWorker extends SwingWorker<WorkerResult, String> {
        private final CliOptions options;

        private CleanupWorker(CliOptions options) {
            this.options = options;
        }

        @Override
        protected WorkerResult doInBackground() {
            WorkerResult loaded = loadPlan(options);
            if (loaded.error() != null) {
                return loaded;
            }

            ConversionPlan plan = Objects.requireNonNull(loaded.plan());
            if (plan.tasks().isEmpty()) {
                return WorkerResult.completed("정리할 HEIC/HEIF 파일이 없습니다.");
            }

            String result = cleanupConvertedSources(plan, this::publish);
            setProgress(100);
            return WorkerResult.completed("원본 정리 완료" + result);
        }

        @Override
        protected void process(List<String> chunks) {
            chunks.forEach(HeicJpgUi.this::appendLog);
        }

        @Override
        protected void done() {
            try {
                finishWorker(get());
            } catch (Exception exception) {
                finishWorker(WorkerResult.failed(exception.getMessage()));
            }
        }
    }

    // Mirrors the CLI's --delete-converted rule: only remove HEIC/HEIF sources whose
    // matching JPG already exists and is non-empty, and only after explicit confirmation.
    // Shared by the convert-then-delete flow and the standalone cleanup action.
    private String cleanupConvertedSources(ConversionPlan plan, java.util.function.Consumer<String> log) {
        List<ConversionTask> deletable = new ArrayList<>();
        for (ConversionTask task : plan.tasks()) {
            if (hasConvertedJpg(task.target())) {
                deletable.add(task);
            }
        }

        if (deletable.isEmpty()) {
            log.accept("삭제할 원본이 없습니다 (대응하는 JPG 없음).");
            return "; 삭제 0개";
        }

        if (!confirmDeletion(deletable.size())) {
            log.accept("원본 삭제를 취소했습니다.");
            return "; 삭제 취소";
        }

        int deleted = 0;
        int deleteFailed = 0;
        for (ConversionTask task : deletable) {
            try {
                Files.delete(task.source());
                log.accept("DELETED " + task.source());
                deleted++;
            } catch (IOException exception) {
                log.accept("FAIL    " + task.source() + "  (" + exception.getMessage() + ")");
                deleteFailed++;
            }
        }
        return "; 원본 삭제 " + deleted + "개, 삭제 실패 " + deleteFailed + "개";
    }

    private String formatMapping(ConversionTask task) {
        return task.source() + " -> " + task.target();
    }

    private boolean hasConvertedJpg(Path target) {
        try {
            return Files.isRegularFile(target) && Files.size(target) > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    // Called from a background worker; show the modal prompt on the EDT and wait for the answer.
    private boolean confirmDeletion(int count) {
        AtomicBoolean confirmed = new AtomicBoolean(false);
        try {
            SwingUtilities.invokeAndWait(() -> {
                int choice = JOptionPane.showConfirmDialog(
                        frame,
                        "원본 HEIC " + count + "개를 영구 삭제합니다.\n되돌릴 수 없습니다. 삭제할까요?",
                        "원본 삭제 확인",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                confirmed.set(choice == JOptionPane.YES_OPTION);
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.lang.reflect.InvocationTargetException exception) {
            return false;
        }
        return confirmed.get();
    }

    private record WorkerResult(ConversionPlan plan, String summary, String error) {
        private static WorkerResult ready(ConversionPlan plan) {
            return new WorkerResult(plan, null, null);
        }

        private static WorkerResult completed(String summary) {
            return new WorkerResult(null, summary, null);
        }

        private static WorkerResult failed(String error) {
            return new WorkerResult(null, null, error);
        }
    }

    private record SimpleDocumentListener(Runnable callback) implements DocumentListener {
        @Override
        public void insertUpdate(DocumentEvent event) {
            callback.run();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            callback.run();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            callback.run();
        }
    }
}
