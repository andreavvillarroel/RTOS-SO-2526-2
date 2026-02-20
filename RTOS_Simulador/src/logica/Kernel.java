package logica;

import modelos.Process;
import modelos.CPU;
import modelos.Memory;
import java.util.concurrent.Semaphore;
import algoritmos.PlanificadorEDF;
import algoritmos.PlanificadorRR;
import algoritmos.PlanificadorPrioridad;
import algoritmos.PlanificadorSRT;
import algoritmos.PlanificadorFCFS;
import algoritmos.IPlanificador;
import algoritmos.TipoAlgoritmo;
import estructuras.ListaDobleEnlazada;
import estructuras.MyQueue;

public class Kernel {
    
    private Memory memory;
    private CPU cpu;
    private Semaphore mutex;
    
    private PlanificadorEDF schedulerEDF;
    private PlanificadorRR schedulerRR;
    private PlanificadorPrioridad schedulerPrio;
    private PlanificadorSRT schedulerSRT;
    private PlanificadorFCFS schedulerFCFS;

    private IPlanificador planificadorActual;
    private TipoAlgoritmo tipoActual;
    
    private int procesosTerminados;
    private int procesosFallidos;
    private int totalProcesos;
    private int totalWaitTime;
    
    private final ListaDobleEnlazada<String> eventLog;
    
    private EventListener guiEventListener;
    public interface EventListener {
        void onEvent(String message);
    }

    public Kernel() {
        this.memory = new Memory(10); // Iniciamos RAM con límite de 5
        this.cpu = new CPU();
        this.mutex = new Semaphore(1);
        
        this.schedulerEDF = new PlanificadorEDF();
        this.schedulerRR = new PlanificadorRR(3);
        this.schedulerPrio = new PlanificadorPrioridad();
        this.schedulerSRT = new PlanificadorSRT();
        this.schedulerFCFS = new PlanificadorFCFS();
        
        
        this.tipoActual = TipoAlgoritmo.FCFS;
        this.planificadorActual = schedulerFCFS;
        
        this.procesosTerminados = 0;
        this.procesosFallidos = 0;
        this.totalProcesos = 0;
        this.totalWaitTime = 0;
        this.eventLog = new ListaDobleEnlazada<>();
    }

    // --- Gestión de Memoria y Procesos ---

    public void addProcess(Process p) {
        try {
            mutex.acquire();
            totalProcesos++;
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

//    public Process getNextProcess() {
//        try {
//            mutex.acquire();
//            // Lógica de Swap-In
//            if (memory.getReadyQueue().isEmpty() && !memory.getSuspendedReadyQueue().isEmpty()) {
//                Process pFromSwap = memory.getSuspendedReadyQueue().dequeue();
//                pFromSwap.setStatus("Listo");
//                memory.getReadyQueue().enqueue(pFromSwap);
//                System.out.println("[KERNEL] Movido de SWAP a RAM: " + pFromSwap.getName());
//            }
//
//            Process p = memory.getReadyQueue().dequeue();
//            mutex.release();
//            return p;
//        } catch (InterruptedException e) { return null; }
//    }
    
    /**
     * Carga masiva de procesos con exclusión mutua.
     * Un solo acquire/release para todo el lote, evitando condiciones de carrera
     * si el reloj está corriendo en paralelo.
     */
    public void addBulkProcesses(ListaDobleEnlazada<Process> processes) {
        try {
            mutex.acquire();
            int loaded = 0;
            int swapped = 0;
            for (int i = 0; i < processes.getSize(); i++) {
                Process p = processes.get(i);
                totalProcesos++;
                if (memory.getRamUsage() < memory.getMaxRamProcesses()) {
                    p.setStatus("Listo");
                    memory.getReadyQueue().enqueue(p);
                    loaded++;
                } else {
                    p.setStatus("Listo-Suspendido");
                    memory.getSuspendedReadyQueue().enqueue(p);
                    swapped++;
                }
            }
            System.out.println("[KERNEL] Carga masiva: " + loaded + " en RAM, "
                    + swapped + " en SWAP (" + processes.getSize() + " procesos total)");
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    // --- Gestión de Bloqueos ---

//    public void blockProcess(Process p) {
//        try {
//            mutex.acquire();
//            p.startIO();
//            memory.getBlockedQueue().enqueue(p);
//            cpu.release(); //Liberamos la CPU física
//            mutex.release();
//            System.out.println("[I/O] Proceso " + p.getName() + " bloqueado.");
//        } catch (InterruptedException e) { e.printStackTrace(); }
//    }

    public void updateBlockedProcesses() {
        try {
            mutex.acquire();
            
            // 1. Tick E/S de bloqueados en RAM
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
            
            // 2. Tick E/S de bloqueados-suspendidos en SWAP
            int suspSize = memory.getSuspendedBlockedQueue().getSize();
            for (int i = 0; i < suspSize; i++) {
                Process p = memory.getSuspendedBlockedQueue().dequeue();
                p.tickIO();
                if (p.isIoFinished()) {
                    // E/S terminada -> pasa a Listo-Suspendido (esperará Swap-In)
                    p.setStatus("Listo-Suspendido");
                    memory.getSuspendedReadyQueue().enqueue(p);
                    System.out.println("[KERNEL] " + p.getName()
                            + " terminó E/S en SWAP -> Listo-Suspendido");
                } else {
                    memory.getSuspendedBlockedQueue().enqueue(p);
                }
            }
            
            // 3. Swap-Out: si RAM está llena y hay bloqueados, moverlos a SWAP
            while (memory.getRamUsage() > memory.getMaxRamProcesses()
                    && !memory.getBlockedQueue().isEmpty()) {
                Process victim = memory.getBlockedQueue().dequeue();
                victim.setStatus("Bloqueado-Suspendido");
                memory.getSuspendedBlockedQueue().enqueue(victim);
                System.out.println("[SWAP-OUT] " + victim.getName()
                        + " movido a Bloqueado-Suspendido (E/S restante="
                        + victim.getRemainingIoTime() + ")");
                fireEvent(0, victim.getName() + " movido a Bloqueado-Suspendido");
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
                    fireEvent(currentCycle, "Fallo de misión detectado en CPU");
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
                    fireEvent(currentCycle, "Proceso " + inCpu.getId()
                            + " (" + inCpu.getName() + ") ha solicitado E/S. Movido a Bloqueados.");
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
                    memory.getFinishedQueue().enqueue(inCpu);
                    totalWaitTime += inCpu.getWaitTime();
                    procesosTerminados++;
                        fireEvent(currentCycle, "✓ " + inCpu.getName()
                            + " completado (espera=" + inCpu.getWaitTime() + " ciclos)");
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
                case FCFS -> this.planificadorActual = schedulerFCFS;
                case EDF -> this.planificadorActual = schedulerEDF;
                case RR -> this.planificadorActual = schedulerRR;
                case PRIORIDAD -> this.planificadorActual = schedulerPrio;
                case SRT -> this.planificadorActual = schedulerSRT;
                default -> this.planificadorActual = schedulerFCFS;
            }

            String msg = "Sistema cambiado de " + anterior + " a " + tipo;
            System.out.println("[KERNEL] " + msg);
            fireEvent(0, msg);
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    public void setAlgorithm(String algorithm) {
        switch (algorithm) {
            case "FCFS" -> setAlgoritmo(TipoAlgoritmo.FCFS);
            case "EDF" -> setAlgoritmo(TipoAlgoritmo.EDF);
            case "RR" -> setAlgoritmo(TipoAlgoritmo.RR);
            case "PRIO" -> setAlgoritmo(TipoAlgoritmo.PRIORIDAD);
            case "SRT" -> setAlgoritmo(TipoAlgoritmo.SRT);
            default -> setAlgoritmo(TipoAlgoritmo.FCFS);
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
    
    public void setEventListener(EventListener listener) {
        this.guiEventListener = listener;
    }

    /**
     * Envía un evento tanto al log interno como a la GUI.
     */
    private void fireEvent(int cycle, String message) {
        logEvent(cycle, message);
        if (guiEventListener != null) {
            guiEventListener.onEvent("[Ciclo " + cycle + "] " + message);
        }
    }
    
     /**
     * Cambia el límite de RAM en caliente.
     * Si el nuevo límite es menor que los procesos actuales en RAM,
     * mueve los excedentes a SWAP priorizando sacar al de deadline más lejano
     * (el menos urgente, para proteger la misión).
     */
    public void updateRamLimit(int newLimit) {
        try {
            mutex.acquire();
            int oldLimit = memory.getMaxRamProcesses();
            memory.setMaxRamProcesses(newLimit);

            int excess = memory.getRamUsage() - newLimit;
            if (excess > 0) {
                System.out.println("[KERNEL] RAM reducida de " + oldLimit + " a " + newLimit
                        + " - Purgando " + excess + " procesos");
                fireEvent(0, "Cambio de Límite RAM: Moviendo " + excess + " procesos excedentes a Suspendido.");

                for (int e = 0; e < excess; e++) {
                    // Encontrar el proceso con deadline más lejano en la cola de Listos
                    Process victim = findLeastUrgent(memory.getReadyQueue());
                    if (victim != null) {
                        removeFromQueue(memory.getReadyQueue(), victim);
                        victim.setStatus("Listo-Suspendido");
                        memory.getSuspendedReadyQueue().enqueue(victim);
                        System.out.println("[SWAP-OUT] " + victim.getName()
                                + " suspendido (deadline=" + victim.getRemainingDeadline() + ")");
                    }
                }
            }

            System.out.println("[KERNEL] Límite de RAM actualizado a " + newLimit + " procesos");
            logEvent(0, "Límite de RAM actualizado a " + newLimit + " procesos");
            mutex.release();
        } catch (InterruptedException ex) { ex.printStackTrace(); }
    }

    /**
     * Busca el proceso con deadline más lejano (menos urgente) en la cola.
     */
    private Process findLeastUrgent(MyQueue<Process> queue) {
        ListaDobleEnlazada<Process> list = queue.getList();
        if (list.getSize() == 0) return null;

        Process leastUrgent = list.get(0);
        for (int i = 1; i < list.getSize(); i++) {
            Process p = list.get(i);
            if (p != null && p.getRemainingDeadline() > leastUrgent.getRemainingDeadline()) {
                leastUrgent = p;
            }
        }
        return leastUrgent;
    }

    /**
     * Remueve un proceso específico de la cola.
     */
    private void removeFromQueue(MyQueue<Process> queue, Process target) {
        ListaDobleEnlazada<Process> list = queue.getList();
        list.remove(target);
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
                p.updateDeadline();
                p.incrementWaitTime(); // Métrica: tiempo de espera en cola
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
        System.out.println("  Tiempo espera prom:  " + String.format("%.2f", getAvgWaitTime()) + " ciclos");

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
    public int getTotalWaitTime()                { return totalWaitTime; }
    public double getAvgWaitTime() {return procesosTerminados > 0 ? (double) totalWaitTime / procesosTerminados : 0;}
    public PlanificadorEDF getSchedulerEDF() { return schedulerEDF; }
    public PlanificadorRR getSchedulerRR() { return schedulerRR; }
    public PlanificadorPrioridad getSchedulerPrio() { return schedulerPrio; }
    public PlanificadorSRT getSchedulerSRT() { return schedulerSRT; }

}