/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package algoritmos;

import estructuras.ListaDobleEnlazada;
import estructuras.MyQueue;
import modelos.Process;

/**
 * Planificador Round Robin (RR) con Quantum Dinámico.
 * Cada proceso recibe un máximo de 'quantum' ciclos de CPU antes de ser expulsado.
 * Incluye detección de fallos de misión y advertencia de inanición de deadlines.
 * 
 * @author Francisco
 */
public class PlanificadorRR implements IPlanificador {

    // --- Quantum dinámico (modificable desde GUI o Kernel) ---
    private int quantum;
    private int quantumCounter; // Ciclos consumidos por el proceso actual

    // --- Registro de contexto guardado al expulsar ---
    public static class SavedContext {
        private final String processId;
        private final int savedPc;
        private final int savedMar;
        private final int cycleOfPreemption;
        private final String reason; // "Quantum agotado" o "Fallo de Misión"

        public SavedContext(String processId, int pc, int mar, int cycle, String reason) {
            this.processId = processId;
            this.savedPc = pc;
            this.savedMar = mar;
            this.cycleOfPreemption = cycle;
            this.reason = reason;
        }

        public String toString() {
            return "Proceso=" + processId + " | PC=" + savedPc
                 + " | MAR=" + savedMar + " | Ciclo=" + cycleOfPreemption
                 + " | Razón=" + reason;
        }

        public String getProcessId()      { return processId; }
        public int getSavedPc()            { return savedPc; }
        public int getSavedMar()           { return savedMar; }
        public int getCycleOfPreemption()  { return cycleOfPreemption; }
        public String getReason()          { return reason; }
    }

    // --- Bitácora del satélite ---
    private final ListaDobleEnlazada<SavedContext> contextLog;
    private final ListaDobleEnlazada<Process> failedProcesses;

    public PlanificadorRR(int quantum) {
        this.quantum = quantum;
        this.quantumCounter = 0;
        this.contextLog = new ListaDobleEnlazada<>();
        this.failedProcesses = new ListaDobleEnlazada<>();
    }
    
    // --- Acciones posibles (reutilizamos el patrón de EDF + QUANTUM_EXPIRED) ---
    public enum Action {
        ASSIGN_NEW,
        QUANTUM_EXPIRED,
        NO_CHANGE,
        CPU_IDLE,
        MISSION_FAIL_CPU
    }

    // --- Resultado de planificación ---
    public static class SchedulingResult {
        private final Action action;
        private final Process assignedProcess;
        private final Process preemptedProcess;

        public SchedulingResult(Action action, Process assigned, Process preempted) {
            this.action = action;
            this.assignedProcess = assigned;
            this.preemptedProcess = preempted;
        }

        public Action getAction()            { return action; }
        public Process getAssignedProcess()  { return assignedProcess; }
        public Process getPreemptedProcess() { return preemptedProcess; }
    }

    // --- IPlanificador: seleccionar siguiente proceso ---
    public Process selectNext(MyQueue<Process> readyQueue) {
        return readyQueue.dequeue();
    }

    // --- IPlanificador: evaluar cambio de contexto ---
    public boolean shouldContextSwitch(Process runningProcess, MyQueue<Process> readyQueue, int currentCycle) {
        if (runningProcess == null) return false;

        // Fallo de misión tiene prioridad
        if (hasExceededDeadline(runningProcess)) return true;

        // Quantum agotado
        return quantumCounter >= quantum;
    }

    // --- IPlanificador: reintegrar desde E/S ---
    public void reinsertFromBlocked(Process process, MyQueue<Process> readyQueue) {
        process.setStatus("Listo");
        readyQueue.enqueue(process);
        System.out.println("[RR] " + process.getName()
                + " reintegrado desde E/S (deadline restante="
                + process.getRemainingDeadline() + ")");
    }

    // --- IPlanificador: reportes ---
    public void printReports() {
        printContextLog();
        printFailureReport();
    }

    // --- IPlanificador: nombre del algoritmo ---
    public String getAlgorithmName() {
        return "RR";
    }

    // --- Método principal: se invoca en cada ciclo de reloj ---
    public SchedulingResult schedule(MyQueue<Process> readyQueue,
                                     Process runningProcess,
                                     int currentCycle) {

        // Detectar fallos de misión en la cola de listos
        detectMissionFailures(readyQueue, currentCycle);

        // Advertir sobre procesos cerca de fallar (inanición de deadline)
        checkDeadlineStarvation(readyQueue, currentCycle);

        // Verificar si el proceso en CPU superó su deadline
        if (runningProcess != null && hasExceededDeadline(runningProcess)) {
            saveContext(runningProcess, currentCycle, "Fallo de Misión");
            registerMissionFailure(runningProcess, currentCycle);
            quantumCounter = 0;
            return new SchedulingResult(Action.MISSION_FAIL_CPU, null, runningProcess);
        }

        // CPU libre -> asignar el siguiente de la cola (FIFO)
        if (runningProcess == null) {
            Process next = readyQueue.dequeue();
            if (next != null) {
                next.setStatus("Ejecución");
                quantumCounter = 0;
                return new SchedulingResult(Action.ASSIGN_NEW, next, null);
            }
            return new SchedulingResult(Action.CPU_IDLE, null, null);
        }

        // Quantum agotado -> cambio de contexto
        if (quantumCounter >= quantum) {
            saveContext(runningProcess, currentCycle, "Quantum agotado");

            System.out.println("[Reloj: Ciclo " + currentCycle + "] -> El Proceso '"
                    + runningProcess.getName() + "' agotó su quantum ("
                    + quantum + " ciclos). Reingresando a la cola de Listos.");

            runningProcess.setStatus("Listo");
            readyQueue.enqueue(runningProcess);
            quantumCounter = 0;

            // Tomar el siguiente
            Process next = readyQueue.dequeue();
            if (next != null) {
                next.setStatus("Ejecución");
                return new SchedulingResult(Action.QUANTUM_EXPIRED, next, runningProcess);
            }
            return new SchedulingResult(Action.CPU_IDLE, null, runningProcess);
        }

        // El proceso actual sigue ejecutándose
        return new SchedulingResult(Action.NO_CHANGE, runningProcess, null);
    }

    // --- Incrementar el contador de quantum (llamar después de ejecutar instrucción) ---
    public void tickQuantum() {
        quantumCounter++;
    }

    // --- Resetear quantum al asignar nuevo proceso ---
    public void resetQuantum() {
        quantumCounter = 0;
    }

    // --- Detección de fallos de misión en la cola ---
    private void detectMissionFailures(MyQueue<Process> readyQueue, int currentCycle) {
        ListaDobleEnlazada<Process> list = readyQueue.getList();
        for (int i = list.getSize() - 1; i >= 0; i--) {
            Process p = list.get(i);
            if (p != null && hasExceededDeadline(p)) {
                registerMissionFailure(p, currentCycle);
                list.remove(p);
            }
        }
    }

    private boolean hasExceededDeadline(Process p) {
        return p.getRemainingDeadline() <= 0 && !p.isFinished();
    }

    private void registerMissionFailure(Process p, int currentCycle) {
        p.setStatus("Fallo de Misión");
        failedProcesses.addLast(p);
        System.out.println("[RR] FALLO DE MISIÓN: " + p.getName()
                + " (ID=" + p.getId() + ") en ciclo " + currentCycle
                + " - Deadline superado sin completar");
    }

    // --- Advertencia de inanición: procesos a menos de 2 ciclos de fallar ---
    private void checkDeadlineStarvation(MyQueue<Process> readyQueue, int currentCycle) {
        ListaDobleEnlazada<Process> list = readyQueue.getList();
        for (int i = 0; i < list.getSize(); i++) {
            Process p = list.get(i);
            if (p != null && !p.isFinished() && p.getRemainingDeadline() <= 2 && p.getRemainingDeadline() > 0) {
                System.out.println("[RR] ⚠ ADVERTENCIA: " + p.getName()
                        + " está a " + p.getRemainingDeadline()
                        + " ciclo(s) de fallar su deadline!");
            }
        }
    }

    // --- Guardado de contexto ---
    private void saveContext(Process process, int currentCycle, String reason) {
        SavedContext ctx = new SavedContext(
                process.getId(),
                process.getPc(),
                process.getMar(),
                currentCycle,
                reason
        );
        contextLog.addLast(ctx);
        System.out.println("[RR] Contexto guardado: " + ctx);
    }

    // --- Reportes ---
    public void printContextLog() {
        System.out.println("\n--- Bitácora de Cambios de Contexto (Round Robin) ---");
        if (contextLog.isEmpty()) {
            System.out.println("  No se realizaron cambios de contexto.");
        } else {
            for (int i = 0; i < contextLog.getSize(); i++) {
                System.out.println("  " + contextLog.get(i));
            }
        }
    }

    public void printFailureReport() {
        System.out.println("\n--- Reporte de Fallos de Misión (Round Robin) ---");
        if (failedProcesses.isEmpty()) {
            System.out.println("  Todos los procesos cumplieron su deadline.");
        } else {
            System.out.println("  Total de fallos: " + failedProcesses.getSize());
            for (int i = 0; i < failedProcesses.getSize(); i++) {
                Process p = failedProcesses.get(i);
                System.out.println("  - " + p.getId() + " (" + p.getName() + ")");
            }
        }
    }

    // --- Quantum dinámico ---
    public int getQuantum()            { return quantum; }
    public void setQuantum(int q)      { this.quantum = q; }
    public int getQuantumCounter()     { return quantumCounter; }

    // --- Getters de bitácora ---
    public ListaDobleEnlazada<SavedContext> getContextLog() { return contextLog; }
    public ListaDobleEnlazada<Process> getFailedProcesses() { return failedProcesses; }
    
    // --- IPlanificador: campos temporales ---
    private Process procesoAsignado;
    private Process procesoExpulsado;

    @Override
    public String decide(MyQueue<Process> readyQueue, Process runningProcess, int currentCycle) {
        SchedulingResult result = schedule(readyQueue, runningProcess, currentCycle);
        this.procesoAsignado = result.getAssignedProcess();
        this.procesoExpulsado = result.getPreemptedProcess();
        return switch (result.getAction()) {
            case ASSIGN_NEW -> "ASSIGN_NEW";
            case QUANTUM_EXPIRED -> "PREEMPT";
            case NO_CHANGE -> "NO_CHANGE";
            case CPU_IDLE -> "CPU_IDLE";
            case MISSION_FAIL_CPU -> "MISSION_FAIL_CPU";
        };
    }

    @Override public Process getAssignedProcess()  { return procesoAsignado; }
    @Override public Process getPreemptedProcess()  { return procesoExpulsado; }
    @Override public String getName()             { return "RR"; }

    @Override
    public String getExtraInfo(Process p) {
        return "quantum=" + quantumCounter + "/" + quantum;
    }

    @Override
    public void postExecution(Process p) {
        tickQuantum();
    }

    @Override
    public void onProcessLeavesCpu(Process p) {
        resetQuantum();
    }

    @Override public int getTotalFailures()                { return failedProcesses.getSize(); }
    @Override public int getTotalContextSwitches()       { return contextLog.getSize(); }
   
}
