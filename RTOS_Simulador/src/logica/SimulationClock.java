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
    private InterruptHandler interruptHandler; // Referencia para detenerlo al finalizar
    
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
                
                // Delegamos toda la lógica al planificador EDF del Kernel
                kernel.executeEdfCycle(totalCycles);
                
                // Condición de parada: todos los procesos terminaron o fallaron
                if (kernel.isSimulationComplete()) {
                    System.out.println("\n[RELOJ] Simulación completada en " + totalCycles + " ciclos.");
                    running = false;
                }

            } catch (InterruptedException e) { running = false; }
        }
        // Al terminar la simulación, imprimir reportes de misión
        kernel.printMissionReports();
        if (interruptHandler != null) {
            interruptHandler.stopHandler();
        }
    }
    

    // Métodos de control
    public void stopSimulation() { this.running = false; }
    public void setCycleDuration(int ms) { this.cycleDurationMs = ms; }
    public int getTotalCycles() { return totalCycles; }
    
    public void setCurrentProcess(Process p) {
        this.currentProcess = p;
    }
    
    public void setInterruptHandler(InterruptHandler handler) {
        this.interruptHandler = handler;
    }
}