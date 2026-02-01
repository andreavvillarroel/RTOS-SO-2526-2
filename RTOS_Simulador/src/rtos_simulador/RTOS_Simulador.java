/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package rtos_simulador;

import estructuras.MyQueue;
import modelos.Process;
import logica.SimulationClock;
import logica.InterruptHandler;
/**
 *
 * @author Dell
 */
public class RTOS_Simulador {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Process p1 = new Process("001", "Sensor_Altitud", 20, 1, 50);
        SimulationClock clock = new SimulationClock(1000);
        InterruptHandler interruptHandler = new InterruptHandler(clock);

        clock.setCurrentProcess(p1);

        clock.start();           // Arranca el tiempo
        interruptHandler.start(); // Arranca las emergencias
    }
    
}
