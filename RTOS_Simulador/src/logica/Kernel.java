package logica;

import modelos.Process;
import modelos.CPU;
import modelos.Memory;
import java.util.concurrent.Semaphore;
import algoritmos.PlanificadorEDF;
import algoritmos.PlanificadorRR;
import algoritmos.PlanificadorPrioridad;
import algoritmos.PlanificadorSRT;
import algoritmos.IPlanificador;
import algoritmos.TipoAlgoritmo;
import estructuras.ListaDobleEnlazada;

public class Kernel {
    
    private Memory memory;
    private CPU cpu;
    private Semaphore mutex;
    
    private PlanificadorEDF schedulerEDF;
    private PlanificadorRR schedulerRR;
    private PlanificadorPrioridad schedulerPrio;
    private PlanificadorSRT schedulerSRT;

    private IPlanificador planificadorActual;
    private TipoAlgoritmo tipoActual;
    
    private int procesosTerminados;
    private int procesosFallidos;
    private int totalProcesos;
    
    private final ListaDobleEnlazada<String> eventLog;

    public Kernel() {
        this.memory = new Memory(5); // Iniciamos RAM con límite de 5
        this.cpu = new CPU();
        this.mutex = new Semaphore(1);
        
        this.schedulerEDF = new PlanificadorEDF();
        this.schedulerRR = new PlanificadorRR(3);
        this.schedulerPrio = new PlanificadorPrioridad();
        this.schedulerSRT = new PlanificadorSRT();
        
        this.tipoActual = TipoAlgoritmo.EDF;
        this.planificadorActual = schedulerEDF;
        
        this.procesosTerminados = 0;
        this.procesosFallidos = 0;
        this.totalProcesos = 0;
        this.eventLog = new ListaDobleEnlazada<>();
    }

    // --- Gestión de Memoria y Procesos ---

    public void addProcess(Process p) {
        try {
            mutex.acquire();
            if (memory.getRamUsage() < memory.getMaxRamProcesses()) {
                p.setStatus("Listo");
                memory.getReadyQueue().enqueue(p);
                System.out.println("[MEMORIA] " + p.getName() + " cargado en RAM.");
            } else {
                p.setStatus("Listo-Suspendido");
                memory.getSuspendedReadyQueue().enqueue(p);
                System.out.println("[MEMORIA SATURADA] " + p.getName() + " enviado a SWAP.");
            }
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public Process getNextProcess() {
        try {
            mutex.acquire();
            // Lógica de Swap-In
            if (memory.getReadyQueue().isEmpty() && !memory.getSuspendedReadyQueue().isEmpty()) {
                Process pFromSwap = memory.getSuspendedReadyQueue().dequeue();
                pFromSwap.setStatus("Listo");
                memory.getReadyQueue().enqueue(pFromSwap);
                System.out.println("[KERNEL] Movido de SWAP a RAM: " + pFromSwap.getName());
            }

            Process p = memory.getReadyQueue().dequeue();
            mutex.release();
            return p;
        } catch (InterruptedException e) { return null; }
    }

    // --- Gestión de Bloqueos ---

    public void blockProcess(Process p) {
        try {
            mutex.acquire();
            p.startIO();
            memory.getBlockedQueue().enqueue(p);
            cpu.release(); //Liberamos la CPU física
            mutex.release();
            System.out.println("[I/O] Proceso " + p.getName() + " bloqueado.");
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public void updateBlockedProcesses() {
        try {
            mutex.acquire();
            int size = memory.getBlockedQueue().getSize();
            for (int i = 0; i < size; i++) {
                Process p = memory.getBlockedQueue().dequeue();
                p.tickIO();
                if (p.isIoFinished()) {
                    planificadorActual.reinsertFromBlocked(p, memory.getReadyQueue());
                } else {
                    memory.getBlockedQueue().enqueue(p);
                }
            }
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }
    // --- Ciclo principal de planificación unificado ---
    /**
     * Se invoca desde el SimulationClock en cada ciclo de reloj.
     * Delega la decisión al Planificador y ejecuta la acción resultante.
     */
    public void executeCycle(int currentCycle) {
        try {
            mutex.acquire();

            // 1. Swap-In
            performSwapIn();

            // 2. Actualizar deadlines
            updateReadyDeadlines();

            // 3. Consultar al planificador activo
            Process runningProcess = cpu.getCurrentProcess();
            String action = planificadorActual.decide(
                    memory.getReadyQueue(), runningProcess, currentCycle);

            String tag = planificadorActual.getName();

            // 4. Ejecutar la decisión
            switch (action) {
                case "ASSIGN_NEW" -> {
                    Process assigned = planificadorActual.getAssignedProcess();
                    cpu.setProcess(assigned);
                    System.out.println("[Ciclo " + currentCycle + "] " + tag
                            + ": Asignando " + assigned.getName()
                            + " a CPU (" + planificadorActual.getExtraInfo(assigned) + ")");
                }
                case "PREEMPT" -> {
                    Process assigned = planificadorActual.getAssignedProcess();
                    cpu.setProcess(assigned);
                    System.out.println("[Ciclo " + currentCycle + "] " + tag
                            + ": " + assigned.getName() + " toma la CPU");
                }
                case "MISSION_FAIL_CPU" -> {
                    cpu.release();
                    procesosFallidos++;
                    logEvent(currentCycle, "Fallo de misión detectado en CPU");
                    System.out.println("[Ciclo " + currentCycle + "] " + tag
                            + ": CPU liberada por fallo de misión");

                    if (!memory.getReadyQueue().isEmpty()) {
                        String nextAction = planificadorActual.decide(
                                memory.getReadyQueue(), null, currentCycle);
                        if ("ASSIGN_NEW".equals(nextAction)) {
                            Process next = planificadorActual.getAssignedProcess();
                            cpu.setProcess(next);
                            System.out.println("[Ciclo " + currentCycle + "] " + tag
                                    + ": Asignando " + next.getName() + " tras fallo");
                        }
                    }
                }
                case "NO_CHANGE" -> { /* El proceso actual sigue */ }
                case "CPU_IDLE" -> {
                    System.out.println("[Ciclo " + currentCycle + "] CPU Ociosa...");
                }
            }

            // 5. Ejecutar instrucción si hay proceso en CPU
            Process inCpu = cpu.getCurrentProcess();
            if (inCpu != null) {
                if (inCpu.shouldBlock()) {
                    inCpu.startIO();
                    memory.getBlockedQueue().enqueue(inCpu);
                    cpu.release();
                    planificadorActual.onProcessLeavesCpu(inCpu);
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " bloqueado (E/S)");
                } else if (!inCpu.isFinished()) {
                    inCpu.executeInstruction();
                    inCpu.updateDeadline();
                    planificadorActual.postExecution(inCpu);
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " en CPU (PC=" + inCpu.getPc()
                            + ", " + planificadorActual.getExtraInfo(inCpu)
                            + ", deadline=" + inCpu.getRemainingDeadline() + ")");
                } else {
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " TERMINADO.");
                    inCpu.setStatus("Terminado");
                    cpu.release();
                    planificadorActual.onProcessLeavesCpu(inCpu);
                    procesosTerminados++;
                }
            }

            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }
    
    // --- Cambio dinámico de algoritmo ---
    public void setAlgoritmo(TipoAlgoritmo tipo) {
        try {
            mutex.acquire();
            TipoAlgoritmo anterior = this.tipoActual;
            this.tipoActual = tipo;

            switch (tipo) {
                case EDF -> this.planificadorActual = schedulerEDF;
                case RR -> this.planificadorActual = schedulerRR;
                case PRIORIDAD -> this.planificadorActual = schedulerPrio;
                case SRT -> this.planificadorActual = schedulerSRT;
                default -> this.planificadorActual = schedulerEDF;
            }

            String msg = "Sistema cambiado de " + anterior + " a " + tipo;
            System.out.println("[KERNEL] " + msg);
            logEvent(0, msg);
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public void setAlgorithm(String algorithm) {
        switch (algorithm) {
            case "EDF" -> setAlgoritmo(TipoAlgoritmo.EDF);
            case "RR" -> setAlgoritmo(TipoAlgoritmo.RR);
            case "PRIO" -> setAlgoritmo(TipoAlgoritmo.PRIORIDAD);
            case "SRT" -> setAlgoritmo(TipoAlgoritmo.SRT);
            default -> setAlgoritmo(TipoAlgoritmo.EDF);
        }
    }
    
    //---- CONFIGURACION ----
    
    // --- Quantum dinámico (para GUI) ---
    public void setQuantum(int q) {
        schedulerRR.setQuantum(q);
        System.out.println("[KERNEL] Quantum actualizado a: " + q + " ciclos");
    }
    
    public void setAgingEnabled(boolean enabled) { schedulerPrio.setAgingEnabled(enabled); }
    public void setAgingThreshold(int t)         { schedulerPrio.setAgingThreshold(t); }

    // --- Swap-In ---
    private void performSwapIn() {
        while (memory.getRamUsage() < memory.getMaxRamProcesses()
                && !memory.getSuspendedReadyQueue().isEmpty()) {
            Process fromSwap = memory.getSuspendedReadyQueue().dequeue();
            fromSwap.setStatus("Listo");
            memory.getReadyQueue().enqueue(fromSwap);
            System.out.println("[KERNEL] Swap-In: " + fromSwap.getName() + " movido a RAM");
        }
    }

    // --- Actualizar deadlines de procesos en espera ---
    private void updateReadyDeadlines() {
        var list = memory.getReadyQueue().getList();
        for (int i = 0; i < list.getSize(); i++) {
            Process p = list.get(i);
            if (p != null) p.updateDeadline();
        }
    }
    
    // --- Reportes de misión y metricas ---
     public void printMissionReports() {
        System.out.println("\n[KERNEL] Algoritmo utilizado: " + tipoActual
                + " (" + tipoActual.getDescripcion() + ")");

        System.out.println("\n--- Métricas de Misión ---");
        System.out.println("  Procesos totales:    " + totalProcesos);
        System.out.println("  Completados:         " + procesosTerminados);
        System.out.println("  Fallos de misión:    " + planificadorActual.getTotalFailures());
        System.out.println("  Cambios de contexto: " + planificadorActual.getTotalContextSwitches());
        double exitoRate = totalProcesos > 0
                ? (procesosTerminados * 100.0 / totalProcesos) : 0;
        System.out.println("  Tasa de éxito:       " + String.format("%.1f", exitoRate) + "%");

        planificadorActual.printContextLog();
        planificadorActual.printFailureReport();
        printEventLog();
    }
    
    private void logEvent(int cycle, String message) {
        eventLog.addLast("[Ciclo " + cycle + "]: " + message);
    }
    
    private void printEventLog() {
        if (!eventLog.isEmpty()) {
            System.out.println("\n--- Log de Eventos del Sistema ---");
            for (int i = 0; i < eventLog.getSize(); i++) {
                System.out.println("  " + eventLog.get(i));
            }
        }
    }
    
    // --- Condición de parada: no quedan procesos vivos ---
    public boolean isSimulationComplete() {
        return cpu.getCurrentProcess() == null
            && memory.getReadyQueue().isEmpty()
            && memory.getBlockedQueue().isEmpty()
            && memory.getSuspendedReadyQueue().isEmpty()
            && memory.getSuspendedBlockedQueue().isEmpty();
    }
    
    // Getters para que el Reloj y la GUI accedan a las piezas
    public CPU getCpu() { return cpu; }
    public Memory getMemory() { return memory; }
    public IPlanificador getPlanificadorActual() { return planificadorActual; }
    public TipoAlgoritmo getTipoActual()        { return tipoActual; }
    public int getProcesosTerminados()           { return procesosTerminados; }
    public int getProcesosFallidos()             { return procesosFallidos; }
    public int getTotalProcesos()                { return totalProcesos; }
    public int getQuantum() { return schedulerRR.getQuantum(); }
    public PlanificadorEDF getSchedulerEDF() { return schedulerEDF; }
    public PlanificadorRR getSchedulerRR() { return schedulerRR; }
    public PlanificadorPrioridad getSchedulerPrio() { return schedulerPrio; }
    public PlanificadorSRT getSchedulerSRT() { return schedulerSRT; }

}