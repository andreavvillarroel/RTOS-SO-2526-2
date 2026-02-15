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
    // --- Ciclo principal de planificación EDF ---
    /**
     * Se invoca desde el SimulationClock en cada ciclo de reloj.
     * Delega la decisión al PlanificadorEDF y ejecuta la acción resultante.
     */
    public void executeEdfCycle(int currentCycle) {
        try {
            mutex.acquire();

            // Swap-In: mover de SWAP a RAM si hay espacio
            performSwapIn();

            // Actualizar deadlines de procesos en espera
            updateReadyDeadlines();

            // Consultar al planificador EDF
            Process runningProcess = cpu.getCurrentProcess();
            PlanificadorEDF.SchedulingResult result = schedulerEDF.schedule(
                    memory.getReadyQueue(), runningProcess, currentCycle);

            // Ejecutar la decisión
            switch (result.getAction()) {
                case ASSIGN_NEW -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] EDF: Asignando "
                            + result.getAssignedProcess().getName() + " a CPU");
                }
                case PREEMPT -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] EDF: "
                            + result.getAssignedProcess().getName() + " toma la CPU");
                }
                case MISSION_FAIL_CPU -> {
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] EDF: CPU liberada por fallo de misión");
                    if (!memory.getReadyQueue().isEmpty()) {
                        Process next = memory.getReadyQueue().dequeue();
                        next.setStatus("Ejecución");
                        cpu.setProcess(next);
                        System.out.println("[Ciclo " + currentCycle + "] EDF: Asignando "
                                + next.getName() + " tras fallo");
                    }
                }
                case NO_CHANGE -> { /* El proceso actual sigue */ }
                case CPU_IDLE -> {
                    System.out.println("[Ciclo " + currentCycle + "] CPU Ociosa...");
                }
            }

            // Ejecutar instrucción si hay proceso en CPU
            Process inCpu = cpu.getCurrentProcess();
            if (inCpu != null) {
                if (inCpu.shouldBlock()) {
                    inCpu.startIO();
                    memory.getBlockedQueue().enqueue(inCpu);
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " bloqueado (E/S)");
                } else if (!inCpu.isFinished()) {
                    inCpu.executeInstruction();
                    inCpu.updateDeadline();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " en CPU (PC=" + inCpu.getPc()
                            + ", deadline=" + inCpu.getRemainingDeadline() + ")");
                } else {
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " TERMINADO.");
                    inCpu.setStatus("Terminado");
                    cpu.release();
                }
            }

            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

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
    
    // --- Condición de parada: no quedan procesos vivos ---
    public boolean isSimulationComplete() {
        return cpu.getCurrentProcess() == null
            && memory.getReadyQueue().isEmpty()
            && memory.getBlockedQueue().isEmpty()
            && memory.getSuspendedReadyQueue().isEmpty()
            && memory.getSuspendedBlockedQueue().isEmpty();
    }
    
    // --- Ciclo principal de planificación Round Robin ---
    
    public void executeRrCycle(int currentCycle) {
        try {
            mutex.acquire();

            // Swap-In
            performSwapIn();

            // Actualizar deadlines de procesos en espera
            updateReadyDeadlines();

            // Consultar al planificador RR
            Process runningProcess = cpu.getCurrentProcess();
            PlanificadorRR.SchedulingResult result = schedulerRR.schedule(
                    memory.getReadyQueue(), runningProcess, currentCycle);

            // Ejecutar la decisión
            switch (result.getAction()) {
                case ASSIGN_NEW -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] RR: Asignando "
                            + result.getAssignedProcess().getName() + " a CPU (quantum="
                            + schedulerRR.getQuantum() + ")");
                }
                case QUANTUM_EXPIRED -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] RR: "
                            + result.getAssignedProcess().getName() + " toma la CPU");
                }
                case MISSION_FAIL_CPU -> {
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] RR: CPU liberada por fallo de misión");
                    if (!memory.getReadyQueue().isEmpty()) {
                        Process next = memory.getReadyQueue().dequeue();
                        next.setStatus("Ejecución");
                        cpu.setProcess(next);
                        schedulerRR.resetQuantum();
                        System.out.println("[Ciclo " + currentCycle + "] RR: Asignando "
                                + next.getName() + " tras fallo");
                    }
                }
                case NO_CHANGE -> { /* El proceso actual sigue */ }
                case CPU_IDLE -> {
                    System.out.println("[Ciclo " + currentCycle + "] CPU Ociosa...");
                }
            }

            // Ejecutar instrucción si hay proceso en CPU
            Process inCpu = cpu.getCurrentProcess();
            if (inCpu != null) {
                if (inCpu.shouldBlock()) {
                    inCpu.startIO();
                    memory.getBlockedQueue().enqueue(inCpu);
                    cpu.release();
                    schedulerRR.resetQuantum();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " bloqueado (E/S)");
                } else if (!inCpu.isFinished()) {
                    inCpu.executeInstruction();
                    inCpu.updateDeadline();
                    schedulerRR.tickQuantum();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " en CPU (PC=" + inCpu.getPc()
                            + ", quantum=" + schedulerRR.getQuantumCounter()
                            + "/" + schedulerRR.getQuantum()
                            + ", deadline=" + inCpu.getRemainingDeadline() + ")");
                } else {
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " TERMINADO.");
                    inCpu.setStatus("Terminado");
                    cpu.release();
                    schedulerRR.resetQuantum();
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


    // --- Quantum dinámico (para GUI) ---
    public void setQuantum(int q) {
        schedulerRR.setQuantum(q);
        System.out.println("[KERNEL] Quantum actualizado a: " + q + " ciclos");
    }

    // --- Ciclo principal de planificación por Prioridad ---
    public void executePriorityCycle(int currentCycle) {
        try {
            mutex.acquire();

            // Swap-In
            performSwapIn();

            // Actualizar deadlines de procesos en espera
            updateReadyDeadlines();

            // Consultar al planificador de Prioridad
            Process runningProcess = cpu.getCurrentProcess();
            PlanificadorPrioridad.SchedulingResult result = schedulerPrio.schedule(
                    memory.getReadyQueue(), runningProcess, currentCycle);

            // Ejecutar la decisión
            switch (result.getAction()) {
                case ASSIGN_NEW -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] PRIO: Asignando "
                            + result.getAssignedProcess().getName()
                            + " a CPU (prioridad="
                            + result.getAssignedProcess().getEffectivePriority() + ")");
                }
                case PREEMPT_PRIORITY -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] PRIO: "
                            + result.getAssignedProcess().getName() + " toma la CPU");
                }
                case MISSION_FAIL_CPU -> {
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] PRIO: CPU liberada por fallo de misión");
                    if (!memory.getReadyQueue().isEmpty()) {
                        Process next = memory.getReadyQueue().dequeue();
                        next.setStatus("Ejecución");
                        next.resetWaitCycles();
                        cpu.setProcess(next);
                        System.out.println("[Ciclo " + currentCycle + "] PRIO: Asignando "
                                + next.getName() + " tras fallo");
                    }
                }
                case NO_CHANGE -> { /* El proceso actual sigue */ }
                case CPU_IDLE -> {
                    System.out.println("[Ciclo " + currentCycle + "] CPU Ociosa...");
                }
            }

            // Ejecutar instrucción si hay proceso en CPU
            Process inCpu = cpu.getCurrentProcess();
            if (inCpu != null) {
                if (inCpu.shouldBlock()) {
                    inCpu.startIO();
                    memory.getBlockedQueue().enqueue(inCpu);
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " bloqueado (E/S)");
                } else if (!inCpu.isFinished()) {
                    inCpu.executeInstruction();
                    inCpu.updateDeadline();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " en CPU (PC=" + inCpu.getPc()
                            + ", prio=" + inCpu.getEffectivePriority()
                            + ", deadline=" + inCpu.getRemainingDeadline() + ")");
                } else {
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " TERMINADO.");
                    inCpu.setStatus("Terminado");
                    cpu.release();
                }
            }

            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }
    
    // --- Ciclo principal de planificación SRT ---
    public void executeSrtCycle(int currentCycle) {
        try {
            mutex.acquire();

            // Swap-In
            performSwapIn();

            // Actualizar deadlines de procesos en espera
            updateReadyDeadlines();

            // Consultar al planificador SRT
            Process runningProcess = cpu.getCurrentProcess();
            PlanificadorSRT.SchedulingResult result = schedulerSRT.schedule(
                    memory.getReadyQueue(), runningProcess, currentCycle);

            // Ejecutar la decisión
            switch (result.getAction()) {
                case ASSIGN_NEW -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] SRT: Asignando "
                            + result.getAssignedProcess().getName()
                            + " a CPU (restantes="
                            + result.getAssignedProcess().getRemainingInstructions() + ")");
                }
                case PREEMPT_SRT -> {
                    cpu.setProcess(result.getAssignedProcess());
                    System.out.println("[Ciclo " + currentCycle + "] SRT: "
                            + result.getAssignedProcess().getName() + " toma la CPU");
                }
                case MISSION_FAIL_CPU -> {
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] SRT: CPU liberada por fallo de misión");
                    if (!memory.getReadyQueue().isEmpty()) {
                        Process next = memory.getReadyQueue().dequeue();
                        next.setStatus("Ejecución");
                        cpu.setProcess(next);
                        System.out.println("[Ciclo " + currentCycle + "] SRT: Asignando "
                                + next.getName() + " tras fallo");
                    }
                }
                case NO_CHANGE -> { /* El proceso actual sigue */ }
                case CPU_IDLE -> {
                    System.out.println("[Ciclo " + currentCycle + "] CPU Ociosa...");
                }
            }

            // Ejecutar instrucción si hay proceso en CPU
            Process inCpu = cpu.getCurrentProcess();
            if (inCpu != null) {
                if (inCpu.shouldBlock()) {
                    inCpu.startIO();
                    memory.getBlockedQueue().enqueue(inCpu);
                    cpu.release();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " bloqueado (E/S)");
                } else if (!inCpu.isFinished()) {
                    inCpu.executeInstruction();
                    inCpu.updateDeadline();
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " en CPU (PC=" + inCpu.getPc()
                            + ", restantes=" + inCpu.getRemainingInstructions()
                            + ", deadline=" + inCpu.getRemainingDeadline() + ")");
                } else {
                    System.out.println("[Ciclo " + currentCycle + "] "
                            + inCpu.getName() + " TERMINADO.");
                    inCpu.setStatus("Terminado");
                    cpu.release();
                }
            }

            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
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