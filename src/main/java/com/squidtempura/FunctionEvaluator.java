package com.squidtempura;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FunctionEvaluator {

    private final Map<String, String> functions = new HashMap<>();
    private final List<Function> compiledFunctions = new ArrayList<>();
    private boolean functionsDirty = true;
    private static final int MAX_DEPTH = 20;
    private final ThreadLocal<Integer> evalDepth = ThreadLocal.withInitial(() -> 0);

    public double evaluate(String expr, double x) {
        return evaluateInternal(expr, x);
    }

    public void setFunctions(Map<String, String> defs) {
        functions.clear();
        if (defs != null) {
            functions.putAll(defs);
        }
        functionsDirty = true;
    }

    private double evaluateInternal(String expr, double x) {
        int depth = evalDepth.get();
        if (depth > MAX_DEPTH) return Double.NaN;
        evalDepth.set(depth + 1);
        ensureFunctions();

        String processed = applyDerivativeSyntax(expr);
        try {
            Expression e = new ExpressionBuilder(processed)
                    .variable("x")
                    .functions(compiledFunctions)
                    .build()
                    .setVariable("x", x);

            return e.evaluate();

        } catch (Exception ex) {
            return Double.NaN;
        } finally {
            evalDepth.set(depth);
        }
    }

    private void ensureFunctions() {
        if (!functionsDirty) return;
        compiledFunctions.clear();
        for (Map.Entry<String, String> entry : functions.entrySet()) {
            String name = entry.getKey();
            String body = entry.getValue();
            compiledFunctions.add(new Function(name, 1) {
                @Override
                public double apply(double... args) {
                    return evaluateInternal(body, args[0]);
                }
            });

            String dName = name + "_d";
            compiledFunctions.add(new Function(dName, 1) {
                @Override
                public double apply(double... args) {
                    double x = args[0];
                    double h = 1e-5 * (1.0 + Math.abs(x));
                    double f1 = evaluateInternal(body, x + h);
                    double f2 = evaluateInternal(body, x - h);
                    if (Double.isNaN(f1) || Double.isNaN(f2)) return Double.NaN;
                    return (f1 - f2) / (2.0 * h);
                }
            });
        }
        functionsDirty = false;
    }

    private String applyDerivativeSyntax(String expr) {
        String out = expr;
        for (String name : functions.keySet()) {
            out = out.replace(name + "'(", name + "_d(");
            out = out.replace(name + "' (", name + "_d(");
        }
        return out;
    }
}
