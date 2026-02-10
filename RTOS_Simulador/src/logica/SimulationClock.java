/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package logica;

import modelos.Process;
import logica.Kernel;

/**
 * Hilo que marca el paso del tiempo (ciclos)
 * @author Andrea
 */
public class SimulationClock extends Thread {
    private int cycleDurationMs; // Duración de cada ciclo en milisegundos
    private int totalCycles;      // Contador global de ciclos
    private boolean running;      // Control del hilo
    private boolean interrupted = false;
    private Kernel kernel; 
    
    // Proceso que está actualmente en CPU 
    private Process currentProcess;

    public SimulationClock(int durationMs, Kernel kernel) {
        this.cycleDurationMs = durationMs;
        this.kernel = kernel;
        this.totalCycles = 0;
        this.running = false;
        
    }
    
    public void handleInterrupt() {
        this.interrupted = true; // Activamos la bandera de interrupción
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                // 1. Manejo de Interrupciones (ISR)
                if (interrupted) {
                    System.out.println("\n[KERNEL] Ejecutando ISR (Rutina de Servicio de Interrupción)...");
                    Thread.sleep(2000); // El satélite tarda 2 segundos en recuperarse
                    System.out.println("[KERNEL] Satélite estabilizado. Reanudando planificación.\n");
                    interrupted = false; 
                }

                Thread.sleep(cycleDurationMs);
                totalCycles++;
                kernel.updateBlockedProcesses();

                // Miramos qué hay en la CPU física
                Process p = kernel.getCpu().getCurrentProcess();

                if (p != null) {
                    if (p.shouldBlock()) {
                        kernel.blockProcess(p); // El kernel lo saca de la CPU y lo bloquea
                    } else if (!p.isFinished()) {
                        p.executeInstruction();
                        p.updateDeadline();
                        System.out.println("[Ciclo " + totalCycles + "] " + p.getName() + " en CPU.");
                    } else {
                        System.out.println("[Ciclo " + totalCycles + "] " + p.getName() + " TERMINADO.");
                        p.setStatus("Terminado");
                        kernel.getCpu().release(); // Liberamos la CPU física
                    }
                } else {
                    // CPU libre: Pedimos al Kernel el siguiente
                    Process next = kernel.getNextProcess();
                    if (next != null) {
                        next.setStatus("Ejecución");
                        kernel.getCpu().setProcess(next); // Ponemos el proceso en el socket de la CPU
                        System.out.println("[Ciclo " + totalCycles + "] KERNEL: Asignando " + next.getName());
                    } else {
                        System.out.println("[Ciclo " + totalCycles + "] CPU Ociosa...");
                    }
                }

            } catch (InterruptedException e) { running = false; }
        }
    }
    

    // Métodos de control
    public void stopSimulation() { this.running = false; }
    public void setCycleDuration(int ms) { this.cycleDurationMs = ms; }
    public int getTotalCycles() { return totalCycles; }
    
    public void setCurrentProcess(Process p) {
        this.currentProcess = p;
    }
}