package logica;

import modelos.Process;
import modelos.CPU;
import modelos.Memory;
import java.util.concurrent.Semaphore;
import algoritmos.PlanificadorEDF;
import algoritmos.PlanificadorRR;
import algoritmos.PlanificadorPrioridad;
import algoritmos.PlanificadorSRT;

public class Kernel {
    private Memory memory;
    private CPU cpu;
    private Semaphore mutex;
    private PlanificadorEDF schedulerEDF;
    private PlanificadorRR schedulerRR;
    private PlanificadorPrioridad schedulerPrio;
    private PlanificadorSRT schedulerSRT;
    private String activeAlgorithm;

    public Kernel() {
        this.memory = new Memory(5); // Iniciamos RAM con límite de 5
        this.cpu = new CPU();
        this.mutex = new Semaphore(1);
        this.schedulerEDF = new PlanificadorEDF();
        this.schedulerRR = new PlanificadorRR(3);
        this.schedulerPrio = new PlanificadorPrioridad();
        this.schedulerSRT = new PlanificadorSRT();
        this.activeAlgorithm = "EDF"; // Algoritmo por defecto
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
                   // Reintegrar vía el algoritmo activo
                    if ("RR".equals(activeAlgorithm)) {
                        schedulerRR.reinsertFromBlocked(p, memory.getReadyQueue());
                        } else if ("PRIO".equals(activeAlgorithm)) {
                        schedulerPrio.reinsertFromBlocked(p, memory.getReadyQueue());
                        } else if ("SRT".equals(activeAlgorithm)) {
                        schedulerSRT.reinsertFromBlocked(p, memory.getReadyQueue());
                    } else {
                        schedulerEDF.reinsertFromBlocked(p, memory.getReadyQueue());
                    }
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

    // --- Cambio dinámico de algoritmo (patrón Strategy) ---
    public void setAlgorithm(String algorithm) {
        this.activeAlgorithm = algorithm;
        System.out.println("[KERNEL] Algoritmo cambiado a: " + algorithm);
    }

    public String getActiveAlgorithm() { return activeAlgorithm; }

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
    
    // --- Reportes de misión ---
    public void printMissionReports() {
        System.out.println("\n[KERNEL] Algoritmo utilizado: " + activeAlgorithm);
        if ("RR".equals(activeAlgorithm)) {
            schedulerRR.printContextLog();
            schedulerRR.printFailureReport();
        } else if ("PRIO".equals(activeAlgorithm)) {
            schedulerPrio.printContextLog();
            schedulerPrio.printFailureReport();
        } else if ("SRT".equals(activeAlgorithm)) {
            schedulerSRT.printContextLog();
            schedulerSRT.printFailureReport();
        } else {
            schedulerEDF.printContextLog();
            schedulerEDF.printFailureReport();
        }
    }
    
    // Getters para que el Reloj y la GUI accedan a las piezas
    public CPU getCpu() { return cpu; }
    public Memory getMemory() { return memory; }
    public PlanificadorEDF getSchedulerEDF() { return schedulerEDF; }
    public int getQuantum() { return schedulerRR.getQuantum(); }
    public PlanificadorRR getSchedulerRR() { return schedulerRR; }
    public PlanificadorPrioridad getSchedulerPrio() { return schedulerPrio; }
    public PlanificadorSRT getSchedulerSRT() { return schedulerSRT; }

    // --- Ciclo genérico: delega al algoritmo activo ---
    public void executeCycle(int currentCycle) {
        if ("RR".equals(activeAlgorithm)) {
            executeRrCycle(currentCycle);
        } else if ("PRIO".equals(activeAlgorithm)) {
            executePriorityCycle(currentCycle);
        } else if ("SRT".equals(activeAlgorithm)) {
            executeSrtCycle(currentCycle);
        } else {
            executeEdfCycle(currentCycle);
        }
    }  
}