/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package modelos;
import estructuras.MyQueue;
/**
 * Representa la gestión de RAM y SWAP del satélite.
 * @author Andrea 
 */
public class Memory {
    // RAM
    private MyQueue<Process> readyQueue;
    private MyQueue<Process> blockedQueue;
    
    // SWAP (Memoria Secundaria)
    private MyQueue<Process> suspendedReadyQueue;
    private MyQueue<Process> suspendedBlockedQueue;
    
    // Terminados
    private MyQueue<Process> finishedQueue;
    
    private int maxRamProcesses;

    public Memory(int maxRam) {
        this.readyQueue = new MyQueue<>();
        this.blockedQueue = new MyQueue<>();
        this.suspendedReadyQueue = new MyQueue<>();
        this.suspendedBlockedQueue = new MyQueue<>();
        this.finishedQueue = new MyQueue<>();
        this.maxRamProcesses = maxRam;
    }

    // Getters para que la GUI pueda ver las colas
    public MyQueue<Process> getReadyQueue() { return readyQueue; }
    public MyQueue<Process> getBlockedQueue() { return blockedQueue; }
    public MyQueue<Process> getSuspendedReadyQueue() { return suspendedReadyQueue; }
    public MyQueue<Process> getSuspendedBlockedQueue() { return suspendedBlockedQueue; }
    public MyQueue<Process> getFinishedQueue() { return finishedQueue; }

    public int getMaxRamProcesses() { return maxRamProcesses; }
    public void setMaxRamProcesses(int max) { this.maxRamProcesses = max; }
    
    // Método para saber cuántos procesos hay en RAM actualmente
    public int getRamUsage() {
        return readyQueue.getSize() + blockedQueue.getSize();
    }

}
