/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package gui;

import algoritmos.TipoAlgoritmo;
import estructuras.ListaDobleEnlazada;
import estructuras.MyQueue;
import logica.InterruptHandler;
import logica.Kernel;
import logica.SimulationClock;
import modelos.Process;
import utils.ProcessFactory;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.util.Random;

/**
 * Centro de Control de Misión - GUI del Simulador RTOS.
 * Estética de satélite: fondo oscuro, bordes de cian/morado, fuentes monoespaciadas.
 * @author Francisco
 */
public class MainFrame extends JFrame {

    // === Colores del tema espacial ===
    private static final Color BG_DARK = new Color(20, 20, 28);
    private static final Color BG_PANEL = new Color(30, 30, 42);
    private static final Color BG_TABLE = new Color(25, 25, 35);
    private static final Color CYAN = new Color(0, 200, 220);
    private static final Color PURPLE = new Color(140, 80, 220);
    private static final Color GREEN = new Color(0, 220, 100);
    private static final Color RED = new Color(220, 50, 50);
    private static final Color YELLOW = new Color(220, 200, 0);
    private static final Color TEXT_PRIMARY = new Color(220, 220, 230);
    private static final Color TEXT_DIM = new Color(140, 140, 160);

    // === Fuentes ===
    private static final Font FONT_DIGITAL = new Font("Consolas", Font.BOLD, 28);
    private static final Font FONT_TITLE = new Font("Consolas", Font.BOLD, 13);
    private static final Font FONT_DATA = new Font("Consolas", Font.PLAIN, 12);
    private static final Font FONT_SMALL = new Font("Consolas", Font.PLAIN, 11);
    private static final Font FONT_BUTTON = new Font("Consolas", Font.BOLD, 12);

    // === Componentes del sistema ===
    private Kernel kernel;
    private SimulationClock clock;
    private InterruptHandler interruptHandler;
    private boolean simulationStarted = false;

    // === Panel Superior ===
    private JLabel lblCycleCounter;
    private JLabel lblModeIndicator;
    private JComboBox<String> cmbAlgorithm;
    private JSlider sliderSpeed;
    private JLabel lblSpeedValue;
    private boolean modeBlinkState = false;
    private Timer modeBlinkTimer;

    // === Monitor CPU ===
    private JLabel lblCpuProcessName;
    private JLabel lblCpuId, lblCpuPc, lblCpuMar, lblCpuPriority, lblCpuStatus;
    private JProgressBar progressCpu;
    private JLabel lblDeadlineValue;
    private JProgressBar progressDeadline;

    // === Tablas de colas ===
    private DefaultTableModel modelReady, modelBlocked, modelFinished;
    private DefaultTableModel modelSuspReady, modelSuspBlocked;

    // === Métricas ===
    private JLabel lblMetricTotal, lblMetricCompleted, lblMetricFailed;
    private JLabel lblMetricRate, lblMetricThroughput, lblMetricContextSwitches;

    // === Gráfico CPU (simple) ===
    private int[] cpuHistory = new int[120];
    private int cpuHistoryIndex = 0;
    private JPanel cpuGraphPanel;

    // === Log de Eventos ===
    private JTextArea txtLog;

    // === Controles ===
    private JButton btnStartPause, btnStop, btnInject;

    // === Contador de procesos inyectados ===
    private int injectedCount = 0;

    public MainFrame() {
        super("RTOS SATELLITE MISSION CONTROL");
        initKernel();
        initUI();
        initTimers();
    }

    // =====================================================================
    // INICIALIZACIÓN DEL KERNEL
    // =====================================================================

    private void initKernel() {
        kernel = new Kernel();
        // Cargar procesos de misión por defecto
        kernel.addProcess(new Process("P1", "Sensor_1", 6, 2, 20, 3, 2));
        kernel.addProcess(new Process("P2", "Sensor_2", 4, 0, 12, -1, 0));
        kernel.addProcess(new Process("P3", "Camara_1", 5, 1, 8, 2, 3));
        kernel.addProcess(new Process("P4", "Camara_2", 3, 3, 25, -1, 0));
        kernel.addProcess(new Process("P5", "Telemetria", 0, 1, 15, -1, 0));
        kernel.addProcess(new Process("P6", "Antena_A", 5, 1, 18, -1, 0));
        kernel.addProcess(new Process("P7", "Antena_B", 5, 2, 3, -1, 0));
    }

    // =====================================================================
    // INTERFAZ GRÁFICA
    // =====================================================================

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 850);
        setMinimumSize(new Dimension(1200, 750));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(4, 4));

        add(createTopBar(), BorderLayout.NORTH);
        add(createCenterPanel(), BorderLayout.CENTER);
        add(createRightPanel(), BorderLayout.EAST);
        add(createBottomPanel(), BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAll();
            }
        });

        // Refrescar tablas iniciales
        refreshAllTables();
    }

    // =====================================================================
    // PANEL SUPERIOR - Status Bar
    // =====================================================================

    private JPanel createTopBar() {
        JPanel bar = darkPanel(new FlowLayout(FlowLayout.LEFT, 15, 6));
        bar.setPreferredSize(new Dimension(0, 55));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, CYAN));

        // Reloj digital
        lblCycleCounter = new JLabel("CICLO: 0000");
        lblCycleCounter.setFont(FONT_DIGITAL);
        lblCycleCounter.setForeground(CYAN);
        bar.add(lblCycleCounter);

        bar.add(createSeparator());

        // Indicador de modo
        lblModeIndicator = new JLabel(" USUARIO (USER) ");
        lblModeIndicator.setFont(FONT_TITLE);
        lblModeIndicator.setForeground(Color.BLACK);
        lblModeIndicator.setOpaque(true);
        lblModeIndicator.setBackground(GREEN);
        bar.add(lblModeIndicator);

        bar.add(createSeparator());

        // Selector de algoritmo
        JLabel lblAlg = new JLabel("ALGORITMO:");
        lblAlg.setFont(FONT_TITLE);
        lblAlg.setForeground(TEXT_PRIMARY);
        bar.add(lblAlg);

        cmbAlgorithm = new JComboBox<>(new String[]{"FCFS", "EDF", "RR", "PRIORIDAD", "SRT"});
        cmbAlgorithm.setFont(FONT_DATA);
        cmbAlgorithm.setBackground(BG_PANEL);
        cmbAlgorithm.setForeground(CYAN);
        cmbAlgorithm.setPreferredSize(new Dimension(130, 30));
        cmbAlgorithm.addActionListener(e -> onAlgorithmChanged());
        bar.add(cmbAlgorithm);

        bar.add(createSeparator());

        // Slider de velocidad
        JLabel lblSpeed = new JLabel("VELOCIDAD:");
        lblSpeed.setFont(FONT_TITLE);
        lblSpeed.setForeground(TEXT_PRIMARY);
        bar.add(lblSpeed);

        sliderSpeed = new JSlider(100, 2000, 800);
        sliderSpeed.setPreferredSize(new Dimension(150, 30));
        sliderSpeed.setBackground(BG_DARK);
        sliderSpeed.setForeground(CYAN);
        sliderSpeed.addChangeListener(e -> onSpeedChanged());
        bar.add(sliderSpeed);

        lblSpeedValue = new JLabel("800ms");
        lblSpeedValue.setFont(FONT_DATA);
        lblSpeedValue.setForeground(YELLOW);
        bar.add(lblSpeedValue);

        bar.add(createSeparator());

        // Botones de control
        btnStartPause = styledButton("▶ INICIAR", GREEN);
        btnStartPause.addActionListener(e -> onStartPause());
        bar.add(btnStartPause);

        btnStop = styledButton("■ DETENER", RED);
        btnStop.setEnabled(false);
        btnStop.addActionListener(e -> onStop());
        bar.add(btnStop);

        return bar;
    }

    // =====================================================================
    // PANEL CENTRAL - Dashboard
    // =====================================================================

    private JPanel createCenterPanel() {
        JPanel center = darkPanel(new BorderLayout(4, 4));

        // Parte superior: CPU Monitor + Deadline + Emergencia
        JPanel topDash = darkPanel(new GridLayout(1, 3, 4, 0));
        topDash.add(createCpuMonitor());
        topDash.add(createDeadlineTracker());
        topDash.add(createEmergencyPanel());
        center.add(topDash, BorderLayout.NORTH);

        // Parte central: Tablas de colas
        JPanel tables = darkPanel(new GridLayout(1, 2, 4, 0));
        tables.add(createRamPanel());
        tables.add(createSwapPanel());
        center.add(tables, BorderLayout.CENTER);

        return center;
    }

    private JPanel createCpuMonitor() {
        JPanel panel = titledPanel("CPU MONITOR", CYAN);
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 6, 2, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        // Nombre del proceso
        lblCpuProcessName = new JLabel("[ CPU OCIOSA ]");
        lblCpuProcessName.setFont(new Font("Consolas", Font.BOLD, 18));
        lblCpuProcessName.setForeground(YELLOW);
        lblCpuProcessName.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(lblCpuProcessName, gbc);

        // PCB
        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0;
        lblCpuId = dataLabel("ID: ---");
        panel.add(lblCpuId, gbc);
        gbc.gridx = 1;
        lblCpuStatus = dataLabel("Estado: ---");
        panel.add(lblCpuStatus, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        lblCpuPc = dataLabel("PC: ---");
        panel.add(lblCpuPc, gbc);
        gbc.gridx = 1;
        lblCpuMar = dataLabel("MAR: ---");
        panel.add(lblCpuMar, gbc);

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 2;
        lblCpuPriority = dataLabel("Prioridad: ---");
        panel.add(lblCpuPriority, gbc);

        // Barra de progreso
        gbc.gridy = 4;
        progressCpu = new JProgressBar(0, 100);
        progressCpu.setStringPainted(true);
        progressCpu.setFont(FONT_SMALL);
        progressCpu.setBackground(BG_TABLE);
        progressCpu.setForeground(CYAN);
        progressCpu.setString("Ejecución: 0%");
        panel.add(progressCpu, gbc);

        return panel;
    }

    private JPanel createDeadlineTracker() {
        JPanel panel = titledPanel("DEADLINE TRACKER", PURPLE);
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0; gbc.gridy = 0;

        JLabel lblTitle = new JLabel("TIEMPO RESTANTE");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(TEXT_DIM);
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(lblTitle, gbc);

        gbc.gridy = 1;
        lblDeadlineValue = new JLabel("---");
        lblDeadlineValue.setFont(new Font("Consolas", Font.BOLD, 42));
        lblDeadlineValue.setForeground(GREEN);
        lblDeadlineValue.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(lblDeadlineValue, gbc);

        gbc.gridy = 2;
        progressDeadline = new JProgressBar(0, 100);
        progressDeadline.setStringPainted(true);
        progressDeadline.setFont(FONT_SMALL);
        progressDeadline.setBackground(BG_TABLE);
        progressDeadline.setForeground(GREEN);
        progressDeadline.setString("Deadline: N/A");
        panel.add(progressDeadline, gbc);

        return panel;
    }

    private JPanel createEmergencyPanel() {
        JPanel panel = titledPanel("CONTROL DE EMERGENCIA", RED);
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;

        // Botón de meteorito
        gbc.gridy = 0;
        JButton btnMeteor = new JButton("☄ MICRO-METEORITO");
        btnMeteor.setFont(new Font("Consolas", Font.BOLD, 14));
        btnMeteor.setBackground(RED);
        btnMeteor.setForeground(Color.WHITE);
        btnMeteor.setFocusPainted(false);
        btnMeteor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 80, 80), 2),
                BorderFactory.createEmptyBorder(10, 15, 10, 15)));
        btnMeteor.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnMeteor.addActionListener(e -> onMeteorImpact());
        panel.add(btnMeteor, gbc);

        // Botón inyectar tarea individual
        gbc.gridy = 1;
        btnInject = styledButton("⊕ INYECTAR TAREA", PURPLE);
        btnInject.addActionListener(e -> onInjectProcess());
        panel.add(btnInject, gbc);

        // Botón generar 20 procesos de estrés
        gbc.gridy = 2;
        JButton btnStress = styledButton("⚡ GENERAR 20 PROCESOS", YELLOW);
        btnStress.setForeground(Color.BLACK);
        btnStress.addActionListener(e -> onStressLoad());
        panel.add(btnStress, gbc);

        // Botones de serialización
        gbc.gridy = 2;
        JButton btnSave = styledButton("💾 GUARDAR MISIÓN", CYAN);
        btnSave.addActionListener(e -> onSaveState());
        panel.add(btnSave, gbc);

        gbc.gridy = 3;
        JButton btnLoad = styledButton("📂 CARGAR MISIÓN", CYAN);
        btnLoad.addActionListener(e -> onLoadState());
        panel.add(btnLoad, gbc);

        return panel;
    }

    // =====================================================================
    // TABLAS DE COLAS
    // =====================================================================

    private JPanel createRamPanel() {
        JPanel panel = titledPanel("MEMORIA RAM", CYAN);
        panel.setLayout(new GridLayout(3, 1, 0, 3));

        String[] cols = {"ID", "Nombre", "PC", "MAR", "Prio", "Deadline", "Estado"};
        modelReady = new DefaultTableModel(cols, 0);
        modelBlocked = new DefaultTableModel(cols, 0);
        modelFinished = new DefaultTableModel(cols, 0);

        panel.add(tableWithTitle("Cola de Listos", modelReady, GREEN));
        panel.add(tableWithTitle("Cola de Bloqueados", modelBlocked, YELLOW));
        panel.add(tableWithTitle("Terminados", modelFinished, TEXT_DIM));

        return panel;
    }

    private JPanel createSwapPanel() {
        JPanel panel = titledPanel("MEMORIA SWAP", PURPLE);
        panel.setLayout(new GridLayout(2, 1, 0, 3));

        String[] cols = {"ID", "Nombre", "PC", "MAR", "Prio", "Deadline", "Estado"};
        modelSuspReady = new DefaultTableModel(cols, 0);
        modelSuspBlocked = new DefaultTableModel(cols, 0);

        panel.add(tableWithTitle("Listo-Suspendido", modelSuspReady, YELLOW));
        panel.add(tableWithTitle("Bloqueado-Suspendido", modelSuspBlocked, RED));

        return panel;
    }

    private JPanel tableWithTitle(String title, DefaultTableModel model, Color titleColor) {
        JPanel wrapper = darkPanel(new BorderLayout());
        JLabel lbl = new JLabel("  " + title);
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(titleColor);
        lbl.setPreferredSize(new Dimension(0, 18));
        wrapper.add(lbl, BorderLayout.NORTH);

        JTable table = new JTable(model) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table.setFont(FONT_SMALL);
        table.setBackground(BG_TABLE);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(new Color(50, 50, 65));
        table.setRowHeight(20);
        table.setSelectionBackground(PURPLE.darker());
        table.setSelectionForeground(Color.WHITE);

        JTableHeader header = table.getTableHeader();
        header.setBackground(new Color(40, 40, 55));
        header.setForeground(CYAN);
        header.setFont(FONT_SMALL);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
        }

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_TABLE);
        wrapper.add(sp, BorderLayout.CENTER);

        return wrapper;
    }

    // =====================================================================
    // PANEL DERECHO - Métricas y Gráfico
    // =====================================================================

    private JPanel createRightPanel() {
        JPanel right = darkPanel(new BorderLayout(0, 4));
        right.setPreferredSize(new Dimension(280, 0));

        // Métricas
        JPanel metrics = titledPanel("MÉTRICAS DE MISIÓN", CYAN);
        metrics.setLayout(new GridLayout(6, 1, 0, 2));
        lblMetricTotal = metricLabel("Procesos totales: 0");
        lblMetricCompleted = metricLabel("Completados: 0");
        lblMetricFailed = metricLabel("Fallos de misión: 0");
        lblMetricRate = metricLabel("Tasa de éxito: 0.0%");
        lblMetricThroughput = metricLabel("Throughput: 0.00");
        lblMetricContextSwitches = metricLabel("Context Switches: 0");
        metrics.add(lblMetricTotal);
        metrics.add(lblMetricCompleted);
        metrics.add(lblMetricFailed);
        metrics.add(lblMetricRate);
        metrics.add(lblMetricThroughput);
        metrics.add(lblMetricContextSwitches);
        right.add(metrics, BorderLayout.NORTH);

        // Gráfico CPU
        cpuGraphPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawCpuGraph(g);
            }
        };
        cpuGraphPanel.setBackground(BG_TABLE);
        cpuGraphPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(PURPLE, 1),
                "CPU UTILIZACIÓN", TitledBorder.LEFT, TitledBorder.TOP,
                FONT_SMALL, PURPLE));
        cpuGraphPanel.setPreferredSize(new Dimension(280, 150));
        right.add(cpuGraphPanel, BorderLayout.CENTER);

        return right;
    }

    private void drawCpuGraph(Graphics g) {
        int w = cpuGraphPanel.getWidth() - 20;
        int h = cpuGraphPanel.getHeight() - 30;
        int x0 = 10, y0 = 20;

        // Fondo de grilla
        g.setColor(new Color(35, 35, 50));
        g.fillRect(x0, y0, w, h);

        // Líneas horizontales
        g.setColor(new Color(50, 50, 65));
        for (int i = 0; i <= 4; i++) {
            int y = y0 + (h * i / 4);
            g.drawLine(x0, y, x0 + w, y);
        }

        // Dibujar barras
        int barWidth = Math.max(1, w / cpuHistory.length);
        for (int i = 0; i < cpuHistory.length; i++) {
            int barH = (int) (h * (cpuHistory[i] / 100.0));
            if (cpuHistory[i] > 0) {
                g.setColor(CYAN);
                g.fillRect(x0 + i * barWidth, y0 + h - barH, barWidth - 1, barH);
            }
        }
    }

    // =====================================================================
    // PANEL INFERIOR - Log de Eventos
    // =====================================================================

    private JPanel createBottomPanel() {
        JPanel bottom = titledPanel("EVENT LOG", GREEN);
        bottom.setPreferredSize(new Dimension(0, 140));
        bottom.setLayout(new BorderLayout());

        txtLog = new JTextArea();
        txtLog.setFont(FONT_SMALL);
        txtLog.setBackground(BG_TABLE);
        txtLog.setForeground(GREEN);
        txtLog.setCaretColor(GREEN);
        txtLog.setEditable(false);
        txtLog.setLineWrap(true);

        JScrollPane sp = new JScrollPane(txtLog);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_TABLE);
        bottom.add(sp, BorderLayout.CENTER);

        return bottom;
    }

    // =====================================================================
    // ACCIONES DE BOTONES
    // =====================================================================

    private void onStartPause() {
        if (!simulationStarted) {
            // Primera vez: iniciar
            startSimulation();
            simulationStarted = true;
            btnStartPause.setText("⏸ PAUSAR");
            btnStartPause.setBackground(YELLOW);
            btnStop.setEnabled(true);
            cmbAlgorithm.setEnabled(false);
        } else if (clock != null && clock.isRunning()) {
            if (clock.isPaused()) {
                clock.resumeSimulation();
                btnStartPause.setText("⏸ PAUSAR");
                btnStartPause.setBackground(YELLOW);
                setModeOs(false);
            } else {
                clock.pauseSimulation();
                btnStartPause.setText("▶ REANUDAR");
                btnStartPause.setBackground(GREEN);
            }
        }
    }

    private void onStop() {
        stopAll();
        logEvent("Simulación detenida manualmente.");

        // Imprimir reportes en consola
        kernel.printMissionReports();

        btnStartPause.setText("▶ INICIAR");
        btnStartPause.setBackground(GREEN);
        btnStop.setEnabled(false);
        cmbAlgorithm.setEnabled(true);
        simulationStarted = false;
    }

    private void onAlgorithmChanged() {
        String selected = (String) cmbAlgorithm.getSelectedItem();
        switch (selected) {
            case "FCFS" -> kernel.setAlgorithm("FCFS");
            case "EDF" -> kernel.setAlgorithm("EDF");
            case "RR" -> kernel.setAlgorithm("RR");
            case "PRIORIDAD" -> kernel.setAlgorithm("PRIO");
            case "SRT" -> kernel.setAlgorithm("SRT");
        }
        logEvent("Algoritmo cambiado a: " + selected);
    }

    private void onSpeedChanged() {
        int ms = sliderSpeed.getValue();
        lblSpeedValue.setText(ms + "ms");
        if (clock != null) {
            clock.setCycleDuration(ms);
        }
    }

    private void onMeteorImpact() {
        if (clock != null && clock.isRunning()) {
            clock.handleInterrupt();
            setModeOs(true);
            logEvent("☄ INTERRUPCIÓN: Impacto de micro-meteorito!");
        } else {
            JOptionPane.showMessageDialog(this,
                    "La simulación no está corriendo.",
                    "Sin simulación", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void onInjectProcess() {
        injectedCount++;
        Random rnd = new Random();
        String id = "INJ" + injectedCount;
        String[] names = {"Radar_X", "GPS_Fix", "Solar_Chk", "Gyro_Cal", "Comm_TX", "Therm_Rd"};
        String name = names[rnd.nextInt(names.length)] + "_" + injectedCount;
        int inst = rnd.nextInt(5) + 2;
        int prio = rnd.nextInt(4);
        int deadline = rnd.nextInt(15) + 5;

        Process p = new Process(id, name, inst, prio, deadline, -1, 0);
        kernel.addProcess(p);
        logEvent("⊕ Inyectado: " + name + " (prio=" + prio
                + ", inst=" + inst + ", deadline=" + deadline + ")");
        refreshAllTables();
    }

    // =====================================================================
    // SERIALIZACIÓN
    // =====================================================================

    private void onSaveState() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("mision_state.dat"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new FileOutputStream(fc.getSelectedFile()))) {
                // Serializar todos los procesos de todas las colas
                ListaDobleEnlazada<Process> allProcesses = new ListaDobleEnlazada<>();
                copyQueueToList(kernel.getMemory().getReadyQueue(), allProcesses);
                copyQueueToList(kernel.getMemory().getBlockedQueue(), allProcesses);
                copyQueueToList(kernel.getMemory().getSuspendedReadyQueue(), allProcesses);
                copyQueueToList(kernel.getMemory().getSuspendedBlockedQueue(), allProcesses);

                Process cpuProcess = kernel.getCpu().getCurrentProcess();
                if (cpuProcess != null) allProcesses.addLast(cpuProcess);

                // Escribir como array
                int size = allProcesses.getSize();
                oos.writeInt(size);
                for (int i = 0; i < size; i++) {
                    oos.writeObject(allProcesses.get(i));
                }

                // Guardar algoritmo y quantum
                oos.writeObject(kernel.getTipoActual().name());
                oos.writeInt(kernel.getQuantum());

                logEvent("💾 Estado guardado: " + fc.getSelectedFile().getName()
                        + " (" + size + " procesos)");
            } catch (IOException ex) {
                logEvent("ERROR al guardar: " + ex.getMessage());
            }
        }
    }

    private void onLoadState() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new FileInputStream(fc.getSelectedFile()))) {

                // Detener simulación si está corriendo
                if (simulationStarted) onStop();

                // Nuevo kernel limpio
                kernel = new Kernel();

                int size = ois.readInt();
                for (int i = 0; i < size; i++) {
                    Process p = (Process) ois.readObject();
                    kernel.addProcess(p);
                }

                String algName = (String) ois.readObject();
                kernel.setAlgorithm(algName);

                int quantum = ois.readInt();
                kernel.setQuantum(quantum);

                logEvent("📂 Estado cargado: " + fc.getSelectedFile().getName()
                        + " (" + size + " procesos, alg=" + algName + ")");

                refreshAllTables();
            } catch (IOException | ClassNotFoundException ex) {
                logEvent("ERROR al cargar: " + ex.getMessage());
            }
        }
    }

    private void copyQueueToList(MyQueue<Process> queue, ListaDobleEnlazada<Process> list) {
        ListaDobleEnlazada<Process> qList = queue.getList();
        for (int i = 0; i < qList.getSize(); i++) {
            list.addLast(qList.get(i));
        }
    }

    // =====================================================================
    // SIMULACIÓN
    // =====================================================================

    private void startSimulation() {
        onAlgorithmChanged();
        kernel.setQuantum(3);

        clock = new SimulationClock(sliderSpeed.getValue(), kernel);
        interruptHandler = new InterruptHandler(clock);
        clock.setInterruptHandler(interruptHandler);

        // Listener: cada ciclo actualiza la GUI
        clock.setOnCycleListener(() -> {
            SwingUtilities.invokeLater(() -> {
                refreshGUI();
            });
        });

        clock.start();
        interruptHandler.start();

        logEvent("Simulación iniciada con " + cmbAlgorithm.getSelectedItem());
    }

    private void stopAll() {
        if (clock != null) {
            clock.stopSimulation();
        }
        if (interruptHandler != null) {
            interruptHandler.stopHandler();
            interruptHandler.interrupt();
        }
    }

    // =====================================================================
    // ACTUALIZACIÓN DE GUI (siempre en EDT vía invokeLater)
    // =====================================================================

    private void refreshGUI() {
        refreshCycleCounter();
        refreshCpuMonitor();
        refreshDeadlineTracker();
        refreshAllTables();
        refreshMetrics();
        refreshCpuGraph();

        // Verificar fin de simulación
        if (kernel.isSimulationComplete()) {
            logEvent("✓ MISIÓN COMPLETADA en " + clock.getTotalCycles() + " ciclos");
            btnStartPause.setText("▶ INICIAR");
            btnStartPause.setBackground(GREEN);
            btnStop.setEnabled(false);
            cmbAlgorithm.setEnabled(true);
            simulationStarted = false;
        }
    }

    private void refreshCycleCounter() {
        int cycles = (clock != null) ? clock.getTotalCycles() : 0;
        lblCycleCounter.setText(String.format("CICLO: %04d", cycles));
    }

    private void refreshCpuMonitor() {
        Process p = kernel.getCpu().getCurrentProcess();
        if (p != null) {
            lblCpuProcessName.setText("▶ " + p.getName());
            lblCpuProcessName.setForeground(CYAN);
            lblCpuId.setText("ID: " + p.getId());
            lblCpuPc.setText("PC: " + p.getPc());
            lblCpuMar.setText("MAR: " + p.getMar());
            lblCpuPriority.setText("Prioridad: " + p.getPriority()
                    + " (eff=" + p.getEffectivePriority() + ")");
            lblCpuStatus.setText("Estado: " + p.getStatus());

            int total = p.getTotalInstructions();
            int exec = p.getExecutedInstructions();
            int pct = total > 0 ? (exec * 100 / total) : 100;
            progressCpu.setValue(pct);
            progressCpu.setString("Ejecución: " + exec + "/" + total + " (" + pct + "%)");
        } else {
            lblCpuProcessName.setText("[ CPU OCIOSA ]");
            lblCpuProcessName.setForeground(YELLOW);
            lblCpuId.setText("ID: ---");
            lblCpuPc.setText("PC: ---");
            lblCpuMar.setText("MAR: ---");
            lblCpuPriority.setText("Prioridad: ---");
            lblCpuStatus.setText("Estado: ---");
            progressCpu.setValue(0);
            progressCpu.setString("Ejecución: 0%");
        }
    }

    private void refreshDeadlineTracker() {
        Process p = kernel.getCpu().getCurrentProcess();
        if (p != null) {
            int remaining = p.getRemainingDeadline();
            int original = p.getDeadline();
            lblDeadlineValue.setText(String.valueOf(remaining));

            int pct = original > 0 ? (remaining * 100 / original) : 0;
            progressDeadline.setValue(pct);
            progressDeadline.setString("Deadline: " + remaining + "/" + original);

            if (remaining <= 2) {
                lblDeadlineValue.setForeground(RED);
                progressDeadline.setForeground(RED);
            } else if (remaining <= 5) {
                lblDeadlineValue.setForeground(YELLOW);
                progressDeadline.setForeground(YELLOW);
            } else {
                lblDeadlineValue.setForeground(GREEN);
                progressDeadline.setForeground(GREEN);
            }
        } else {
            lblDeadlineValue.setText("---");
            lblDeadlineValue.setForeground(TEXT_DIM);
            progressDeadline.setValue(0);
            progressDeadline.setString("Deadline: N/A");
            progressDeadline.setForeground(GREEN);
        }
    }

    private void refreshAllTables() {
        refreshTable(modelReady, kernel.getMemory().getReadyQueue());
        refreshTable(modelBlocked, kernel.getMemory().getBlockedQueue());
        refreshTable(modelSuspReady, kernel.getMemory().getSuspendedReadyQueue());
        refreshTable(modelSuspBlocked, kernel.getMemory().getSuspendedBlockedQueue());

        // Tabla de terminados: no tenemos cola, reconstruir desde contexto
        // Por ahora, mostrar vacía (los terminados salen del sistema)
        modelFinished.setRowCount(0);
    }

    private void refreshTable(DefaultTableModel model, MyQueue<Process> queue) {
        model.setRowCount(0);
        ListaDobleEnlazada<Process> list = queue.getList();
        for (int i = 0; i < list.getSize(); i++) {
            Process p = list.get(i);
            if (p != null) {
                model.addRow(new Object[]{
                        p.getId(),
                        p.getName(),
                        p.getPc(),
                        p.getMar(),
                        p.getPriority(),
                        p.getRemainingDeadline(),
                        p.getStatus()
                });
            }
        }
    }

    private void refreshMetrics() {
        int total = kernel.getTotalProcesos();
        int completed = kernel.getProcesosTerminados();
        int failed = kernel.getPlanificadorActual().getTotalFailures();
        int switches = kernel.getPlanificadorActual().getTotalContextSwitches();
        double rate = total > 0 ? (completed * 100.0 / total) : 0;
        int cycles = (clock != null) ? clock.getTotalCycles() : 0;
        double throughput = cycles > 0 ? ((double) completed / cycles) : 0;

        lblMetricTotal.setText("Procesos totales: " + total);
        lblMetricCompleted.setText("Completados: " + completed);
        lblMetricFailed.setText("Fallos de misión: " + failed);
        lblMetricRate.setText(String.format("Tasa de éxito: %.1f%%", rate));
        lblMetricThroughput.setText(String.format("Throughput: %.3f p/c", throughput));
        lblMetricContextSwitches.setText("Context Switches: " + switches);
    }

    private void refreshCpuGraph() {
        boolean busy = kernel.getCpu().isBusy();
        cpuHistory[cpuHistoryIndex % cpuHistory.length] = busy ? 100 : 0;
        cpuHistoryIndex++;

        // Shift si llenamos
        if (cpuHistoryIndex > cpuHistory.length) {
            int[] newHist = new int[cpuHistory.length];
            System.arraycopy(cpuHistory, 1, newHist, 0, cpuHistory.length - 1);
            newHist[cpuHistory.length - 1] = busy ? 100 : 0;
            cpuHistory = newHist;
        }
        cpuGraphPanel.repaint();
    }

    // =====================================================================
    // MODO OS / USER
    // =====================================================================

    private void setModeOs(boolean isOs) {
        if (isOs) {
            lblModeIndicator.setText(" SISTEMA (OS) ");
            lblModeIndicator.setBackground(YELLOW);
            startModeBlink();
        } else {
            lblModeIndicator.setText(" USUARIO (USER) ");
            lblModeIndicator.setBackground(GREEN);
            stopModeBlink();
        }
    }

    private void startModeBlink() {
        if (modeBlinkTimer != null) modeBlinkTimer.stop();
        modeBlinkTimer = new Timer(300, e -> {
            modeBlinkState = !modeBlinkState;
            lblModeIndicator.setBackground(modeBlinkState ? YELLOW : RED);
        });
        modeBlinkTimer.start();

        // Volver a USER después de 3 segundos
        Timer returnTimer = new Timer(3000, e -> setModeOs(false));
        returnTimer.setRepeats(false);
        returnTimer.start();
    }

    private void stopModeBlink() {
        if (modeBlinkTimer != null) {
            modeBlinkTimer.stop();
            modeBlinkTimer = null;
        }
    }

    // =====================================================================
    // TIMERS
    // =====================================================================

    private void initTimers() {
        // Timer de seguridad: refresca GUI cada 500ms
        Timer safetyTimer = new Timer(500, e -> {
            if (simulationStarted) {
                SwingUtilities.invokeLater(this::refreshGUI);
            }
        });
        safetyTimer.start();
    }

    // =====================================================================
    // LOG DE EVENTOS
    // =====================================================================

    private void logEvent(String message) {
        int cycle = (clock != null) ? clock.getTotalCycles() : 0;
        String entry = "[Ciclo " + cycle + "] " + message + "\n";
        SwingUtilities.invokeLater(() -> {
            txtLog.append(entry);
            txtLog.setCaretPosition(txtLog.getDocument().getLength());
        });
    }

    // =====================================================================
    // UTILIDADES DE ESTILO
    // =====================================================================

    private JPanel darkPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(BG_DARK);
        return p;
    }

    private JPanel titledPanel(String title, Color borderColor) {
        JPanel p = new JPanel();
        p.setBackground(BG_PANEL);
        Border line = BorderFactory.createLineBorder(borderColor, 1);
        TitledBorder tb = BorderFactory.createTitledBorder(line, title,
                TitledBorder.LEFT, TitledBorder.TOP, FONT_TITLE, borderColor);
        p.setBorder(tb);
        return p;
    }

    private JLabel dataLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(FONT_DATA);
        lbl.setForeground(TEXT_PRIMARY);
        return lbl;
    }

    private JLabel metricLabel(String text) {
        JLabel lbl = new JLabel("  " + text);
        lbl.setFont(FONT_DATA);
        lbl.setForeground(TEXT_PRIMARY);
        return lbl;
    }

    private JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BUTTON);
        btn.setBackground(bg);
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.brighter(), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel createSeparator() {
        JLabel sep = new JLabel("│");
        sep.setFont(FONT_TITLE);
        sep.setForeground(new Color(60, 60, 80));
        return sep;
    }

    // =====================================================================
    // MAIN
    // =====================================================================

    public static void main(String[] args) {
        // Look and Feel oscuro
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            UIManager.put("Panel.background", BG_DARK);
            UIManager.put("OptionPane.background", BG_PANEL);
            UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        } catch (Exception e) { /* usar default */ }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}