/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package estructuras;

/**
 * Estructura de datos de tipo Cola (FIFO).
 * Utiliza la ListaDobleEnlazada interna para cumplir con las restricciones.
 * 
 * @param <T> Tipo de dato a almacenar
 * @author Andrea
 */
public class MyQueue<T> {
    private ListaDobleEnlazada<T> list;

    public MyQueue() {
        this.list = new ListaDobleEnlazada<>();
    }

    /**
     * Inserta un elemento al final de la cola (Enqueue).
     * @param data el dato a insertar
     */
    public void enqueue(T data) {
        list.addLast(data);
    }

    /**
     * Retira y devuelve el primer elemento de la cola (Dequeue).
     * @return el dato al frente de la cola, o null si está vacía
     */
    public T dequeue() {
        return list.pollFirst();
    }

    /**
     * Mira el primer elemento sin retirarlo.
     * @return el dato al frente
     */
    public T peek() {
        return list.getFirst();
    }

    /**
     * Verifica si la cola está vacía.
     * @return true si está vacía
     */
    public boolean isEmpty() {
        return list.isEmpty();
    }

    /**
     * Devuelve el número de elementos en la cola.
     * @return cantidad de elementos
     */
    public int getSize() {
        return list.getSize();
    }
    
    /**
     * Permite obtener la lista interna.
     * @return la lista enlazada
     */
    public ListaDobleEnlazada<T> getList() {
        return list;
    }
}
