package com.github.api.sun.source.util;

import com.github.api.sun.source.doctree.DocCommentTree;
import com.github.api.sun.source.doctree.DocTree;

import java.util.Iterator;

@jdk.Exported
public class DocTreePath implements Iterable<DocTree> {
    private final TreePath treePath;
    private final DocCommentTree docComment;
    private final DocTree leaf;
    private final DocTreePath parent;

    public DocTreePath(TreePath treePath, DocCommentTree t) {
        treePath.getClass();
        t.getClass();
        this.treePath = treePath;
        this.docComment = t;
        this.parent = null;
        this.leaf = t;
    }

    public DocTreePath(DocTreePath p, DocTree t) {
        if (t.getKind() == DocTree.Kind.DOC_COMMENT) {
            throw new IllegalArgumentException("Use DocTreePath(TreePath, DocCommentTree) to construct DocTreePath for a DocCommentTree.");
        } else {
            treePath = p.treePath;
            docComment = p.docComment;
            parent = p;
        }
        leaf = t;
    }

    public static DocTreePath getPath(TreePath treePath, DocCommentTree doc, DocTree target) {
        return getPath(new DocTreePath(treePath, doc), target);
    }

    public static DocTreePath getPath(DocTreePath path, DocTree target) {
        path.getClass();
        target.getClass();
        class Result extends Error {
            static final long serialVersionUID = -5942088234594905625L;
            DocTreePath path;

            Result(DocTreePath path) {
                this.path = path;
            }
        }
        class PathFinder extends DocTreePathScanner<DocTreePath, DocTree> {
            public DocTreePath scan(DocTree tree, DocTree target) {
                if (tree == target) {
                    throw new Result(new DocTreePath(getCurrentPath(), target));
                }
                return super.scan(tree, target);
            }
        }
        if (path.getLeaf() == target) {
            return path;
        }
        try {
            new PathFinder().scan(path, target);
        } catch (Result result) {
            return result.path;
        }
        return null;
    }

    public TreePath getTreePath() {
        return treePath;
    }

    public DocCommentTree getDocComment() {
        return docComment;
    }

    public DocTree getLeaf() {
        return leaf;
    }

    public DocTreePath getParentPath() {
        return parent;
    }

    public Iterator<DocTree> iterator() {
        return new Iterator<DocTree>() {
            private DocTreePath next = DocTreePath.this;

            public boolean hasNext() {
                return next != null;
            }

            public DocTree next() {
                DocTree t = next.leaf;
                next = next.parent;
                return t;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
