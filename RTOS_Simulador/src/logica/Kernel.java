package logica;

import modelos.Process;
import modelos.CPU;
import modelos.Memory;
import java.util.concurrent.Semaphore;

public class Kernel {
    private Memory memory;
    private CPU cpu;
    private Semaphore mutex;

    public Kernel() {
        this.memory = new Memory(5); // Iniciamos RAM con límite de 5
        this.cpu = new CPU();
        this.mutex = new Semaphore(1);
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
                    p.setStatus("Listo");
                    memory.getReadyQueue().enqueue(p);
                    System.out.println("[I/O] " + p.getName() + " vuelve a Ready.");
                } else {
                    memory.getBlockedQueue().enqueue(p);
                }
            }
            mutex.release();
        } catch (InterruptedException e) { e.printStackTrace(); }
    }

    // Getters para que el Reloj y la GUI accedan a las piezas
    public CPU getCpu() { return cpu; }
    public Memory getMemory() { return memory; }
}