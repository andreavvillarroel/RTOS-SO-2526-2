/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package logica;
import modelos.Process;
/**
 * hilo que marca el paso del tiempo (ciclos)
 * @author Andrea
 */
public class SimulationClock extends Thread{
    private int cycleDurationMs; // Duración de cada ciclo en milisegundos
    private int totalCycles;      // Contador global de ciclos
    private boolean running;      // Control del hilo
    
    // proceso que está actualmente en CPU 
    private Process currentProcess;

    public SimulationClock(int durationMs) {
        this.cycleDurationMs = durationMs;
        this.totalCycles = 0;
        this.running = false;
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            try {
                //  El "Tick" del reloj: Esperamos la duración del ciclo
                Thread.sleep(cycleDurationMs);
                
               
                totalCycles++;
                
                // Lógica de ejecución
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
