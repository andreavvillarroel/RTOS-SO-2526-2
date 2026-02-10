/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package modelos;

/**
 * Representa el procesador del satélite.
 * @author ANdrea
 */
public class CPU {
    private Process currentProcess; // El proceso que está "montado" en el chip
    private boolean busy;           // ¿Está trabajando o está ocioso?

    public CPU() {
        this.currentProcess = null;
        this.busy = false;
    }

    // El "socket": Pone un proceso en la CPU
    public void setProcess(Process p) {
        this.currentProcess = p;
        this.busy = (p != null);
    }

    public Process getCurrentProcess() {
        return currentProcess;
    }

    public boolean isBusy() {
        return busy;
    }

    // Limpia la CPU cuando un proceso termina o se bloquea
    public void release() {
        this.currentProcess = null;
        this.busy = false;
    }

}
