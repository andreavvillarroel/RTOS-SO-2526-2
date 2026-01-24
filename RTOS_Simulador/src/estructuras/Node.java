/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package estructuras;

/**
 * Clase Nodo Genérica. 
 * @author Andrea
 */

public class Node<T> {
    private T data;          // El objeto que guardamos (ej: un Proceso)
    private Node<T> next;    // Puntero al siguiente nodo
    private Node<T> previous; // Puntero al nodo anterior (Lista Doble)

    // Constructor
    public Node(T data) {
        this.data = data;
        this.next = null;
        this.previous = null;
    }

    // --- Getters y Setters ---
    
    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Node<T> getNext() {
        return next;
    }

    public void setNext(Node<T> next) {
        this.next = next;
    }

    public Node<T> getPrevious() {
        return previous;
    }

    public void setPrevious(Node<T> previous) {
        this.previous = previous;
    }
}