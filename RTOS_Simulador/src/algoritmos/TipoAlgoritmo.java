/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package algoritmos;

/**
 *Enum con los tipos de algoritmo de planificación disponibles.
 * Se usa para cambiar dinámicamente el algoritmo desde la GUI.
 * @author Francisco
 */

public enum TipoAlgoritmo {
    FCFS("First Come First Served"),
    EDF("Earliest Deadline First"),
    RR("Round Robin"),
    PRIORIDAD("Prioridad Estática Preemptiva"),
    SRT("Shortest Remaining Time");

    private final String descripcion;

    TipoAlgoritmo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() { return descripcion; }

    @Override
    public String toString() { return name(); }
}
