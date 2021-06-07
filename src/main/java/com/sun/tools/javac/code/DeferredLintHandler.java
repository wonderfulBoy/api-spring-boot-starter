package com.sun.tools.javac.code;

import com.sun.tools.javac.tree.EndPosTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.ListBuffer;

import java.util.HashMap;
import java.util.Map;

public class DeferredLintHandler {
    protected static final Context.Key<DeferredLintHandler> deferredLintHandlerKey =
            new Context.Key<DeferredLintHandler>();
    private static final DiagnosticPosition IMMEDIATE_POSITION = new DiagnosticPosition() {
        @Override
        public JCTree getTree() {
            Assert.error();
            return null;
        }

        @Override
        public int getStartPosition() {
            Assert.error();
            return -1;
        }

        @Override
        public int getPreferredPosition() {
            Assert.error();
            return -1;
        }

        @Override
        public int getEndPosition(EndPosTable endPosTable) {
            Assert.error();
            return -1;
        }
    };
    private DiagnosticPosition currentPos;
    private Map<DiagnosticPosition, ListBuffer<LintLogger>> loggersQueue = new HashMap<DiagnosticPosition, ListBuffer<LintLogger>>();

    protected DeferredLintHandler(Context context) {
        context.put(deferredLintHandlerKey, this);
        this.currentPos = IMMEDIATE_POSITION;
    }

    public static DeferredLintHandler instance(Context context) {
        DeferredLintHandler instance = context.get(deferredLintHandlerKey);
        if (instance == null)
            instance = new DeferredLintHandler(context);
        return instance;
    }

    public void report(LintLogger logger) {
        if (currentPos == IMMEDIATE_POSITION) {
            logger.report();
        } else {
            ListBuffer<LintLogger> loggers = loggersQueue.get(currentPos);
            if (loggers == null) {
                loggersQueue.put(currentPos, loggers = new ListBuffer<>());
            }
            loggers.append(logger);
        }
    }

    public void flush(DiagnosticPosition pos) {
        ListBuffer<LintLogger> loggers = loggersQueue.get(pos);
        if (loggers != null) {
            for (LintLogger lintLogger : loggers) {
                lintLogger.report();
            }
            loggersQueue.remove(pos);
        }
    }

    public DiagnosticPosition setPos(DiagnosticPosition currentPos) {
        DiagnosticPosition prevPosition = this.currentPos;
        this.currentPos = currentPos;
        return prevPosition;
    }

    public DiagnosticPosition immediate() {
        return setPos(IMMEDIATE_POSITION);
    }

    public interface LintLogger {
        void report();
    }
}
