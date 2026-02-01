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
        // 1. Inicializamos el Kernel y el Reloj
        Kernel kernel = new Kernel();
        SimulationClock clock = new SimulationClock(1000, kernel); // 1 segundo por ciclo

        System.out.println("--- INICIANDO PRUEBA DE KERNEL (RAM MÁX: 5) ---");

        // 2. Intentamos cargar 8 procesos
        for (int i = 1; i <= 8; i++) {
            Process p = new Process("ID-" + i, "Tarea_" + i, 10, 1, 100);
            kernel.addProcess(p);
        }

        // 3. Verificamos el estado de las colas
        System.out.println("\n--- RESULTADO DE CARGA ---");
        System.out.println("Procesos en RAM (Ready): " + kernel.getReadyQueue().getSize());
        // Nota: Asegúrate de tener getters para las otras colas en tu clase Kernel
        // System.out.println("Procesos en SWAP (Suspended): " + kernel.getSuspendedReadyQueue().getSize());

        // 4. Simulamos que el reloj toma el primer proceso de la RAM
        if (!kernel.getReadyQueue().isEmpty()) {
            clock.setCurrentProcess(kernel.getReadyQueue().dequeue());
            clock.start();
        }
    }
    
}
