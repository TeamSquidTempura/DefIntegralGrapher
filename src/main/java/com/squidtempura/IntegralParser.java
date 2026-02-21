package com.squidtempura;

import java.util.ArrayList;
import java.util.List;

public class IntegralParser {
    public List<IntegralSpec> extractIntegrals(String expr) {
        List<IntegralSpec> result = new ArrayList<>();
        int idx = 0;
        while (idx < expr.length()) {
            int start = expr.indexOf("int(", idx);
            if (start < 0) break;
            ParsedIntegral p = parseAt(expr, start);
            if (p != null) {
                result.add(new IntegralSpec(p.integrand, p.aExpr, p.bExpr));
                idx = p.endIndex + 1;
            } else {
                idx = start + 4;
            }
        }
        return result;
    }

    public boolean isSingleIntegral(String expr) {
        String trimmed = expr.trim();
        if (!trimmed.startsWith("int(")) return false;
        ParsedIntegral p = parseAt(trimmed, 0);
        return p != null && p.endIndex == trimmed.length() - 1;
    }

    private ParsedIntegral parseAt(String expr, int intIndex) {
        if (!expr.startsWith("int(", intIndex)) return null;
        int start = intIndex + 4;
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
        p.integrand = parts[0].trim();
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

    private static class ParsedIntegral {
        String integrand;
        String aExpr;
        String bExpr;
        int endIndex;
    }
}
