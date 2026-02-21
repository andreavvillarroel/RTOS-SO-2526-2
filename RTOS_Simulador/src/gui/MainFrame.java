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
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
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
    private JTextPane txtLog;

    // === Controles ===
    private JButton btnStartPause, btnStop, btnInject;
    
    // === RAM dinámica ===
    private JSpinner spinnerRam;
    private JProgressBar progressRam;
    
    // === Control de eventos ===
    private boolean suppressAlgorithmEvent = false;
    
    // === Métricas adicionales ===
    private JLabel lblMetricWaitAvg;

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
        
        // Procesos con E/S intensiva (fuerzan Bloqueado-Suspendido con RAM=5)
        // E/S temprana + duración larga = se bloquean rápido y ocupan espacio
        kernel.addProcess(new Process("P8",  "Radar_IO",   8, 1, 30, 1, 5));  // Se bloquea en inst 1, 5 ciclos E/S
        kernel.addProcess(new Process("P9",  "GPS_Link",   7, 0, 25, 2, 4));  // Se bloquea en inst 2, 4 ciclos E/S
        kernel.addProcess(new Process("P10", "Solar_Scan", 6, 2, 22, 1, 6));  // Se bloquea en inst 1, 6 ciclos E/S
        kernel.addProcess(new Process("P11", "Gyro_Sync",  9, 1, 28, 3, 5));  // Se bloquea en inst 3, 5 ciclos E/S
        kernel.addProcess(new Process("P12", "Comm_Burst", 5, 0, 20, 1, 4));  // Se bloquea en inst 1, 4 ciclos E/S
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
        
        // RAM dinámica
        JLabel lblRam = new JLabel("RAM:");
        lblRam.setFont(FONT_TITLE);
        lblRam.setForeground(TEXT_PRIMARY);
        bar.add(lblRam);

        spinnerRam = new JSpinner(new SpinnerNumberModel(
        kernel.getMemory().getMaxRamProcesses(), 1, 50, 1));
        spinnerRam.setFont(FONT_DATA);
        spinnerRam.setPreferredSize(new Dimension(55, 30));
        bar.add(spinnerRam);

        JButton btnRam = styledButton("SET", YELLOW);
        btnRam.setForeground(Color.BLACK);
        btnRam.addActionListener(e -> onRamLimitChanged());
        bar.add(btnRam);

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
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
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
        gbc.gridy = 3;
        JButton btnSave = styledButton("💾 GUARDAR MISIÓN", CYAN);
        btnSave.addActionListener(e -> onSaveState());
        panel.add(btnSave, gbc);

        gbc.gridy = 4;
        JButton btnLoad = styledButton("📂 CARGAR MISIÓN", CYAN);
        btnLoad.addActionListener(e -> onLoadState());
        panel.add(btnLoad, gbc);

        return panel;
    }
//    private void onStressLoad() {        // 1. Usamos fábrica para generar 20 procesos
//        int cantidad = 20;
//        estructuras.ListaDobleEnlazada<modelos.Process> nuevosProcesos = utils.ProcessFactory.createStressLoad(cantidad);
//        for (int i = 0; i < nuevosProcesos.getSize(); i++) {
//            kernel.addProcess(nuevosProcesos.get(i));
//        }
//        logEvent("⚡ CARGA DE ESTRÉS: " + cantidad + " procesos generados por ProcessFactory.");
//        refreshAllTables();
//    }

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
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS));
        lblMetricTotal = metricLabel("Procesos totales: 0");
        lblMetricCompleted = metricLabel("Completados: 0");
        lblMetricFailed = metricLabel("Fallos de misión: 0");
        lblMetricRate = metricLabel("Tasa de éxito: 0.0%");
        lblMetricThroughput = metricLabel("Throughput: 0.00");
        lblMetricContextSwitches = metricLabel("Context Switches: 0");
        lblMetricWaitAvg = metricLabel("Espera prom: 0.0 ciclos");
        
         metrics.add(lblMetricTotal);
        metrics.add(Box.createVerticalStrut(3));
        metrics.add(lblMetricCompleted);
        metrics.add(Box.createVerticalStrut(3));
        metrics.add(lblMetricFailed);
        metrics.add(Box.createVerticalStrut(3));
        metrics.add(lblMetricRate);
        metrics.add(Box.createVerticalStrut(3));
        metrics.add(lblMetricThroughput);
        metrics.add(Box.createVerticalStrut(3));
        metrics.add(lblMetricContextSwitches);
        metrics.add(Box.createVerticalStrut(3));
        metrics.add(lblMetricWaitAvg);
        metrics.add(Box.createVerticalStrut(6));
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

        txtLog = new JTextPane();
        txtLog.setFont(FONT_SMALL);
        txtLog.setBackground(BG_TABLE);
        txtLog.setForeground(GREEN);
        txtLog.setCaretColor(GREEN);
        txtLog.setEditable(false);
        
        // Crear estilos de colores
        StyledDocument doc = txtLog.getStyledDocument();
        Style styleNormal = doc.addStyle("normal", null);
        StyleConstants.setForeground(styleNormal, GREEN);
        StyleConstants.setFontFamily(styleNormal, "Consolas");
        StyleConstants.setFontSize(styleNormal, 11);

        Style styleMeteor = doc.addStyle("meteor", null);
        StyleConstants.setForeground(styleMeteor, new Color(255, 100, 50));
        StyleConstants.setFontFamily(styleMeteor, "Consolas");
        StyleConstants.setFontSize(styleMeteor, 11);
        StyleConstants.setBold(styleMeteor, true);

        Style styleAlert = doc.addStyle("alert", null);
        StyleConstants.setForeground(styleAlert, RED);
        StyleConstants.setFontFamily(styleAlert, "Consolas");
        StyleConstants.setFontSize(styleAlert, 11);
        StyleConstants.setBold(styleAlert, true);

        Style styleIO = doc.addStyle("io", null);
        StyleConstants.setForeground(styleIO, YELLOW);
        StyleConstants.setFontFamily(styleIO, "Consolas");
        StyleConstants.setFontSize(styleIO, 11);

        Style styleComplete = doc.addStyle("complete", null);
        StyleConstants.setForeground(styleComplete, CYAN);
        StyleConstants.setFontFamily(styleComplete, "Consolas");
        StyleConstants.setFontSize(styleComplete, 11);

        Style styleSystem = doc.addStyle("system", null);
        StyleConstants.setForeground(styleSystem, PURPLE);
        StyleConstants.setFontFamily(styleSystem, "Consolas");
        StyleConstants.setFontSize(styleSystem, 11);
        StyleConstants.setBold(styleSystem, true);
        
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
            cmbAlgorithm.setEnabled(true);
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
        if (suppressAlgorithmEvent) return;
        applySelectedAlgorithm();
    }

    /**
     * Aplica el algoritmo seleccionado en el combo al Kernel.
     * No genera log (para evitar duplicados al iniciar).
     */
    private void applySelectedAlgorithm() {
    String selected = (String) cmbAlgorithm.getSelectedItem();
    int cycle = (clock != null) ? clock.getTotalCycles() : 0;
    switch (selected) {
        case "FCFS" -> kernel.setAlgorithm("FCFS", cycle);
        case "EDF" -> kernel.setAlgorithm("EDF", cycle);
        case "RR" -> kernel.setAlgorithm("RR", cycle);
        case "PRIORIDAD" -> kernel.setAlgorithm("PRIO", cycle);
        case "SRT" -> kernel.setAlgorithm("SRT", cycle);
    }
}

    private void onSpeedChanged() {
        int ms = sliderSpeed.getValue();
        lblSpeedValue.setText(ms + "ms");
        if (clock != null) {
            clock.setCycleDuration(ms);
        }
    }
    
    private void onRamLimitChanged() {
        int newLimit = (int) spinnerRam.getValue();
        int ciclo = (clock != null) ? clock.getTotalCycles() : 0; // Obtener ciclo actual
        kernel.updateRamLimit(newLimit,ciclo);
        logEvent("Límite de RAM actualizado a " + newLimit + " procesos");
        refreshAllTables();
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
    
    private void onStressLoad() {
        ListaDobleEnlazada<Process> batch = ProcessFactory.createStressLoad(20);
        kernel.addBulkProcesses(batch);
        logEvent("⚡ CARGA DE ESTRÉS: 20 procesos inyectados de golpe");
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
                kernel.setAlgorithm(algName,0);

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
        // Aplicar algoritmo sin disparar evento duplicado
        suppressAlgorithmEvent = true;
        applySelectedAlgorithm();
        suppressAlgorithmEvent = false;
        kernel.setQuantum(3);

        clock = new SimulationClock(sliderSpeed.getValue(), kernel);
        interruptHandler = new InterruptHandler(clock);
        clock.setInterruptHandler(interruptHandler);
        
        // Listener de eventos del Kernel -> GUI log con colores
        kernel.setEventListener(message -> {
            SwingUtilities.invokeLater(() -> {
                appendColoredLog(message);
            });
        });

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
        refreshTable(modelFinished, kernel.getMemory().getFinishedQueue());
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
        double avgWait = kernel.getAvgWaitTime();


        lblMetricTotal.setText("Procesos totales: " + total);
        lblMetricCompleted.setText("Completados: " + completed);
        lblMetricFailed.setText("Fallos de misión: " + failed);
        lblMetricRate.setText(String.format("Tasa de éxito: %.1f%%", rate));
        lblMetricThroughput.setText(String.format("Throughput: %.3f p/c", throughput));
        lblMetricContextSwitches.setText("Context Switches: " + switches);
        lblMetricWaitAvg.setText(String.format("  Espera prom: %.1f ciclos", avgWait));
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
            appendColoredLog(entry);
        });
    }
    
     private void appendColoredLog(String message) {
        StyledDocument doc = txtLog.getStyledDocument();
        String styleName;

        if (message.contains("☄") || message.contains("METEORITO") || message.contains("INTERRUPCIÓN")
                || message.contains("meteorito")) {
            styleName = "meteor";
        } else if (message.contains("¡ALERTA!") || message.contains("FALLO") || message.contains("Fallo")
                || message.contains("fallo") || message.contains("deadline")) {
            styleName = "alert";
        } else if (message.contains("E/S") || message.contains("Bloqueado") || message.contains("bloqueado")) {
            styleName = "io";
        } else if (message.contains("✓") || message.contains("completado") || message.contains("TERMINADO")) {
            styleName = "complete";
        } else if (message.contains("cambiado") || message.contains("RAM") || message.contains("Swap")
                || message.contains("ESTRÉS") || message.contains("Inyectado") || message.contains("inyectado")
                || message.contains("Límite") || message.contains("iniciada")) {
            styleName = "system";
        } else {
            styleName = "normal";
        }

        try {
            Style style = doc.getStyle(styleName);
            if (style == null) style = doc.getStyle("normal");
            doc.insertString(doc.getLength(), message + "\n", style);
            txtLog.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) { /* ignorar */ }
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