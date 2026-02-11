/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package algoritmos;

import estructuras.ListaDobleEnlazada;
import estructuras.MyQueue;
import modelos.Process;

/**
 *
 * @author Francisco
 */
public class PlanificadorEDF {

    // --- Constantes de estado ---
    public static final String READY          = "Listo";
    public static final String RUNNING        = "Ejecución";
    public static final String BLOCKED        = "Bloqueado";
    public static final String FINISHED       = "Terminado";
    public static final String MISSION_FAILED = "Fallo de Misión";

    // --- Registro de contexto guardado al expulsar un proceso ---
    public static class SavedContext {
        private final String processId;
        private final int savedPc;
        private final int savedMar;
        private final int cycleOfPreemption;

        public SavedContext(String processId, int pc, int mar, int cycle) {
            this.processId = processId;
            this.savedPc = pc;
            this.savedMar = mar;
            this.cycleOfPreemption = cycle;
        }

        @Override
        public String toString() {
            return "Proceso=" + processId + " | PC=" + savedPc
                 + " | MAR=" + savedMar + " | Ciclo=" + cycleOfPreemption;
        }

        public String getProcessId()      { return processId; }
        public int getSavedPc()            { return savedPc; }
        public int getSavedMar()           { return savedMar; }
        public int getCycleOfPreemption()  { return cycleOfPreemption; }
    }

    // --- Bitácora del satélite ---
    private final ListaDobleEnlazada<SavedContext> contextLog;
    private final ListaDobleEnlazada<Process> failedProcesses;

    public PlanificadorEDF() {
        this.contextLog = new ListaDobleEnlazada<>();
        this.failedProcesses = new ListaDobleEnlazada<>();
    }

    // --- Acciones posibles que retorna el planificador ---
    public enum Action {
        ASSIGN_NEW,       // CPU estaba libre, se asignó un proceso
        PREEMPT,          // Se expulsó al proceso actual por uno más urgente
        NO_CHANGE,        // El proceso actual sigue ejecutándose
        CPU_IDLE,         // No hay procesos en cola de listos
        MISSION_FAIL_CPU  // El proceso en CPU superó su deadline
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

    // --- Método principal: se invoca en cada ciclo de reloj ---
    public SchedulingResult schedule(MyQueue<Process> readyQueue,
                                     Process runningProcess,
                                     int currentCycle) {

        // Detectar fallos de misión en la cola de listos
        detectMissionFailures(readyQueue, currentCycle);

        // Verificar si el proceso en CPU superó su deadline
        if (runningProcess != null && hasExceededDeadline(runningProcess)) {
            registerMissionFailure(runningProcess, currentCycle);
            return new SchedulingResult(Action.MISSION_FAIL_CPU, null, runningProcess);
        }

        // Ordenar cola de listos por deadline
        sortByDeadline(readyQueue);

        // CPU libre -> asignar el más urgente
        if (runningProcess == null) {
            Process next = readyQueue.dequeue();
            if (next != null) {
                next.setStatus(RUNNING);
                return new SchedulingResult(Action.ASSIGN_NEW, next, null);
            }
            return new SchedulingResult(Action.CPU_IDLE, null, null);
        }

        // CPU ocupada -> evaluar preemptión
        Process candidate = readyQueue.peek();
        if (candidate != null && candidate.getRemainingDeadline() < runningProcess.getRemainingDeadline()) {
            saveContext(runningProcess, currentCycle);

            runningProcess.setStatus(READY);
            readyQueue.dequeue();
            readyQueue.enqueue(runningProcess);
            candidate.setStatus(RUNNING);

            System.out.println("[EDF] Preempción: " + runningProcess.getName()
                    + " (deadline=" + runningProcess.getRemainingDeadline()
                    + ") sale, entra " + candidate.getName()
                    + " (deadline=" + candidate.getRemainingDeadline() + ")");

            return new SchedulingResult(Action.PREEMPT, candidate, runningProcess);
        }

        return new SchedulingResult(Action.NO_CHANGE, runningProcess, null);
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
        p.setStatus(MISSION_FAILED);
        failedProcesses.addLast(p);
        System.out.println("[EDF] FALLO DE MISIÓN: " + p.getName()
                + " (ID=" + p.getId() + ") en ciclo " + currentCycle
                + " - Deadline superado sin completar");
    }

    // --- Ordenamiento por deadline (Insertion Sort sobre la lista) ---
    public void sortByDeadline(MyQueue<Process> queue) {
        ListaDobleEnlazada<Process> list = queue.getList();
        int size = list.getSize();
        if (size <= 1) return;

        Process[] temp = new Process[size];
        for (int i = 0; i < size; i++) {
            temp[i] = list.get(i);
        }

        // Insertion sort ascendente por remainingDeadline
        for (int i = 1; i < size; i++) {
            Process key = temp[i];
            int j = i - 1;
            while (j >= 0 && temp[j].getRemainingDeadline() > key.getRemainingDeadline()) {
                temp[j + 1] = temp[j];
                j--;
            }
            temp[j + 1] = key;
        }

        // Reconstruir la cola en orden
        while (!queue.isEmpty()) {
            queue.dequeue();
        }
        for (int i = 0; i < size; i++) {
            queue.enqueue(temp[i]);
        }
    }

    // --- Guardado de contexto (PC y MAR) al expulsar ---
    private void saveContext(Process process, int currentCycle) {
        SavedContext ctx = new SavedContext(
                process.getId(),
                process.getPc(),
                process.getMar(),
                currentCycle
        );
        contextLog.addLast(ctx);
        System.out.println("[EDF] Contexto guardado: " + ctx);
    }

    // --- Reintegración de procesos que vuelven de E/S ---
    public void reinsertFromBlocked(Process process, MyQueue<Process> readyQueue) {
        process.setStatus(READY);
        readyQueue.enqueue(process);
        sortByDeadline(readyQueue);
        System.out.println("[EDF] " + process.getName()
                + " reintegrado desde E/S (deadline restante="
                + process.getRemainingDeadline() + ")");
    }

    // --- Reportes de misión ---
    public void printContextLog() {
        System.out.println("\n--- Bitácora de Cambios de Contexto ---");
        if (contextLog.isEmpty()) {
            System.out.println("  No se realizaron cambios de contexto.");
        } else {
            for (int i = 0; i < contextLog.getSize(); i++) {
                System.out.println("  " + contextLog.get(i));
            }
        }
    }

    public void printFailureReport() {
        System.out.println("\n--- Reporte de Fallos de Misión ---");
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

    // --- Getters ---
    public ListaDobleEnlazada<SavedContext> getContextLog() { return contextLog; }
    public ListaDobleEnlazada<Process> getFailedProcesses() { return failedProcesses; }
}
