package com.squidtempura;

public class Constraint {
    Double xMin;
    Double xMax;
    Double yMin;
    Double yMax;
    boolean xMinInc;
    boolean xMaxInc;
    boolean yMinInc;
    boolean yMaxInc;

    boolean isEmpty() {
        return xMin == null && xMax == null && yMin == null && yMax == null;
    }

    void applyMin(double v, boolean inc, boolean isX) {
        if (isX) {
            if (xMin == null || v > xMin || (v == xMin && inc)) {
                xMin = v;
                xMinInc = inc;
            }
        } else {
            if (yMin == null || v > yMin || (v == yMin && inc)) {
                yMin = v;
                yMinInc = inc;
            }
        }
    }

    void applyMax(double v, boolean inc, boolean isX) {
        if (isX) {
            if (xMax == null || v < xMax || (v == xMax && inc)) {
                xMax = v;
                xMaxInc = inc;
            }
        } else {
            if (yMax == null || v < yMax || (v == yMax && inc)) {
                yMax = v;
                yMaxInc = inc;
            }
        }
    }

    boolean allowsX(double x) {
        if (xMin != null) {
            if (x < xMin || (x == xMin && !xMinInc)) return false;
        }
        if (xMax != null) {
            if (x > xMax || (x == xMax && !xMaxInc)) return false;
        }
        return true;
    }

    boolean allowsY(double y) {
        if (yMin != null) {
            if (y < yMin || (y == yMin && !yMinInc)) return false;
        }
        if (yMax != null) {
            if (y > yMax || (y == yMax && !yMaxInc)) return false;
        }
        return true;
    }
}
