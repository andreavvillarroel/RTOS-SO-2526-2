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

    private int ioInstruction;      // En qué instrucción se bloquea
    private int ioDuration;         // Cuántos ciclos se queda bloqueado
    private int remainingIoTime;    // Cuenta regresiva del bloqueo
    private boolean hasDoneIO;    // Para que no se bloquee infinitas veces
    private int quantumConsumido; // Ciclos consumidos del quantum actual (para RR)
    private int effectivePriority; // Prioridad efectiva (puede cambiar con Aging)
    private int waitCycles;        // Ciclos que lleva esperando en cola (para Aging)
    
    public Process(String id, String name, int totalInstructions, int priority, int deadline, int ioInstruction, int ioDuration) {
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
        this.ioInstruction = ioInstruction;
        this.ioDuration = ioDuration;
        this.remainingIoTime = 0;
        this.hasDoneIO = false;
        this.quantumConsumido = 0;
        this.effectivePriority = priority; // Comienza igual a la prioridad base
        this.waitCycles = 0;
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
    public boolean shouldBlock() { return !hasDoneIO && executedInstructions == ioInstruction; }
    public void startIO() {this.remainingIoTime = ioDuration;this.hasDoneIO = true;this.status = "Bloqueado";}
    public void tickIO() {if (remainingIoTime > 0) remainingIoTime--;}
    public boolean isIoFinished() {return remainingIoTime <= 0;}
    
    // --- Getters agregados para el Planificador EDF ---
    public int getPc() { return pc; }
    public int getMar() { return mar; }
    public int getDeadline() { return deadline; }

    /**
     * Restaura los registros PC y MAR al recargar un proceso
     * que fue expulsado por preempción (cambio de contexto).
     */
    public void restoreContext(int pc, int mar) {
        this.pc = pc;
        this.mar = mar;
    }
    
     // --- Getters agregados para el Planificador Round Robin ---
    public int getQuantumConsumido()                { return quantumConsumido; }
    public void setQuantumConsumido(int q)          { this.quantumConsumido = q; }
    public void incrementQuantumConsumido()         { this.quantumConsumido++; }
    public void resetQuantumConsumido()             { this.quantumConsumido = 0; }
    
    // --- Getters y métodos para Planificador de Prioridad con Aging ---
    public int getEffectivePriority()               { return effectivePriority; }
    public void setEffectivePriority(int p)         { this.effectivePriority = p; }
    public int getWaitCycles()                      { return waitCycles; }
    public void incrementWaitCycles()               { this.waitCycles++; }
    public void resetWaitCycles()                   { this.waitCycles = 0; }

    /**
     * Aplica Aging: reduce la prioridad efectiva (la sube en importancia).
     * No puede bajar de 0 (la máxima prioridad).
     */
    public void applyAging(int boost) {
        this.effectivePriority = Math.max(0, this.effectivePriority - boost);
    }

    /**
     * Restaura la prioridad efectiva a la prioridad base original.
     * Se llama cuando el proceso entra a CPU o se necesita resetear.
     */
    public void resetEffectivePriority() {
        this.effectivePriority = this.priority;
    }
    
    // --- Getters agregados para el Planificador SRT ---
    public int getRemainingInstructions() { return totalInstructions - executedInstructions; }
    public int getTotalInstructions()     { return totalInstructions; }
    public int getExecutedInstructions()  { return executedInstructions; }
}
    
