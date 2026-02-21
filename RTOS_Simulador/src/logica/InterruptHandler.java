/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package logica;
import java.util.Random;
/**
 * Hilo independiente que genera eventos externos inesperados (Interrupciones).
 * @author Andrea
 */
public class InterruptHandler extends Thread {
    private SimulationClock clock;
    private boolean running;
    private Random random;

    public InterruptHandler(SimulationClock clock) {
        this.clock = clock;
        this.running = true;
        this.random = new Random();
    }

    @Override
    public void run() {
        while (running) {
            try {
                // El hilo "duerme" un tiempo aleatorio entre 5 y 10 segundos
                // antes de generar la próxima interrupción.
                int sleepTime = (random.nextInt(6) + 5) * 1000;
                Thread.sleep(sleepTime);

                // Generar la interrupción
                triggerInterrupt();

            } catch (InterruptedException e) {
                running = false;
            }
        }
    }

    private void triggerInterrupt() {
        System.out.println("\n--- [ALERTA] ¡INTERRUPCIÓN DE HARDWARE DETECTADA! ---");
        System.out.println("Causa: Impacto de micro-meteorito detectado.");
        

        // Aquí llamaremos a una rutina de servicio (ISR)
        clock.handleInterrupt();
    }

    public void stopHandler() {
        this.running = false;
        this.interrupt(); // Corta el Thread.sleep inmediatamente
    }
}
