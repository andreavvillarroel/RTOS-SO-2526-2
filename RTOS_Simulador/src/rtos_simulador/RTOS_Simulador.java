/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package rtos_simulador;

import estructuras.MyQueue;
import modelos.Process;
import logica.SimulationClock;
import logica.InterruptHandler;
import logica.Kernel;
/**
 *
 * @author Dell
 */
public class RTOS_Simulador {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Kernel kernel = new Kernel(); // El kernel ya trae su CPU y Memoria (Límite 5)
        SimulationClock clock = new SimulationClock(800, kernel); // Ciclos un poco más rápidos
        InterruptHandler interrupts = new InterruptHandler(clock);

        System.out.println("=== INICIANDO VALIDACIÓN DE ARQUITECTURA MODULAR ===");
        System.out.println("Configuración: RAM Límite = 5 | SWAP = Ilimitado\n");

        // 2. Cargamos 7 procesos para forzar el uso de SWAP
        // Parámetros: ID, Nombre, Instrucciones, Prioridad, Deadline, Inst_Bloqueo, Duracion_Bloqueo
        
        // Procesos que entrarán en RAM
        kernel.addProcess(new Process("P1", "Sensor_1", 6, 1, 20, 3, 2)); // Se bloquea en ciclo 3
        kernel.addProcess(new Process("P2", "Sensor_2", 4, 1, 12, -1, 0)); // No se bloquea
        kernel.addProcess(new Process("P3", "Camara_1", 5, 1, 8, 2, 3)); // Se bloquea en ciclo 2
        kernel.addProcess(new Process("P4", "Camara_2", 3, 1, 25, -1, 0)); 
        kernel.addProcess(new Process("P5", "Telemetria", 4, 1, 15, -1, 0));

        // Procesos que irán a SWAP
        kernel.addProcess(new Process("P6", "Antena_A", 5, 1, 18, -1, 0));
        kernel.addProcess(new Process("P7", "Antena_B", 5, 1, 3, -1, 0));

        System.out.println("\nEstado inicial de Memoria:");
        System.out.println("En RAM (Ready): " + kernel.getMemory().getReadyQueue().getSize());
        System.out.println("En SWAP (Suspended): " + kernel.getMemory().getSuspendedReadyQueue().getSize());
        System.out.println("\n--- ARRANCANDO RELOJ ---\n");

        // 3. Encendemos los motores
        clock.start();
        interrupts.start();
    }

    
}
