/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package algoritmos;
import estructuras.ListaDobleEnlazada;
import estructuras.MyQueue;
import modelos.Process;

/**
  * Planificador SRT (Shortest Remaining Time) - Preemptivo.
 * Selecciona siempre al proceso con menos instrucciones restantes.
 * Si llega uno más corto que el que está en CPU, lo desaloja.
 * Empates se resuelven por FCFS (Insertion Sort estable).
 * @author Francisco
 */
public class PlanificadorSRT {
    
    // --- Acciones posibles ---
    public enum Action {
        ASSIGN_NEW,
        PREEMPT_SRT,
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

    // --- Registro de contexto guardado al desalojar ---
    public static class SavedContext {
        private final String processId;
        private final int savedPc;
        private final int savedMar;
        private final int cycleOfPreemption;
        private final String reason;

        public SavedContext(String processId, int pc, int mar, int cycle, String reason) {
            this.processId = processId;
            this.savedPc = pc;
            this.savedMar = mar;
            this.cycleOfPreemption = cycle;
            this.reason = reason;
        }

        @Override
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

    public PlanificadorSRT() {
        this.contextLog = new ListaDobleEnlazada<>();
        this.failedProcesses = new ListaDobleEnlazada<>();
    }

    // --- Método principal: se invoca en cada ciclo de reloj ---
    public SchedulingResult schedule(MyQueue<Process> readyQueue,
                                     Process runningProcess,
                                     int currentCycle) {

        // Detectar fallos de misión en la cola de listos
        detectMissionFailures(readyQueue, currentCycle);

        // Verificar si el proceso en CPU superó su deadline
        if (runningProcess != null && hasExceededDeadline(runningProcess)) {
            saveContext(runningProcess, currentCycle, "Fallo de Misión");
            registerMissionFailure(runningProcess, currentCycle);
            return new SchedulingResult(Action.MISSION_FAIL_CPU, null, runningProcess);
        }

        // Ordenar cola por instrucciones restantes (menor primero, FCFS en empates)
        sortByRemainingTime(readyQueue);

        // CPU libre -> asignar el más corto
        if (runningProcess == null) {
            Process next = readyQueue.dequeue();
            if (next != null) {
                next.setStatus("Ejecución");
                return new SchedulingResult(Action.ASSIGN_NEW, next, null);
            }
            return new SchedulingResult(Action.CPU_IDLE, null, null);
        }

        // CPU ocupada -> evaluar desalojo por tiempo restante
        Process candidate = readyQueue.peek();
        if (candidate != null
                && candidate.getRemainingInstructions() < runningProcess.getRemainingInstructions()) {

            saveContext(runningProcess, currentCycle,
                    "Proceso " + candidate.getName() + " es más corto ("
                    + candidate.getRemainingInstructions() + " vs "
                    + runningProcess.getRemainingInstructions() + " inst)");

            System.out.println("[SRT] Expulsión: " + runningProcess.getName()
                    + " (restantes=" + runningProcess.getRemainingInstructions()
                    + ") sale, entra " + candidate.getName()
                    + " (restantes=" + candidate.getRemainingInstructions() + ")");

            runningProcess.setStatus("Listo");
            readyQueue.dequeue();
            readyQueue.enqueue(runningProcess);
            candidate.setStatus("Ejecución");

            return new SchedulingResult(Action.PREEMPT_SRT, candidate, runningProcess);
        }

        return new SchedulingResult(Action.NO_CHANGE, runningProcess, null);
    }

    // --- Ordenamiento por instrucciones restantes (Insertion Sort estable) ---
    public void sortByRemainingTime(MyQueue<Process> queue) {
        ListaDobleEnlazada<Process> list = queue.getList();
        int size = list.getSize();
        if (size <= 1) return;

        Process[] temp = new Process[size];
        for (int i = 0; i < size; i++) {
            temp[i] = list.get(i);
        }

        // Insertion sort estable: menor remainingInstructions primero
        // Al ser estable, empates mantienen orden FCFS
        for (int i = 1; i < size; i++) {
            Process key = temp[i];
            int j = i - 1;
            while (j >= 0 && temp[j].getRemainingInstructions() > key.getRemainingInstructions()) {
                temp[j + 1] = temp[j];
                j--;
            }
            temp[j + 1] = key;
        }

        while (!queue.isEmpty()) {
            queue.dequeue();
        }
        for (int i = 0; i < size; i++) {
            queue.enqueue(temp[i]);
        }
    }

    // --- Detección de fallos de misión ---
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
        System.out.println("[SRT] FALLO DE MISIÓN: " + p.getName()
                + " (ID=" + p.getId() + ") en ciclo " + currentCycle
                + " - Deadline superado sin completar");
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
        System.out.println("[SRT] Contexto guardado: " + ctx);
    }

    // --- Reintegración de procesos que vuelven de E/S ---
    public void reinsertFromBlocked(Process process, MyQueue<Process> readyQueue) {
        process.setStatus("Listo");
        readyQueue.enqueue(process);
        sortByRemainingTime(readyQueue);
        System.out.println("[SRT] " + process.getName()
                + " reintegrado desde E/S (restantes="
                + process.getRemainingInstructions() + ")");
    }

    // --- Reportes ---
    public void printContextLog() {
        System.out.println("\n--- Bitácora de Cambios de Contexto (SRT) ---");
        if (contextLog.isEmpty()) {
            System.out.println("  No se realizaron cambios de contexto.");
        } else {
            for (int i = 0; i < contextLog.getSize(); i++) {
                System.out.println("  " + contextLog.get(i));
            }
        }
    }

    public void printFailureReport() {
        System.out.println("\n--- Reporte de Fallos de Misión (SRT) ---");
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

    // --- Getters de bitácora ---
    public ListaDobleEnlazada<SavedContext> getContextLog() { return contextLog; }
    public ListaDobleEnlazada<Process> getFailedProcesses() { return failedProcesses; }
}
