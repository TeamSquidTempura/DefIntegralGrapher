package com.squidtempura;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

public class FunctionEvaluator {

    public double evaluate(String expr, double x) {
        try {
            Expression e = new ExpressionBuilder(expr)
                    .variable("x")
                    .build()
                    .setVariable("x", x);

            return e.evaluate();

        } catch (Exception ex) {
            return Double.NaN;
        }
    }
}