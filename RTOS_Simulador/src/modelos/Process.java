/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package modelos;

/**
 *
 * @author Andrea
 */
public class Process {
  // Datos de identificación
    private String id;
    private String name;
    private String status; // Nuevo, Listo, Ejecución, Bloqueado, Terminado, etc.

    // Elementos del PCB 
    private int pc;                 // Program Counter
    private int mar;                // Memory Address Register
    private int priority;           // Prioridad
    private int totalInstructions;  // Cantidad de instrucciones total
    private int executedInstructions; // Instrucciones ya procesadas
    
    // Tiempo Real
    private int deadline;           // Tiempo límite original
    private int remainingDeadline;  // Cuenta regresiva visual para los deadlines

    public Process(String id, String name, int totalInstructions, int priority, int deadline) {
        this.id = id;
        this.name = name;
        this.totalInstructions = totalInstructions;
        this.executedInstructions = 0;
        this.priority = priority;
        this.deadline = deadline;
        this.remainingDeadline = deadline;
        this.status = "Nuevo";
        this.pc = 0;  // Comienza en 0
        this.mar = 0; // Comienza en 0
    }

    /**
     * Simula la ejecución de un ciclo de instrucción.     * El PDF dice: el PC y el MAR incrementarán una unidad por cada ciclo.
     */
    public void executeInstruction() {
        if (executedInstructions < totalInstructions) {
            executedInstructions++;
            pc++;
            mar++;
        }
    }

    /**
     * Reduce el tiempo restante de vida (Deadline).
     */
    public void updateDeadline() {
        if (remainingDeadline > 0) {
            remainingDeadline--;
        }
    }

    // --- Getters y Setters necesarios para la GUI ---
    public String getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRemainingDeadline() { return remainingDeadline; }
    public int getPriority() { return priority; }
    public boolean isFinished() { return executedInstructions >= totalInstructions; }
}
