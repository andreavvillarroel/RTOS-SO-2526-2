/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package rtos_simulador;

import estructuras.MyQueue;
import modelos.Process;
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
        MyQueue<Process> readyQueue = new MyQueue<>();

        // 2. Creamos un proceso de prueba
        Process p1 = new Process("001", "Sensor_Altitud", 10, 1, 50);

        // 3. Lo metemos en la cola
        readyQueue.enqueue(p1);

        // 4. Probamos si sale correctamente
        Process extraido = readyQueue.dequeue();
        System.out.println("Proceso recuperado de la cola: " + extraido.getName());

        if(extraido.getName().equals("Sensor_Altitud")) {
            System.out.println("¡Tus estructuras y el modelo funcionan perfecto!");
        }
    }
    
}
