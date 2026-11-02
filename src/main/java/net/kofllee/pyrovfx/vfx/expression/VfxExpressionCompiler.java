package net.kofllee.pyrovfx.vfx.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kofllee.pyrovfx.vfx.definition.VfxEmitterDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxEventDefinition;
import net.kofllee.pyrovfx.vfx.definition.VfxParameterDefinition;
import net.kofllee.pyrovfx.vfx.curve.VfxCurveSet;

/** Bind all expressions in one definition to a shared immutable variable table. */
public final class VfxExpressionCompiler {
    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private VfxExpressionCompiler() {}

    public static Session beginProgram() {
        Session session = new Session(CURRENT.get());
        CURRENT.set(session);
        return session;
    }

    public record CompiledNumber(VfxExpressionNode node, VfxProgram.ExpressionInfo info) {}

    public static VfxExpressionNode compileNumber(String source) {
        return compileNumberWithInfo(source).node();
    }

    public static CompiledNumber compileNumberWithInfo(String source) {
        Session session = CURRENT.get();
        VfxExpressionParser parser = session == null
                ? new VfxExpressionParser(source)
                : new VfxExpressionParser(source, session.layout, session.curves);
        VfxExpressionNode node = parser.parse();
        VfxProgram.ExpressionInfo info = parser.expressionInfo();
        if (session != null) {
            session.expressions.add(info);
            if (info.constant()) {
                // Pure math expressions have no variables, mutable params, curves or RNG.
                // Keep the actual numeric result, not a fresh evaluation every particle tick.
                double constant = node.evaluate(VfxExpressionContext.root());
                return new CompiledNumber(new ConstantNode(constant), info);
            }
        }
        return new CompiledNumber(node, info);
    }

    public record ConstantNode(double value) implements VfxExpressionNode {
        @Override
        public double evaluate(VfxExpressionContext context) {
            return value;
        }
    }

    public static final class Session implements AutoCloseable {
        private final Session previous;
        private final VfxVariableLayout.Builder layout = new VfxVariableLayout.Builder();
        private final List<VfxProgram.ExpressionInfo> expressions = new ArrayList<>();
        private VfxCurveSet curves;
        private boolean closed;

        private Session(Session previous) {
            this.previous = previous;
        }

        public VfxProgram program() {
            if (closed) {
                throw new IllegalStateException("Compilation scope already closed");
            }
            return new VfxProgram(layout.build(), expressions);
        }

        public VfxProgram program(List<VfxEmitterDefinition> emitters,
                                  Map<String, VfxEventDefinition> events,
                                  Map<String, VfxParameterDefinition> parameters) {
            if (closed) {
                throw new IllegalStateException("Compilation scope already closed");
            }
            List<VfxProgram.ParameterBinding> bindings = new ArrayList<>();
            for (String id : parameters.keySet()) {
                bindings.add(new VfxProgram.ParameterBinding(id, layout.intern("param." + id)));
            }
            return new VfxProgram(layout.build(), expressions, emitters, events, bindings);
        }

        public void registerVariable(String name) {
            layout.intern(name);
        }

        public void setCurves(VfxCurveSet curves) {
            this.curves = curves;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
