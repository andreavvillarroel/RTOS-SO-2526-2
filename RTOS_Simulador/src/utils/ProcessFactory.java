/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utils;

import modelos.Process;
import estructuras.ListaDobleEnlazada;

/**
 * Fábrica de procesos aleatorios para el simulador RTOS.
 * Genera procesos con parámetros coherentes para pruebas de estrés.
 * No usa java.util.Random: usa un LCG (Linear Congruential Generator) propio.
 * 
 * @author Andrea
 */
public class ProcessFactory {

    // LCG propio para no usar java.util.Random y mantener consistencia
    private static long seed = System.nanoTime();

    /**
     * Generador congruencial lineal simple para obtener números pseudoaleatorios.
     */
    private static int nextRandom(int bound) {
        seed = (seed * 6364136223846793005L + 1442695040888963407L);
        int val = (int) ((seed >>> 33) % bound);
        return Math.abs(val);
    }

    // Nombres de subsistemas del satélite para realismo
    private static final String[] SUBSYSTEMS = {
        "Sensor", "Camara", "Radar", "GPS", "Solar",
        "Gyro", "Comm", "Therm", "Antena", "Payload",
        "Magnet", "Star_Trk", "Batt_Mon", "Prop_Chk", "Telem"
    };

    private static final String[] SUFFIXES = {
        "RX", "TX", "Cal", "Fix", "Chk", "Mon", "Scan", "Sync"
    };

    // Contador global de IDs para asegurar unicidad durante la ejecución
    private static int globalCounter = 0;

    /**
     * Genera un único proceso aleatorio de emergencia.
     * Instrucciones: 2-8, Prioridad: 0-3, Deadline: instrucciones + 3~10
     * Incluye E/S con una probabilidad del 30%.
     */
    public static Process createEmergencyProcess() {
        globalCounter++;
        String id = "EMR" + globalCounter;
        String name = SUBSYSTEMS[nextRandom(SUBSYSTEMS.length)]
                + "_" + SUFFIXES[nextRandom(SUFFIXES.length)]
                + "_" + globalCounter;

        int instructions = nextRandom(7) + 2; // Rango: 2-8
        int priority = nextRandom(4);        // Rango: 0-3
        int deadline = instructions + nextRandom(8) + 3; // inst + offset 3-10

        // 30% de probabilidad de tener una instrucción de E/S
        int ioInstruction = -1;
        int ioDuration = 0;
        if (nextRandom(10) < 3 && instructions > 2) {
            ioInstruction = nextRandom(instructions - 1) + 1; // Entre la instrucción 1 y penúltima
            ioDuration = 3;                  // Duración 3 ciclos
        }

        return new Process(id, name, instructions, priority, deadline, ioInstruction, ioDuration);
    }

    /**
     * Genera una lista de N procesos aleatorios para pruebas de estrés.
     * Mezcla prioridades y distribuye deadlines para poner a prueba el planificador.
     * 
     * @param count Cantidad de procesos a generar.
     * @return ListaDobleEnlazada con los procesos generados.
     */
    public static ListaDobleEnlazada<Process> createStressLoad(int count) {
        ListaDobleEnlazada<Process> processes = new ListaDobleEnlazada<>();

        for (int i = 0; i < count; i++) {
            globalCounter++;
            String id = "STR" + globalCounter;
            String name = SUBSYSTEMS[nextRandom(SUBSYSTEMS.length)]
                    + "_" + SUFFIXES[nextRandom(SUFFIXES.length)]
                    + "_" + globalCounter;

            int instructions = nextRandom(8) + 2; // Rango: 2-9
            int priority = i % 4;                // Distribución uniforme (0,1,2,3)

            // Configuración de Deadlines:
            int deadline;
            if (i % 5 == 0) {
                // 20% Críticos: deadline muy ajustado
                deadline = instructions + nextRandom(3) + 1;
            } else if (i % 5 == 1) {
                // 20% Medios: deadline moderado
                deadline = instructions + nextRandom(6) + 4;
            } else {
                // 60% Holgados: deadline amplio
                deadline = instructions + nextRandom(12) + 8;
            }

            // 25% de probabilidad de E/S
            int ioInstruction = -1;
            int ioDuration = 0;
            if (nextRandom(4) == 0 && instructions > 2) {
                ioInstruction = nextRandom(instructions - 1) + 1;
                ioDuration = 3;
            }

            processes.addLast(new Process(id, name, instructions, priority, deadline,
                    ioInstruction, ioDuration));
        }

        return processes;
    }

    /**
     * Resetea el contador global de IDs.
     */
    public static void resetCounter() {
        globalCounter = 0;
    }

    /**
     * Obtiene el valor actual del contador global.
     */
    public static int getGlobalCounter() {
        return globalCounter;
    }
}