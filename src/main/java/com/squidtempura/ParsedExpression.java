package com.squidtempura;

public class ParsedExpression {
    public final String baseExpression;
    public final Constraint constraint;
    public final ExprType type;
    public final double xConst;

    public ParsedExpression(String baseExpression, Constraint constraint, ExprType type, double xConst) {
        this.baseExpression = baseExpression;
        this.constraint = constraint;
        this.type = type;
        this.xConst = xConst;
    }
}
