/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package logica;
import modelos.Process;
import estructuras.MyQueue;
import java.util.concurrent.Semaphore;
/**
 * Administrador central
 * @author Andrea
 */
public class Kernel {
    private MyQueue<Process> readyQueue;
    private MyQueue<Process> blockedQueue;
    private MyQueue<Process> suspendedReadyQueue;
    private MyQueue<Process> suspendedBlockedQueue;
    
    // Configuración de Memoria 
    private int maxRamProcesses = 5; // Ejemplo: solo 5 procesos caben en RAM
    
    // Semáforo para Exclusión Mutua (Obligatorio según el PDF)
    // Protege el acceso a las colas cuando varios hilos intentan usarlas
    private Semaphore mutex;

    public Kernel() {
        this.readyQueue = new MyQueue<>();
        this.blockedQueue = new MyQueue<>();
        this.suspendedReadyQueue = new MyQueue<>();
        this.suspendedBlockedQueue = new MyQueue<>();
        this.mutex = new Semaphore(1); // 1 permiso = Exclusión mutua
    }

    /**
     * Lógica del Planificador de Mediano Plazo:
     * Si la RAM está llena, envía el proceso a la cola de Suspendidos.
     */
    public void addProcess(Process p) {
        try {
            mutex.acquire(); // Bloqueamos el acceso para otros hilos
            
            int processesInRAM = readyQueue.getSize() + blockedQueue.getSize();
            
            if (processesInRAM < maxRamProcesses) {
                p.setStatus("Listo");
                readyQueue.enqueue(p);
                System.out.println("[MEMORIA] Proceso " + p.getName() + " cargado en RAM.");
            } else {
                p.setStatus("Listo-Suspendido");
                suspendedReadyQueue.enqueue(p);
                System.out.println("[MEMORIA SATURADA] Proceso " + p.getName() + " enviado a SWAP (Memoria Secundaria).");
            }
            
            mutex.release(); // Liberamos el acceso
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Getters para las colas
    public MyQueue<Process> getReadyQueue() { return readyQueue; }
    
    public void setMaxRamProcesses(int value) {
    this.maxRamProcesses = value;
}
}
