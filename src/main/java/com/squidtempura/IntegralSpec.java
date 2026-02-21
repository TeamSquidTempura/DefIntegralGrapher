package com.squidtempura;

public class IntegralSpec {
    public final String integrandExpr;
    public final String aExpr;
    public final String bExpr;

    public IntegralSpec(String integrandExpr, String aExpr, String bExpr) {
        this.integrandExpr = integrandExpr;
        this.aExpr = aExpr;
        this.bExpr = bExpr;
    }

    public String key() {
        return integrandExpr + "|" + aExpr + "|" + bExpr;
    }

    public String toExpression() {
        return "int(" + integrandExpr + "," + aExpr + "," + bExpr + ")";
    }
}
