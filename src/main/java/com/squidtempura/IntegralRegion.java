package com.squidtempura;

import java.awt.Shape;

public class IntegralRegion {
    public final IntegralSpec spec;
    public final Shape screenShape;

    public IntegralRegion(IntegralSpec spec, Shape screenShape) {
        this.spec = spec;
        this.screenShape = screenShape;
    }
}
