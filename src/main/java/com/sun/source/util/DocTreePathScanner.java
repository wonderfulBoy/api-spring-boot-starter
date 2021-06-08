package com.sun.source.util;

import com.sun.source.doctree.DocTree;

@jdk.Exported
public class DocTreePathScanner<R, P> extends DocTreeScanner<R, P> {
    private DocTreePath path;

    public R scan(DocTreePath path, P p) {
        this.path = path;
        try {
            return path.getLeaf().accept(this, p);
        } finally {
            this.path = null;
        }
    }

    @Override
    public R scan(DocTree tree, P p) {
        if (tree == null)
            return null;
        DocTreePath prev = path;
        path = new DocTreePath(path, tree);
        try {
            return tree.accept(this, p);
        } finally {
            path = prev;
        }
    }

    public DocTreePath getCurrentPath() {
        return path;
    }
}
