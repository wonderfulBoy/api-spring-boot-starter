package com.sun.tools.javac.util;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ListBuffer<A> extends AbstractQueue<A> {
    private List<A> elems;
    private List<A> last;
    private int count;
    private boolean shared;

    public ListBuffer() {
        clear();
    }

    public static <T> ListBuffer<T> of(T x) {
        ListBuffer<T> lb = new ListBuffer<T>();
        lb.add(x);
        return lb;
    }

    public final void clear() {
        this.elems = List.nil();
        this.last = null;
        count = 0;
        shared = false;
    }

    public int length() {
        return count;
    }

    public int size() {
        return count;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public boolean nonEmpty() {
        return count != 0;
    }

    private void copy() {
        if (elems.nonEmpty()) {
            List<A> orig = elems;
            elems = last = List.of(orig.head);
            while ((orig = orig.tail).nonEmpty()) {
                last.tail = List.of(orig.head);
                last = last.tail;
            }
        }
    }

    public ListBuffer<A> prepend(A x) {
        elems = elems.prepend(x);
        if (last == null) last = elems;
        count++;
        return this;
    }

    public ListBuffer<A> append(A x) {
        x.getClass();
        if (shared) copy();
        List<A> newLast = List.of(x);
        if (last != null) {
            last.tail = newLast;
            last = newLast;
        } else {
            elems = last = newLast;
        }
        count++;
        return this;
    }

    public ListBuffer<A> appendList(List<A> xs) {
        while (xs.nonEmpty()) {
            append(xs.head);
            xs = xs.tail;
        }
        return this;
    }

    public ListBuffer<A> appendList(ListBuffer<A> xs) {
        return appendList(xs.toList());
    }

    public ListBuffer<A> appendArray(A[] xs) {
        for (int i = 0; i < xs.length; i++) {
            append(xs[i]);
        }
        return this;
    }

    public List<A> toList() {
        shared = true;
        return elems;
    }

    public boolean contains(Object x) {
        return elems.contains(x);
    }

    public <T> T[] toArray(T[] vec) {
        return elems.toArray(vec);
    }

    public Object[] toArray() {
        return toArray(new Object[size()]);
    }

    public A first() {
        return elems.head;
    }

    public A next() {
        A x = elems.head;
        if (!elems.isEmpty()) {
            elems = elems.tail;
            if (elems.isEmpty()) last = null;
            count--;
        }
        return x;
    }

    public Iterator<A> iterator() {
        return new Iterator<A>() {
            List<A> elems = ListBuffer.this.elems;

            public boolean hasNext() {
                return !elems.isEmpty();
            }

            public A next() {
                if (elems.isEmpty())
                    throw new NoSuchElementException();
                A elem = elems.head;
                elems = elems.tail;
                return elem;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    public boolean add(A a) {
        append(a);
        return true;
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection<?> c) {
        for (Object x : c) {
            if (!contains(x))
                return false;
        }
        return true;
    }

    public boolean addAll(Collection<? extends A> c) {
        for (A a : c)
            append(a);
        return true;
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean offer(A a) {
        append(a);
        return true;
    }

    public A poll() {
        return next();
    }

    public A peek() {
        return first();
    }

    public A last() {
        return last != null ? last.head : null;
    }
}
