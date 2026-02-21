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
    private final Map<String, Double> integralCache = new HashMap<>();
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
        integralCache.clear();
    }

    private double evaluateInternal(String expr, double x) {
        int depth = evalDepth.get();
        if (depth > MAX_DEPTH) return Double.NaN;
        evalDepth.set(depth + 1);
        ensureFunctions();

        String processed = applyDerivativeSyntax(expr);
        processed = evaluateIntegrals(processed, x);
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

    private String evaluateIntegrals(String expr, double x) {
        String out = expr;
        int safety = 0;
        while (true) {
            int idx = out.lastIndexOf("int(");
            if (idx < 0) break;
            if (safety++ > 50) break;

            ParsedIntegral parsed = parseIntegral(out, idx);
            if (parsed == null) break;

            boolean aHasX = containsVarX(parsed.aExpr);
            boolean bHasX = containsVarX(parsed.bExpr);
            String cacheKey = null;
            if (!aHasX && !bHasX) {
                cacheKey = parsed.integrandExpr + "|" + parsed.aExpr + "|" + parsed.bExpr;
                Double cached = integralCache.get(cacheKey);
                if (cached != null) {
                    String replacement = Double.isNaN(cached) ? "NaN" : Double.toString(cached);
                    out = out.substring(0, idx) + replacement + out.substring(parsed.endIndex + 1);
                    continue;
                }
            }

            double a = evaluateSimpleExpression(parsed.aExpr, x);
            double b = evaluateSimpleExpression(parsed.bExpr, x);
            double value = integrateSimpson(parsed.integrandExpr, a, b);
            if (cacheKey != null) {
                integralCache.put(cacheKey, value);
            }

            String replacement = Double.isNaN(value) ? "NaN" : Double.toString(value);
            out = out.substring(0, idx) + replacement + out.substring(parsed.endIndex + 1);
        }
        return out;
    }

    private ParsedIntegral parseIntegral(String expr, int intIndex) {
        int start = intIndex + 4; // after "int("
        int depth = 1;
        int i = start;
        for (; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) break;
            }
        }
        if (depth != 0) return null;

        String inside = expr.substring(start, i);
        String[] parts = splitTopLevelCommas(inside);
        if (parts.length != 3) return null;

        ParsedIntegral p = new ParsedIntegral();
        p.integrandExpr = parts[0].trim();
        p.aExpr = parts[1].trim();
        p.bExpr = parts[2].trim();
        p.endIndex = i;
        return p;
    }

    private String[] splitTopLevelCommas(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int last = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(last, i));
                last = i + 1;
            }
        }
        parts.add(s.substring(last));
        return parts.toArray(new String[0]);
    }

    private double evaluateSimpleExpression(String expr, double x) {
        String processed = applyDerivativeSyntax(expr);
        processed = evaluateIntegrals(processed, x);
        try {
            Expression e = new ExpressionBuilder(processed)
                    .variable("x")
                    .functions(compiledFunctions)
                    .build()
                    .setVariable("x", x);
            return e.evaluate();
        } catch (Exception ex) {
            return Double.NaN;
        }
    }

    //Simpson's rule
    /*
    int ab f(x) dx = [(b-a)/6]{f(a)+4f([a+b]/2)+f(b)}
     */
    private double integrateSimpson(String integrandExpr, double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) return Double.NaN;
        if (a == b) return 0.0;
        double left = a;
        double right = b;
        double sign = 1.0;
        if (right < left) {
            double tmp = left;
            left = right;
            right = tmp;
            sign = -1.0;
        }

        int n = 200;
        if (n % 2 == 1) n++;
        double h = (right - left) / n;

        double sum = evaluateSimpleExpression(integrandExpr, left);
        double end = evaluateSimpleExpression(integrandExpr, right);
        if (Double.isNaN(sum) || Double.isNaN(end)) return Double.NaN;
        sum += end;

        for (int i = 1; i < n; i++) {
            double x = left + i * h;
            double fx = evaluateSimpleExpression(integrandExpr, x);
            if (Double.isNaN(fx)) return Double.NaN;
            sum += (i % 2 == 0 ? 2.0 : 4.0) * fx;
        }
        return sign * (h / 3.0) * sum;
    }

    private boolean containsVarX(String expr) {
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == 'x' || c == 'X') {
                boolean leftOk = i == 0 || !Character.isLetterOrDigit(expr.charAt(i - 1));
                boolean rightOk = i == expr.length() - 1 || !Character.isLetterOrDigit(expr.charAt(i + 1));
                if (leftOk && rightOk) return true;
            }
        }
        return false;
    }

    private String applyDerivativeSyntax(String expr) {
        String out = expr;
        for (String name : functions.keySet()) {
            out = out.replace(name + "'(", name + "_d(");
            out = out.replace(name + "' (", name + "_d(");
        }
        return out;
    }

    private static class ParsedIntegral {
        String integrandExpr;
        String aExpr;
        String bExpr;
        int endIndex;
    }
}
