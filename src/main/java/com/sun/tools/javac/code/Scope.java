package com.sun.tools.javac.code;

import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import java.util.Iterator;

public class Scope {

    public static final Scope emptyScope = new Scope(null, null, new Entry[]{});
    static final Filter<Symbol> noFilter = new Filter<Symbol>() {
        public boolean accepts(Symbol s) {
            return true;
        }
    };
    private static final Entry sentinel = new Entry(null, null, null, null);
    private static final int INITIAL_SIZE = 0x10;
    public Scope next;
    public Symbol owner;
    public Entry elems;
    Entry[] table;
    int hashMask;
    int nelems = 0;
    List<ScopeListener> listeners = List.nil();
    private int shared;

    private Scope(Scope next, Symbol owner, Entry[] table) {
        this.next = next;
        Assert.check(emptyScope == null || owner != null);
        this.owner = owner;
        this.table = table;
        this.hashMask = table.length - 1;
    }

    private Scope(Scope next, Symbol owner, Entry[] table, int nelems) {
        this(next, owner, table);
        this.nelems = nelems;
    }

    public Scope(Symbol owner) {
        this(null, owner, new Entry[INITIAL_SIZE]);
    }

    public Scope dup() {
        return dup(this.owner);
    }

    public Scope dup(Symbol newOwner) {
        Scope result = new Scope(this, newOwner, this.table, this.nelems);
        shared++;
        return result;
    }

    public Scope dupUnshared() {
        return new Scope(this, this.owner, this.table.clone(), this.nelems);
    }

    public Scope leave() {
        Assert.check(shared == 0);
        if (table != next.table) return next;
        while (elems != null) {
            int hash = getIndex(elems.sym.name);
            Entry e = table[hash];
            Assert.check(e == elems, elems.sym);
            table[hash] = elems.shadowed;
            elems = elems.sibling;
        }
        Assert.check(next.shared > 0);
        next.shared--;
        next.nelems = nelems;
        return next;
    }

    private void dble() {
        Assert.check(shared == 0);
        Entry[] oldtable = table;
        Entry[] newtable = new Entry[oldtable.length * 2];
        for (Scope s = this; s != null; s = s.next) {
            if (s.table == oldtable) {
                Assert.check(s == this || s.shared != 0);
                s.table = newtable;
                s.hashMask = newtable.length - 1;
            }
        }
        int n = 0;
        for (int i = oldtable.length; --i >= 0; ) {
            Entry e = oldtable[i];
            if (e != null && e != sentinel) {
                table[getIndex(e.sym.name)] = e;
                n++;
            }
        }
        nelems = n;
    }

    public void enter(Symbol sym) {
        Assert.check(shared == 0);
        enter(sym, this);
    }

    public void enter(Symbol sym, Scope s) {
        enter(sym, s, s, false);
    }

    public void enter(Symbol sym, Scope s, Scope origin, boolean staticallyImported) {
        Assert.check(shared == 0);
        if (nelems * 3 >= hashMask * 2)
            dble();
        int hash = getIndex(sym.name);
        Entry old = table[hash];
        if (old == null) {
            old = sentinel;
            nelems++;
        }
        Entry e = makeEntry(sym, old, elems, s, origin, staticallyImported);
        table[hash] = e;
        elems = e;
        for (List<ScopeListener> l = listeners; l.nonEmpty(); l = l.tail) {
            l.head.symbolAdded(sym, this);
        }
    }

    Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin, boolean staticallyImported) {
        return new Entry(sym, shadowed, sibling, scope);
    }

    public void addScopeListener(ScopeListener sl) {
        listeners = listeners.prepend(sl);
    }

    public void remove(Symbol sym) {
        Assert.check(shared == 0);
        Entry e = lookup(sym.name);
        if (e.scope == null) return;

        int i = getIndex(sym.name);
        Entry te = table[i];
        if (te == e)
            table[i] = e.shadowed;
        else while (true) {
            if (te.shadowed == e) {
                te.shadowed = e.shadowed;
                break;
            }
            te = te.shadowed;
        }

        te = elems;
        if (te == e)
            elems = e.sibling;
        else while (true) {
            if (te.sibling == e) {
                te.sibling = e.sibling;
                break;
            }
            te = te.sibling;
        }

        for (List<ScopeListener> l = listeners; l.nonEmpty(); l = l.tail) {
            l.head.symbolRemoved(sym, this);
        }
    }

    public void enterIfAbsent(Symbol sym) {
        Assert.check(shared == 0);
        Entry e = lookup(sym.name);
        while (e.scope == this && e.sym.kind != sym.kind) e = e.next();
        if (e.scope != this) enter(sym);
    }

    public boolean includes(Symbol c) {
        for (Entry e = lookup(c.name);
             e.scope == this;
             e = e.next()) {
            if (e.sym == c) return true;
        }
        return false;
    }

    public Entry lookup(Name name) {
        return lookup(name, noFilter);
    }

    public Entry lookup(Name name, Filter<Symbol> sf) {
        Entry e = table[getIndex(name)];
        if (e == null || e == sentinel)
            return sentinel;
        while (e.scope != null && (e.sym.name != name || !sf.accepts(e.sym)))
            e = e.shadowed;
        return e;
    }

    int getIndex(Name name) {
        int h = name.hashCode();
        int i = h & hashMask;
        int x = hashMask - ((h + (h >> 16)) << 1);
        int d = -1;
        for (; ; ) {
            Entry e = table[i];
            if (e == null)
                return d >= 0 ? d : i;
            if (e == sentinel) {
                if (d < 0)
                    d = i;
            } else if (e.sym.name == name)
                return i;
            i = (i + x) & hashMask;
        }
    }

    public boolean anyMatch(Filter<Symbol> sf) {
        return getElements(sf).iterator().hasNext();
    }

    public Iterable<Symbol> getElements() {
        return getElements(noFilter);
    }

    public Iterable<Symbol> getElements(final Filter<Symbol> sf) {
        return new Iterable<Symbol>() {
            public Iterator<Symbol> iterator() {
                return new Iterator<Symbol>() {
                    private Scope currScope = Scope.this;
                    private Entry currEntry = elems;

                    {
                        update();
                    }

                    public boolean hasNext() {
                        return currEntry != null;
                    }

                    public Symbol next() {
                        Symbol sym = (currEntry == null ? null : currEntry.sym);
                        if (currEntry != null) {
                            currEntry = currEntry.sibling;
                        }
                        update();
                        return sym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void update() {
                        skipToNextMatchingEntry();
                        while (currEntry == null && currScope.next != null) {
                            currScope = currScope.next;
                            currEntry = currScope.elems;
                            skipToNextMatchingEntry();
                        }
                    }

                    void skipToNextMatchingEntry() {
                        while (currEntry != null && !sf.accepts(currEntry.sym)) {
                            currEntry = currEntry.sibling;
                        }
                    }
                };
            }
        };
    }

    public Iterable<Symbol> getElementsByName(Name name) {
        return getElementsByName(name, noFilter);
    }

    public Iterable<Symbol> getElementsByName(final Name name, final Filter<Symbol> sf) {
        return new Iterable<Symbol>() {
            public Iterator<Symbol> iterator() {
                return new Iterator<Symbol>() {
                    Entry currentEntry = lookup(name, sf);

                    public boolean hasNext() {
                        return currentEntry.scope != null;
                    }

                    public Symbol next() {
                        Entry prevEntry = currentEntry;
                        currentEntry = currentEntry.next(sf);
                        return prevEntry.sym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Scope[");
        for (Scope s = this; s != null; s = s.next) {
            if (s != this) result.append(" | ");
            for (Entry e = s.elems; e != null; e = e.sibling) {
                if (e != s.elems) result.append(", ");
                result.append(e.sym);
            }
        }
        result.append("]");
        return result.toString();
    }

    public interface ScopeListener {
        void symbolAdded(Symbol sym, Scope s);

        void symbolRemoved(Symbol sym, Scope s);
    }

    public static class Entry {

        public Symbol sym;
        public Entry sibling;
        public Scope scope;
        private Entry shadowed;

        public Entry(Symbol sym, Entry shadowed, Entry sibling, Scope scope) {
            this.sym = sym;
            this.shadowed = shadowed;
            this.sibling = sibling;
            this.scope = scope;
        }

        public Entry next() {
            return shadowed;
        }

        public Entry next(Filter<Symbol> sf) {
            if (shadowed.sym == null || sf.accepts(shadowed.sym)) return shadowed;
            else return shadowed.next(sf);
        }

        public boolean isStaticallyImported() {
            return false;
        }

        public Scope getOrigin() {
            return scope;
        }
    }

    public static class ImportScope extends Scope {

        public ImportScope(Symbol owner) {
            super(owner);
        }

        @Override
        Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope,
                        final Scope origin, final boolean staticallyImported) {
            return new Entry(sym, shadowed, sibling, scope) {
                @Override
                public Scope getOrigin() {
                    return origin;
                }

                @Override
                public boolean isStaticallyImported() {
                    return staticallyImported;
                }
            };
        }
    }

    public static class StarImportScope extends ImportScope implements ScopeListener {

        public StarImportScope(Symbol owner) {
            super(owner);
        }

        public void importAll(Scope fromScope) {
            for (Entry e = fromScope.elems; e != null; e = e.sibling) {
                if (e.sym.kind == Kinds.TYP && !includes(e.sym))
                    enter(e.sym, fromScope);
            }
            fromScope.addScopeListener(this);
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            remove(sym);
        }

        public void symbolAdded(Symbol sym, Scope s) {
        }
    }

    public static class DelegatedScope extends Scope {
        public static final Entry[] emptyTable = new Entry[0];
        Scope delegatee;

        public DelegatedScope(Scope outer) {
            super(outer, outer.owner, emptyTable);
            delegatee = outer;
        }

        public Scope dup() {
            return new DelegatedScope(next);
        }

        public Scope dupUnshared() {
            return new DelegatedScope(next);
        }

        public Scope leave() {
            return next;
        }

        public void enter(Symbol sym) {
        }

        public void enter(Symbol sym, Scope s) {
        }

        public void remove(Symbol sym) {
            throw new AssertionError(sym);
        }

        public Entry lookup(Name name) {
            return delegatee.lookup(name);
        }
    }

    public static class CompoundScope extends Scope implements ScopeListener {

        public static final Entry[] emptyTable = new Entry[0];

        private List<Scope> subScopes = List.nil();
        private int mark = 0;

        public CompoundScope(Symbol owner) {
            super(null, owner, emptyTable);
        }

        public void addSubScope(Scope that) {
            if (that != null) {
                subScopes = subScopes.prepend(that);
                that.addScopeListener(this);
                mark++;
                for (ScopeListener sl : listeners) {
                    sl.symbolAdded(null, this);
                }
            }
        }

        public void symbolAdded(Symbol sym, Scope s) {
            mark++;
            for (ScopeListener sl : listeners) {
                sl.symbolAdded(sym, s);
            }
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            mark++;
            for (ScopeListener sl : listeners) {
                sl.symbolRemoved(sym, s);
            }
        }

        public int getMark() {
            return mark;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("CompoundScope{");
            String sep = "";
            for (Scope s : subScopes) {
                buf.append(sep);
                buf.append(s);
                sep = ",";
            }
            buf.append("}");
            return buf.toString();
        }

        @Override
        public Iterable<Symbol> getElements(final Filter<Symbol> sf) {
            return new Iterable<Symbol>() {
                public Iterator<Symbol> iterator() {
                    return new CompoundScopeIterator(subScopes) {
                        Iterator<Symbol> nextIterator(Scope s) {
                            return s.getElements(sf).iterator();
                        }
                    };
                }
            };
        }

        @Override
        public Iterable<Symbol> getElementsByName(final Name name, final Filter<Symbol> sf) {
            return new Iterable<Symbol>() {
                public Iterator<Symbol> iterator() {
                    return new CompoundScopeIterator(subScopes) {
                        Iterator<Symbol> nextIterator(Scope s) {
                            return s.getElementsByName(name, sf).iterator();
                        }
                    };
                }
            };
        }

        @Override
        public Entry lookup(Name name, Filter<Symbol> sf) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scope dup(Symbol newOwner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enter(Symbol sym, Scope s, Scope origin, boolean staticallyImported) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Symbol sym) {
            throw new UnsupportedOperationException();
        }

        abstract class CompoundScopeIterator implements Iterator<Symbol> {

            private Iterator<Symbol> currentIterator;
            private List<Scope> scopesToScan;

            public CompoundScopeIterator(List<Scope> scopesToScan) {
                this.scopesToScan = scopesToScan;
                update();
            }

            abstract Iterator<Symbol> nextIterator(Scope s);

            public boolean hasNext() {
                return currentIterator != null;
            }

            public Symbol next() {
                Symbol sym = currentIterator.next();
                if (!currentIterator.hasNext()) {
                    update();
                }
                return sym;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private void update() {
                while (scopesToScan.nonEmpty()) {
                    currentIterator = nextIterator(scopesToScan.head);
                    scopesToScan = scopesToScan.tail;
                    if (currentIterator.hasNext()) return;
                }
                currentIterator = null;
            }
        }
    }

    public static class ErrorScope extends Scope {
        ErrorScope(Scope next, Symbol errSymbol, Entry[] table) {
            super(next, /*owner=*/errSymbol, table);
        }

        public ErrorScope(Symbol errSymbol) {
            super(errSymbol);
        }

        public Scope dup() {
            return new ErrorScope(this, owner, table);
        }

        public Scope dupUnshared() {
            return new ErrorScope(this, owner, table.clone());
        }

        public Entry lookup(Name name) {
            Entry e = super.lookup(name);
            if (e.scope == null)
                return new Entry(owner, null, null, null);
            else
                return e;
        }
    }
}
