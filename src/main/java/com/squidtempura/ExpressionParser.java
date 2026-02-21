package com.squidtempura;

public class ExpressionParser {
    public ParsedExpression parse(String expression) {
        int start = expression.lastIndexOf('{');
        int end = expression.lastIndexOf('}');
        if (start >= 0 && end > start) {
            String base = expression.substring(0, start).trim();
            if (base.isEmpty()) base = expression;
            String constraintStr = expression.substring(start + 1, end).trim();
            Constraint c = parseConstraint(constraintStr);
            return parseBaseExpression(base, c);
        }
        return parseBaseExpression(expression, null);
    }

    public FunctionDefinition parseFunctionDefinition(String expression) {
        String base = expression;
        int start = expression.lastIndexOf('{');
        int end = expression.lastIndexOf('}');
        if (start >= 0 && end > start) {
            base = expression.substring(0, start).trim();
        }

        FunctionDefinition def = tryParseDefinition(base);
        return def;
    }

    private ParsedExpression parseBaseExpression(String base, Constraint constraint) {
        String trimmed = base.trim();
        FunctionDefinition def = tryParseDefinition(trimmed);
        if (def != null) {
            return new ParsedExpression(def.body, constraint, ExprType.FUNCTION, Double.NaN);
        }
        int eq = trimmed.indexOf('=');
        if (eq >= 0) {
            String left = trimmed.substring(0, eq).trim();
            String right = trimmed.substring(eq + 1).trim();
            if (left.isEmpty() || left.equalsIgnoreCase("y")) {
                return new ParsedExpression(right, constraint, ExprType.FUNCTION, Double.NaN);
            }
            if (left.equalsIgnoreCase("x")) {
                Double v = tryParseDouble(right);
                if (v == null) {
                    return new ParsedExpression("", constraint, ExprType.VERTICAL, Double.NaN);
                }
                return new ParsedExpression("", constraint, ExprType.VERTICAL, v);
            }
        }
        return new ParsedExpression(trimmed, constraint, ExprType.FUNCTION, Double.NaN);
    }

    private FunctionDefinition tryParseDefinition(String trimmed) {
        int eq = trimmed.indexOf('=');
        if (eq < 0) return null;

        String left = trimmed.substring(0, eq).trim();
        String right = trimmed.substring(eq + 1).trim();

        int lp = left.indexOf('(');
        int rp = left.lastIndexOf(')');
        if (lp < 0 || rp < lp) return null;

        String name = left.substring(0, lp).trim();
        String arg = left.substring(lp + 1, rp).trim();

        if (name.isEmpty()) return null;
        if (!isValidIdentifier(name)) return null;
        if (!arg.equalsIgnoreCase("x")) return null;

        if (right.isEmpty()) return null;
        return new FunctionDefinition(name, right);
    }

    private boolean isValidIdentifier(String s) {
        if (s.isEmpty()) return false;
        char c0 = s.charAt(0);
        if (!(Character.isLetter(c0) || c0 == '_')) return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) return false;
        }
        return true;
    }

    private Constraint parseConstraint(String constraintStr) {
        if (constraintStr.isEmpty()) return null;
        Constraint c = new Constraint();
        String[] parts = constraintStr.split("&&");
        for (String raw : parts) {
            String s = raw.replace(" ", "");
            if (s.isEmpty()) continue;

            if (s.contains("x")) {
                applyBoundForVar(c, s, true);
            } else if (s.contains("y")) {
                applyBoundForVar(c, s, false);
            }
        }
        return c.isEmpty() ? null : c;
    }

    private void applyBoundForVar(Constraint c, String s, boolean isX) {
        char var = isX ? 'x' : 'y';
        int idx = s.indexOf(var);
        if (idx < 0) return;

        String left = s.substring(0, idx);
        String right = s.substring(idx + 1);

        Bound leftBound = parseLeftBound(left);
        Bound rightBound = parseRightBound(right);

        if (leftBound != null) {
            if (leftBound.op.equals("<")) {
                c.applyMin(leftBound.value, false, isX);
            } else if (leftBound.op.equals("<=")) {
                c.applyMin(leftBound.value, true, isX);
            } else if (leftBound.op.equals(">")) {
                c.applyMax(leftBound.value, false, isX);
            } else if (leftBound.op.equals(">=")) {
                c.applyMax(leftBound.value, true, isX);
            }
        }

        if (rightBound != null) {
            if (rightBound.op.equals("<")) {
                c.applyMax(rightBound.value, false, isX);
            } else if (rightBound.op.equals("<=")) {
                c.applyMax(rightBound.value, true, isX);
            } else if (rightBound.op.equals(">")) {
                c.applyMin(rightBound.value, false, isX);
            } else if (rightBound.op.equals(">=")) {
                c.applyMin(rightBound.value, true, isX);
            }
        }
    }

    private Bound parseLeftBound(String s) {
        if (s.isEmpty()) return null;
        String op;
        if (s.endsWith("<=")) op = "<=";
        else if (s.endsWith(">=")) op = ">=";
        else if (s.endsWith("<")) op = "<";
        else if (s.endsWith(">")) op = ">";
        else return null;

        String num = s.substring(0, s.length() - op.length());
        Double v = tryParseDouble(num);
        return v == null ? null : new Bound(op, v);
    }

    private Bound parseRightBound(String s) {
        if (s.isEmpty()) return null;
        String op;
        if (s.startsWith("<=")) op = "<=";
        else if (s.startsWith(">=")) op = ">=";
        else if (s.startsWith("<")) op = "<";
        else if (s.startsWith(">")) op = ">";
        else return null;

        String num = s.substring(op.length());
        Double v = tryParseDouble(num);
        return v == null ? null : new Bound(op, v);
    }

    private Double tryParseDouble(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static class Bound {
        final String op;
        final double value;

        Bound(String op, double value) {
            this.op = op;
            this.value = value;
        }
    }
}
