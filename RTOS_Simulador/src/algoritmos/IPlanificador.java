/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package algoritmos;

import estructuras.MyQueue;
import modelos.Process;

/**
 *Interfaz Strategy para los algoritmos de planificación del satélite.
 * Permite al Kernel cambiar de algoritmo en tiempo de ejecución
 * sin modificar la lógica del núcleo (patrón Strategy).
 * @author Francisco
 */
public interface IPlanificador {
    
    /**
     * Método principal de planificación. Evalúa la cola de listos y el proceso
     * en CPU, y retorna un String con la acción a tomar.
     * Acciones posibles: "ASSIGN_NEW", "PREEMPT", "NO_CHANGE", "CPU_IDLE", "MISSION_FAIL_CPU"
     */
    String decidir(MyQueue<Process> readyQueue, Process runningProcess, int currentCycle);

    /**
     * Retorna el proceso que debe asignarse a CPU (el seleccionado por el algoritmo).
     * Se llama después de decidir() cuando la acción es ASSIGN_NEW o PREEMPT.
     */
    Process getProcesoAsignado();

    /**
     * Retorna el proceso que fue expulsado de CPU (si hubo preempción).
     * Se llama después de decidir() cuando la acción es PREEMPT.
     */
    Process getProcesoExpulsado();

    /**
     * Reintegra un proceso que vuelve de la cola de Bloqueados (E/S).
     */
    void reinsertFromBlocked(Process process, MyQueue<Process> readyQueue);

    /**
     * Imprime la bitácora de cambios de contexto.
     */
    void printContextLog();

    /**
     * Imprime el reporte de fallos de misión.
     */
    void printFailureReport();

    /**
     * Retorna el nombre del algoritmo para los logs.
     */
    String getNombre();

    /**
     * Retorna info extra para mostrar en los logs de ejecución.
     * Ejemplo: "quantum=2/3" para RR, "prio=0" para Prioridad, etc.
     */
    String getInfoExtra(Process p);

    /**
     * Se llama después de que el proceso ejecutó una instrucción.
     * Permite al planificador actualizar contadores internos (ej: quantum en RR).
     */
    void postEjecucion(Process p);

    /**
     * Se llama cuando un proceso termina, se bloquea, o falla.
     * Permite al planificador resetear contadores internos.
     */
    void onProcesoSaleCpu(Process p);

    /**
     * Retorna el número de procesos que fallaron su misión.
     */
    int getTotalFallos();

    /**
     * Retorna el número de cambios de contexto realizados.
     */
    int getTotalCambiosContexto();
    
}
