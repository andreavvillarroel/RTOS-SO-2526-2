/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package rtos_simulador;

import estructuras.MyQueue;
import modelos.Process;
import logica.SimulationClock;
/**
 *
 * @author Dell
 */
public class RTOS_Simulador {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
     // 1. Creamos la cola de listos usando TU estructura
        Process p1 = new Process("001", "Sensor_Altitud", 5, 1, 10);
    SimulationClock clock = new SimulationClock(1000); 

    // 2. Le damos el proceso al reloj y arrancamos
    clock.setCurrentProcess(p1);
    clock.start(); // Esto llama al método run() en un hilo nuevo
    
    System.out.println("Simulación iniciada...");
    }
    
}
