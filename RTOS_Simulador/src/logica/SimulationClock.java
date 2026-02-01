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

                // 2. El "Tick" del reloj: Esperamos la duración del ciclo
                Thread.sleep(cycleDurationMs);
                totalCycles++;
                
                // 3. Lógica de ejecución del proceso actual
                if (currentProcess != null) {
                    if (!currentProcess.isFinished()) {
                        currentProcess.executeInstruction();
                        currentProcess.updateDeadline();
                        System.out.println("[Ciclo " + totalCycles + "] Ejecutando: " + currentProcess.getName() 
                                           + " | Deadline Restante: " + currentProcess.getRemainingDeadline());
                    } else {
                        System.out.println("[Ciclo " + totalCycles + "] El proceso " + currentProcess.getName() + " ha TERMINADO.");
                        currentProcess.setStatus("Terminado");
                        this.currentProcess = null; // Liberamos la CPU
                    }
                } else {
                    System.out.println("[Ciclo " + totalCycles + "] CPU Ociosa (Esperando proceso...)");
                    this.currentProcess = kernel.getNextProcess(); 
    
                    if (currentProcess != null) {
                        currentProcess.setStatus("Ejecución");
                        System.out.println("[KERNEL] Proceso " + currentProcess.getName() + " asignado a CPU.");
                    }
                }

            } catch (InterruptedException e) {
                System.err.println("Reloj interrumpido");
                running = false;
            }
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