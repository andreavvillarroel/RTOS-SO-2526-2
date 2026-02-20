/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package algoritmos;

import estructuras.ListaDobleEnlazada;
import estructuras.MyQueue;
import modelos.Process;

/**
* Planificador FCFS (First Come First Served) - No Preemptivo.
* El primer proceso que llega a la cola es el primero en ejecutarse.
* Un proceso se queda en CPU hasta que termina o se bloquea.
* No hay reordenamiento de cola, no hay quantum, no hay desalojo.
* @author Andrea
*/
public class PlanificadorFCFS implements IPlanificador {

    // --- Resultado interno ---
    public enum Action {
        ASSIGN_NEW,
        NO_CHANGE,
        CPU_IDLE,
        MISSION_FAIL_CPU
    }

    public static class SchedulingResult {
    private final Action action;
    private final Process assignedProcess;

    public SchedulingResult(Action action, Process assigned) {
    this.action = action;
    this.assignedProcess = assigned;
    }

    public Action getAction() { return action; }
    public Process getAssignedProcess() { return assignedProcess; }
    }

    // --- Registro de contexto ---
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
    }

    // --- Bitácora ---
    private final ListaDobleEnlazada<SavedContext> contextLog;
    private final ListaDobleEnlazada<Process> failedProcesses;

    public PlanificadorFCFS() {
    this.contextLog = new ListaDobleEnlazada<>();
    this.failedProcesses = new ListaDobleEnlazada<>();
    }

    // --- Método principal ---
    public SchedulingResult schedule(MyQueue<Process> readyQueue,
    Process runningProcess,
    int currentCycle) {

    // Detectar fallos de misión en cola
    detectMissionFailures(readyQueue, currentCycle);

    // Verificar fallo en CPU
    if (runningProcess != null && hasExceededDeadline(runningProcess)) {
    saveContext(runningProcess, currentCycle, "Fallo de Misión");
    registerMissionFailure(runningProcess, currentCycle);
    return new SchedulingResult(Action.MISSION_FAIL_CPU, null);
    }

    // CPU libre -> asignar el primero de la cola (FIFO, sin reordenar)
    if (runningProcess == null) {
    Process next = readyQueue.dequeue();
    if (next != null) {
    next.setStatus("Ejecución");
    return new SchedulingResult(Action.ASSIGN_NEW, next);
    }
    return new SchedulingResult(Action.CPU_IDLE, null);
    }

    // CPU ocupada -> FCFS NO desaloja, el proceso sigue
    return new SchedulingResult(Action.NO_CHANGE, runningProcess);
    }

    // --- Detección de fallos ---
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
    System.out.println("[FCFS] FALLO DE MISIÓN: " + p.getName()
    + " (ID=" + p.getId() + ") en ciclo " + currentCycle
    + " - Deadline superado sin completar");
    }

    private void saveContext(Process process, int currentCycle, String reason) {
    SavedContext ctx = new SavedContext(
    process.getId(), process.getPc(), process.getMar(),
    currentCycle, reason);
    contextLog.addLast(ctx);
    System.out.println("[FCFS] Contexto guardado: " + ctx);
    }

    // --- Reintegración desde E/S (al final de la cola, FIFO) ---
    @Override
    public void reinsertFromBlocked(Process process, MyQueue<Process> readyQueue) {
    process.setStatus("Listo");
    readyQueue.enqueue(process);
    System.out.println("[FCFS] " + process.getName() + " reintegrado desde E/S");
    }

    // --- Reportes ---
    @Override
    public void printContextLog() {
    System.out.println("\n--- Bitácora de Cambios de Contexto (FCFS) ---");
    if (contextLog.isEmpty()) {
    System.out.println(" No se realizaron cambios de contexto.");
    } else {
    for (int i = 0; i < contextLog.getSize(); i++) {
    System.out.println(" " + contextLog.get(i));
    }
    }
    }

    @Override
    public void printFailureReport() {
    System.out.println("\n--- Reporte de Fallos de Misión (FCFS) ---");
    if (failedProcesses.isEmpty()) {
    System.out.println(" Todos los procesos cumplieron su deadline.");
    } else {
    System.out.println(" Total de fallos: " + failedProcesses.getSize());
    for (int i = 0; i < failedProcesses.getSize(); i++) {
    Process p = failedProcesses.get(i);
    System.out.println(" - " + p.getId() + " (" + p.getName() + ")");
    }
    }
    }

    // --- IPlanificador ---
    private Process procesoAsignado;

    @Override
    public String decide(MyQueue<Process> readyQueue, Process runningProcess, int currentCycle) {
    SchedulingResult result = schedule(readyQueue, runningProcess, currentCycle);
    this.procesoAsignado = result.getAssignedProcess();
    return switch (result.getAction()) {
    case ASSIGN_NEW -> "ASSIGN_NEW";
    case NO_CHANGE -> "NO_CHANGE";
    case CPU_IDLE -> "CPU_IDLE";
    case MISSION_FAIL_CPU -> "MISSION_FAIL_CPU";
    };
    }

    @Override public Process getAssignedProcess() { return procesoAsignado; }
    @Override public Process getPreemptedProcess() { return null; }
    @Override public String getName() { return "FCFS"; }

    @Override
    public String getExtraInfo(Process p) {
    return "deadline=" + p.getRemainingDeadline();
    }

    @Override public void postExecution(Process p) { /* No aplica */ }
    @Override public void onProcessLeavesCpu(Process p) { /* No aplica */ }
    @Override public int getTotalFailures() { return failedProcesses.getSize(); }
    @Override public int getTotalContextSwitches() { return contextLog.getSize(); }

    // --- Getters ---
    public ListaDobleEnlazada<SavedContext> getContextLog() { return contextLog; }
    public ListaDobleEnlazada<Process> getFailedProcesses() { return failedProcesses; }
    }