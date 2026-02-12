package logica;

import modelos.Process;
import modelos.CPU;
import modelos.Memory;
import java.util.concurrent.Semaphore;
import algoritmos.PlanificadorEDF;
import static algoritmos.PlanificadorEDF.Action.ASSIGN_NEW;
import static algoritmos.PlanificadorEDF.Action.CPU_IDLE;
import static algoritmos.PlanificadorEDF.Action.MISSION_FAIL_CPU;
import static algoritmos.PlanificadorEDF.Action.NO_CHANGE;
import static algoritmos.PlanificadorEDF.Action.PREEMPT;
import algoritmos.PlanificadorEDF.SchedulingResult;

public class Kernel {
    private Memory memory;
    private CPU cpu;
    private Semaphore mutex;
    private PlanificadorEDF scheduler;

    public Kernel() {
        this.memory = new Memory(5); // Iniciamos RAM con límite de 5
        this.cpu = new CPU();
        this.mutex = new Semaphore(1);
        this.scheduler = new PlanificadorEDF();
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
                   // Reintegrar vía EDF para que se evalúe preempción
                    scheduler.reinsertFromBlocked(p, memory.getReadyQueue());
                } else {
                    memory.getBlockedQueue().enqueue(p);
                }
            }
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }
    
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
            SchedulingResult result = scheduler.schedule(
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

    // Getters para que el Reloj y la GUI accedan a las piezas
    public CPU getCpu() { return cpu; }
    public Memory getMemory() { return memory; }
    public PlanificadorEDF getScheduler() { return scheduler; }

    // --- Ciclo principal de planificación EDF ---

    

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

    // --- Reportes de misión ---
    public void printMissionReports() {
        scheduler.printContextLog();
        scheduler.printFailureReport();
    }
    
    // --- Condición de parada: no quedan procesos vivos ---
    public boolean isSimulationComplete() {
        return cpu.getCurrentProcess() == null
            && memory.getReadyQueue().isEmpty()
            && memory.getBlockedQueue().isEmpty()
            && memory.getSuspendedReadyQueue().isEmpty()
            && memory.getSuspendedBlockedQueue().isEmpty();
    }
      
}