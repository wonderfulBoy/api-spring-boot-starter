package com.github.api.sun.tools.javac.util;

public class GraphUtils {

    public static <D, N extends TarjanNode<D>> List<? extends List<? extends N>> tarjan(Iterable<? extends N> nodes) {
        ListBuffer<List<N>> cycles = new ListBuffer<>();
        ListBuffer<N> stack = new ListBuffer<>();
        int index = 0;
        for (N node : nodes) {
            if (node.index == -1) {
                index += tarjan(node, index, stack, cycles);
            }
        }
        return cycles.toList();
    }

    private static <D, N extends TarjanNode<D>> int tarjan(N v, int index, ListBuffer<N> stack, ListBuffer<List<N>> cycles) {
        v.index = index;
        v.lowlink = index;
        index++;
        stack.prepend(v);
        v.active = true;
        for (TarjanNode<D> nd : v.getAllDependencies()) {
            @SuppressWarnings("unchecked")
            N n = (N) nd;
            if (n.index == -1) {
                tarjan(n, index, stack, cycles);
                v.lowlink = Math.min(v.lowlink, n.lowlink);
            } else if (stack.contains(n)) {
                v.lowlink = Math.min(v.lowlink, n.index);
            }
        }
        if (v.lowlink == v.index) {
            N n;
            ListBuffer<N> cycle = new ListBuffer<>();
            do {
                n = stack.remove();
                n.active = false;
                cycle.add(n);
            } while (n != v);
            cycles.add(cycle.toList());
        }
        return index;
    }

    public static <D> String toDot(Iterable<? extends TarjanNode<D>> nodes, String name, String header) {
        StringBuilder buf = new StringBuilder();
        buf.append(String.format("digraph %s {\n", name));
        buf.append(String.format("label = \"%s\";\n", header));

        for (TarjanNode<D> n : nodes) {
            buf.append(String.format("%s [label = \"%s\"];\n", n.hashCode(), n.toString()));
        }

        for (TarjanNode<D> from : nodes) {
            for (DependencyKind dk : from.getSupportedDependencyKinds()) {
                for (TarjanNode<D> to : from.getDependenciesByKind(dk)) {
                    buf.append(String.format("%s -> %s [label = \" %s \" style = %s ];\n",
                            from.hashCode(), to.hashCode(), from.getDependencyName(to, dk), dk.getDotStyle()));
                }
            }
        }
        buf.append("}\n");
        return buf.toString();
    }

    public interface DependencyKind {

        String getDotStyle();
    }

    public static abstract class Node<D> {
        public final D data;

        public Node(D data) {
            this.data = data;
        }

        public abstract DependencyKind[] getSupportedDependencyKinds();

        public abstract Iterable<? extends Node<D>> getAllDependencies();

        public abstract String getDependencyName(Node<D> to, DependencyKind dk);

        @Override
        public String toString() {
            return data.toString();
        }
    }

    public static abstract class TarjanNode<D> extends Node<D> implements Comparable<TarjanNode<D>> {
        int index = -1;
        int lowlink;
        boolean active;

        public TarjanNode(D data) {
            super(data);
        }

        public abstract Iterable<? extends TarjanNode<D>> getAllDependencies();

        public abstract Iterable<? extends TarjanNode<D>> getDependenciesByKind(DependencyKind dk);

        public int compareTo(TarjanNode<D> o) {
            return (index < o.index) ? -1 : (index == o.index) ? 0 : 1;
        }
    }
}
