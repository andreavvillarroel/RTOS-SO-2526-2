/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package estructuras;

/**
 * Lista Doblemente Enlazada Personalizada.
 * Sustituye a ArrayList y LinkedList de java.util
 * 
 * @param <T> Tipo de dato a almacenar
 * @author Andrea
 */
public class ListaDobleEnlazada<T> {
    private Node<T> head;
    private Node<T> tail;
    private int size;

    public ListaDobleEnlazada() {
        this.head = null;
        this.tail = null;
        this.size = 0;
    }

    // Agregar al final 
    public void addLast(T data) {
        Node<T> newNode = new Node<>(data);
        if (isEmpty()) {
            head = tail = newNode;
        } else {
            tail.setNext(newNode);
            newNode.setPrevious(tail);
            tail = newNode;
        }
        size++;
    }

    // Eliminar un objeto específico 
    public void remove(T data) {
        Node<T> current = head;
        while (current != null) {
            if (current.getData().equals(data)) {
                if (current.getPrevious() != null) {
                    current.getPrevious().setNext(current.getNext());
                } else {
                    head = current.getNext();
                }

                if (current.getNext() != null) {
                    current.getNext().setPrevious(current.getPrevious());
                } else {
                    tail = current.getPrevious();
                }
                size--;
                return;
            }
            current = current.getNext();
        }
    }

    // Obtener el primer elemento 
    public T getFirst() {
        return (head != null) ? head.getData() : null;
    }

    // Eliminar y devolver el primero 
    public T pollFirst() {
        if (isEmpty()) return null;
        T data = head.getData();
        remove(data);
        return data;
    }

    // Obtener elemento por índice 
    public T get(int index) {
        if (index < 0 || index >= size) return null;
        Node<T> current = head;
        for (int i = 0; i < index; i++) {
            current = current.getNext();
        }
        return current.getData();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getSize() {
        return size;
    }
}
