# Integrax User Guide

Integrax is a 2D graphing calculator for plotting functions, inspecting values, and exploring intersections and integrals.

## Quick Start

1. Run the app.
2. Type expressions in the left panel.
3. Press Enter in a field to update the graph.

## Core Features

- Plot functions of `x`: `x^2`, `sin(x)`, `x^3 - 4x`
- Multiple functions at once (each entry is a separate expression)
- Zoom with mouse wheel (zoom centers on cursor)
- Pan by dragging the graph
- Mouse coordinate readout at the bottom right
- Click near a curve to snap and label a point (click again to toggle label)
- Intersections between functions are marked with dots
- Click intersection dots to toggle labels (multiple labels supported)
- Axis intercepts (x- and y-intercepts) are marked as dots

## Expression Syntax

### Functions

Use `x` as the variable:

- `x^2`
- `sin(x)`
- `x^3 - 2x + 1`

### Named Functions

Define reusable functions:

- `f(x)=x^2+1`
- `g(x)=f(x)+3`

Then use them in other expressions:

- `f(x)`
- `g(x)`

### Derivatives

Use a prime on a named function:

- `f'(x)`

Derivatives are computed numerically.

### Vertical and Horizontal Lines

- Horizontal line: `y = 3`
- Vertical line: `x = 2`

### Domain and Range Constraints

Add constraints in curly braces:

- `x^2 { -2 < x < 3 }`
- `sin(x) { x <= 6.28 && y >= -0.5 }`

Supported operators: `<`, `<=`, `>`, `>=`  
Use `&&` to combine constraints.

### Definite Integrals

Compute numeric definite integrals:

- `int(x^2+1, 0, 3)`
- `int(f(x), 0, 1)`

Integrals are computed numerically.

## Integral Shading and Area Labels

If an expression contains `int(...)`, Integrax shades the area under the integrand from `a` to `b` (between the curve and the x-axis).

Click a shaded region to toggle an area label at the click position.

## Controls

- **Pan**: click and drag
- **Zoom**: mouse wheel
- **Clear clicked labels**: `Esc`

## Notes

- All numeric results are approximate.
- Invalid expressions are ignored (not plotted).

