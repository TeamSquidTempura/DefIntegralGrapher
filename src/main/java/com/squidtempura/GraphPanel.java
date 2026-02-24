package com.squidtempura;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphPanel extends JPanel {

    private static final double MIN_SCALE = 0.2;
    private static final double MAX_SCALE = 200000.0;
    private static final double ZOOM_BASE = 1.08;

    private double scale = 50;
    private double offsetX = 0;
    private double offsetY = 0;

    private double mouseMathX = 0;
    private double mouseMathY = 0;

    private Point lastMouse;
    private Point pressPoint;
    private boolean dragging = false;

    private double clickedX = Double.NaN;
    private double clickedY = Double.NaN;
    private boolean labelVisible = false;
    private List<Point2D> selectedIntersections = new ArrayList<>();
    private List<Point2D> intersections = new ArrayList<>();
    private final Map<String, ParsedExpression> parsedCache = new HashMap<>();
    private final ExpressionParser expressionParser = new ExpressionParser();
    private final IntegralParser integralParser = new IntegralParser();
    private final List<IntegralRegion> integralRegions = new ArrayList<>();
    private final Map<String, IntegralLabel> integralLabels = new HashMap<>();

    private FunctionEvaluator evaluator = new FunctionEvaluator();
    private List<String> expressions = new ArrayList<String>();

    public GraphPanel() {
        setFocusable(true);
        setupKeyBindings();

        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                lastMouse = e.getPoint();
                pressPoint = e.getPoint();
                dragging = false;
            }

            public void mouseReleased(MouseEvent e) {
                if (pressPoint == null) return;
                Point p = e.getPoint();
                if (!dragging && pressPoint.distance(p) <= 10.0) {
                    handlePointerTap(p);
                }
                pressPoint = null;
                dragging = false;
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {

                Point p = e.getPoint();
                if (!dragging) {
                    if (pressPoint != null && pressPoint.distance(p) <= 10.0) {
                        return;
                    }
                    dragging = true;
                    lastMouse = p;
                    return;
                }

                double dx = (p.x - lastMouse.x) / scale;
                double dy = (p.y - lastMouse.y) / scale;

                offsetX += dx;
                offsetY -= dy;

                lastMouse = p;
                repaint();
            }

            public void mouseMoved(MouseEvent e) {
                updateMouseMath(e.getPoint());
                repaint();
            }
        });

        addMouseWheelListener(e -> {
            double precise = e.getPreciseWheelRotation();
            if (precise == 0.0) return;
            double oldScale = scale;
            double zoomFactor = Math.pow(ZOOM_BASE, -precise);

            scale = clamp(scale * zoomFactor, MIN_SCALE, MAX_SCALE);
            if (scale == oldScale) return;

            Point p = e.getPoint();

            double mx = (p.x - getWidth()/2.0) / oldScale - offsetX;
            double my = -(p.y - getHeight()/2.0) / oldScale - offsetY;

            offsetX = (p.x - getWidth()/2.0) / scale - mx;
            offsetY = -(p.y - getHeight()/2.0) / scale - my;

            repaint();
        });

    }

    private void handlePointerTap(Point p) {
        if (handleIntersectionClick(p)) {
            repaint();
            return;
        }
        if (handleIntegralAreaClick(p)) {
            repaint();
            return;
        }
        Point2D hit = findNearestCurvePoint(p);
        if (hit != null) {
            if (isSamePoint(hit)) {
                labelVisible = !labelVisible;
            } else {
                clickedX = hit.getX();
                clickedY = hit.getY();
                labelVisible = true;
            }
        } else {
            clickedX = Double.NaN;
            clickedY = Double.NaN;
            labelVisible = false;
        }
        repaint();
    }
    
    private void setupKeyBindings() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearClicked");
        am.put("clearClicked", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clickedX = Double.NaN;
                clickedY = Double.NaN;
                labelVisible = false;
                selectedIntersections.clear();
                integralLabels.clear();
                repaint();
            }
        });
    }

    private void updateMouseMath(Point p) {
        mouseMathX = (p.x - getWidth()/2.0) / scale - offsetX;
        mouseMathY = -(p.y - getHeight()/2.0) / scale - offsetY;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
        );

        AffineTransform baseTransform = g2.getTransform();

        AffineTransform at = new AffineTransform(baseTransform);
        at.translate(getWidth() / 2.0, getHeight() / 2.0);
        at.scale(scale, -scale);
        at.translate(offsetX, offsetY);
        AffineTransform worldToPanel = new AffineTransform();
        worldToPanel.translate(getWidth() / 2.0, getHeight() / 2.0);
        worldToPanel.scale(scale, -scale);
        worldToPanel.translate(offsetX, offsetY);

        g2.setTransform(at);

        drawGrid(g2);
        drawAxes(g2, baseTransform);
        drawIntegralAreas(g2, worldToPanel);
        drawFunctions(g2);
        drawIntersections(g2);
        drawClickedPoint(g2, baseTransform);
        //use original non-flipped axes form for UIs
        g2.setTransform(baseTransform);
        drawMouseCoordinates(g2);
        drawIntersectionLabels(g2, baseTransform);
        drawIntegralLabels(g2, baseTransform);
    }

    private void drawGrid(Graphics2D g2) {

        double left = (-getWidth()/2.0) / scale - offsetX;
        double right = (getWidth()/2.0) / scale - offsetX;
        double bottom = (-getHeight()/2.0) / scale - offsetY;
        double top = (getHeight()/2.0) / scale - offsetY;

        double majorSpacing = getNiceGridSpacing();
        double minorSpacing = majorSpacing / 5.0;

        g2.setStroke(new BasicStroke((float)(1.3 / scale)));

        g2.setColor(new Color(210, 210, 210));

        double startXMinor = Math.floor(left / minorSpacing) * minorSpacing;
        for (double x = startXMinor; x <= right; x += minorSpacing) {
            g2.draw(new Line2D.Double(x, bottom, x, top));
        }

        double startYMinor = Math.floor(bottom / minorSpacing) * minorSpacing;
        for (double y = startYMinor; y <= top; y += minorSpacing) {
            g2.draw(new Line2D.Double(left, y, right, y));
        }

        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke((float)(1.5 / scale)));

        double startXMajor = Math.floor(left / majorSpacing) * majorSpacing;
        for (double x = startXMajor; x <= right; x += majorSpacing) {
            g2.draw(new Line2D.Double(x, bottom, x, top));
        }

        double startYMajor = Math.floor(bottom / majorSpacing) * majorSpacing;
        for (double y = startYMajor; y <= top; y += majorSpacing) {
            g2.draw(new Line2D.Double(left, y, right, y));
        }
    }

    private double getNiceGridSpacing() {

        double targetPixels = 100;
        double rawSpacing = targetPixels / scale;

        double exponent = Math.floor(Math.log10(rawSpacing));
        double base = Math.pow(10, exponent);

        double fraction = rawSpacing / base;

        double optimizedRatio;

        if (fraction < 1.5)      optimizedRatio = 1;
        else if (fraction < 3)   optimizedRatio = 2;
        else if (fraction < 7)   optimizedRatio = 5;
        else                     optimizedRatio = 10;

        return optimizedRatio * base;
    }

    private void drawAxes(Graphics2D g2, AffineTransform baseTransform) {

        double left = (-getWidth()/2.0) / scale - offsetX;
        double right = (getWidth()/2.0) / scale - offsetX;
        double bottom = (-getHeight()/2.0) / scale - offsetY;
        double top = (getHeight()/2.0) / scale - offsetY;

        double spacing = getNiceGridSpacing();

        AffineTransform saved = g2.getTransform();

        g2.setTransform(baseTransform);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        int xAxisY = (int) ((-offsetY) * scale + getHeight() / 2.0);
        int yAxisX = (int) ((offsetX) * scale + getWidth() / 2.0);

        g2.drawLine(0, xAxisY, getWidth(), xAxisY);
        g2.drawLine(yAxisX, 0, yAxisX, getHeight());

        // X axis labels
        double startX = Math.floor(left / spacing) * spacing;

        for (double x = startX; x <= right; x += spacing) {

            if (Math.abs(x) < 1e-10) continue;

            int sx = (int) ((x + offsetX) * scale + getWidth() / 2.0);
            int sy = xAxisY;

            g2.drawLine(sx, sy-4, sx, sy+4);

            g2.drawString(formatNumber(x), sx+3, sy+15);
        }

        // Y axis labels
        double startY = Math.floor(bottom / spacing) * spacing;

        for (double y = startY; y <= top; y += spacing) {

            if (Math.abs(y) < 1e-10) continue;

            int sx = yAxisX;
            int sy = (int) ((-y - offsetY) * scale + getHeight() / 2.0);

            g2.drawLine(sx-4, sy, sx+4, sy);

            g2.drawString(formatNumber(y), sx+6, sy-3);
        }

        g2.setTransform(saved);
    }

    private String formatNumber(double number) {
        double abs = Math.abs(number);

        if (abs >= 1) return String.format("%.2f", number);
        if (abs >= 0.01) return String.format("%.3f", number);
        return String.format("%.2f", number);
    }

    private void drawMouseCoordinates(Graphics2D g2) {
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Consolas", Font.PLAIN, 14));

        String text = String.format("(%.4f , %.4f)", mouseMathX, mouseMathY);

        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        int x = getWidth() - textWidth - 10;
        int y = getHeight() - textHeight - 10;

        g2.drawString(text, x, y);
    }

    private void drawFunctions(Graphics2D g2) {

        Color[] colors = {
                Color.BLUE,
                Color.RED,
                Color.GREEN,
                Color.MAGENTA,
                Color.ORANGE
        };
        g2.setStroke(new BasicStroke((float)(1.6 / scale)));

        double left = (-getWidth()/2.0) / scale - offsetX;
        double right = (getWidth()/2.0) / scale - offsetX;
        double bottom = (-getHeight()/2.0) / scale - offsetY;
        double top = (getHeight()/2.0) / scale - offsetY;

        double step = (right - left) / getWidth();

        int index = 0;

        for (String expression : expressions) {
            ParsedExpression parsed = getParsed(expression);
            g2.setColor(colors[index % colors.length]);

            if (parsed.type == ExprType.VERTICAL) {
                drawVerticalLine(g2, parsed, left, right, bottom, top);
                index++;
                continue;
            }

            Path2D path = new Path2D.Double();
            boolean first = true;

            for (double x = left; x <= right; x += step) {
                double y = evaluateParsedFunction(parsed, x);

                if (Double.isNaN(y) || Double.isInfinite(y)) {
                    first = true;
                    continue;
                }

                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }

            g2.draw(path);
            index++;
        }
    }

    private void drawIntegralAreas(Graphics2D g2, AffineTransform worldToPanel) {
        integralRegions.clear();
        if (expressions.isEmpty()) return;

        double left = (-getWidth() / 2.0) / scale - offsetX;
        double right = (getWidth() / 2.0) / scale - offsetX;

        Composite savedComposite = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));

        Color[] fills = {
                new Color(80, 120, 220),
                new Color(220, 120, 80),
                new Color(80, 180, 120),
                new Color(200, 120, 200)
        };
        int colorIdx = 0;

        for (String expression : expressions) {
            ParsedExpression parsed = getParsed(expression);
            String base = parsed.baseExpression;
            List<IntegralSpec> specs = integralParser.extractIntegrals(base);
            for (IntegralSpec spec : specs) {
                Path2D area = buildIntegralArea(spec, left, right);
                if (area == null) continue;

                g2.setColor(fills[colorIdx % fills.length]);
                g2.fill(area);
                Shape screenShape = worldToPanel.createTransformedShape(area);
                integralRegions.add(new IntegralRegion(spec, screenShape));
                colorIdx++;
            }
        }

        g2.setComposite(savedComposite);
    }

    private void drawIntersections(Graphics2D g2) {
        intersections = computeIntersections();
        if (intersections.isEmpty()) return;

        g2.setColor(new Color(20, 20, 20));
        double r = 3.5 / scale;
        for (Point2D p : intersections) {
            g2.fill(new Ellipse2D.Double(p.getX() - r, p.getY() - r, r * 2, r * 2));
        }
    }

    private void drawIntersectionLabels(Graphics2D g2, AffineTransform baseTransform) {
        if (selectedIntersections.isEmpty()) return;

        AffineTransform saved = g2.getTransform();
        g2.setTransform(baseTransform);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        for (Point2D p : selectedIntersections) {
            int sx = (int) ((p.getX() + offsetX) * scale + getWidth() / 2f);
            int sy = (int) ((-p.getY() - offsetY) * scale + getHeight() / 2f);

            if (sx < -50 || sx > getWidth() + 50 || sy < -50 || sy > getHeight() + 50) {
                continue;
            }

            String text = String.format("(%.4f , %.4f)", p.getX(), p.getY());
            g2.drawString(text, sx + 10, sy - 10);
        }

        g2.setTransform(saved);
    }

    private void drawIntegralLabels(Graphics2D g2, AffineTransform baseTransform) {
        if (integralLabels.isEmpty()) return;

        AffineTransform saved = g2.getTransform();
        g2.setTransform(baseTransform);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Consolas", Font.PLAIN, 12));

        for (IntegralLabel label : integralLabels.values()) {
            int sx = (int) ((label.worldX + offsetX) * scale + getWidth() / 2f);
            int sy = (int) ((-label.worldY - offsetY) * scale + getHeight() / 2f);
            String text = String.format("Area ≈ %.6f", label.area);
            g2.drawString(text, sx + 10, sy - 10);
        }

        g2.setTransform(saved);
    }

    private void drawClickedPoint(Graphics2D g2, AffineTransform baseTransform) {
        if (Double.isNaN(clickedX)) return;

        g2.setColor(Color.RED);
        double r = 4 / scale;
        g2.fill(new Ellipse2D.Double(clickedX - r, clickedY - r, r * 2, r * 2));

        if (labelVisible) {
            AffineTransform saved = g2.getTransform();
            g2.setTransform(baseTransform);
            int sx = (int) ((clickedX + offsetX) * scale + getWidth() / 2f);
            int sy = (int) ((-clickedY - offsetY) * scale + getHeight() / 2f);

            String text = String.format("(%.4f , %.4f)", clickedX, clickedY);
            g2.drawString(text, sx + 10, sy - 10);
            g2.setTransform(saved);
        }
    }

    public void setExpression(List<String> exprs) {
        expressions = exprs;
        parsedCache.clear();
        updateFunctionDefinitions();
        integralLabels.clear();
        repaint();
    }

    private Point2D findNearestCurvePoint(Point p) {
        if (expressions.isEmpty()) return null;

        double x = (p.x - getWidth() / 2.0) / scale - offsetX;
        double bestDist = Double.POSITIVE_INFINITY;
        Point2D best = null;

        for (String expression : expressions) {
            ParsedExpression parsed = getParsed(expression);
            if (parsed.type == ExprType.VERTICAL) {
                double sx = (parsed.xConst + offsetX) * scale + getWidth() / 2.0;
                double dx = sx - p.x;
                double dist = Math.abs(dx);
                if (dist <= 8.0 && (parsed.constraint == null || parsed.constraint.allowsX(parsed.xConst))) {
                    double my = -(p.y - getHeight() / 2.0) / scale - offsetY;
                    double y = clampYToConstraint(parsed, my);
                    if (!Double.isNaN(y)) {
                        return new Point2D.Double(parsed.xConst, y);
                    }
                }
                continue;
            }

            double y = evaluateParsedFunction(parsed, x);
            if (Double.isNaN(y) || Double.isInfinite(y)) continue;

            double sx = (x + offsetX) * scale + getWidth() / 2.0;
            double sy = (-y - offsetY) * scale + getHeight() / 2.0;

            double dx = sx - p.x;
            double dy = sy - p.y;
            double dist = Math.hypot(dx, dy);

            if (dist < bestDist) {
                bestDist = dist;
                best = new Point2D.Double(x, y);
            }
        }

        double hitThresholdPx = 8.0;
        return bestDist <= hitThresholdPx ? best : null;
    }

    private boolean isSamePoint(Point2D p) {
        if (Double.isNaN(clickedX)) return false;
        double sx1 = (clickedX + offsetX) * scale + getWidth() / 2.0;
        double sy1 = (-clickedY - offsetY) * scale + getHeight() / 2.0;
        double sx2 = (p.getX() + offsetX) * scale + getWidth() / 2.0;
        double sy2 = (-p.getY() - offsetY) * scale + getHeight() / 2.0;
        double dist = Math.hypot(sx1 - sx2, sy1 - sy2);
        return dist <= 6.0;
    }

    private boolean handleIntersectionClick(Point p) {
        if (intersections.isEmpty()) return false;

        Point2D nearest = null;
        double best = Double.POSITIVE_INFINITY;
        for (Point2D ip : intersections) {
            double sx = (ip.getX() + offsetX) * scale + getWidth() / 2.0;
            double sy = (-ip.getY() - offsetY) * scale + getHeight() / 2.0;
            double dist = Math.hypot(sx - p.x, sy - p.y);
            if (dist < best) {
                best = dist;
                nearest = ip;
            }
        }

        double hitThresholdPx = 8.0;
        if (nearest == null || best > hitThresholdPx) return false;

        int idx = indexOfSelectedIntersection(nearest);
        if (idx >= 0) {
            selectedIntersections.remove(idx);
        } else {
            selectedIntersections.add(nearest);
        }
        return true;
    }

    private boolean handleIntegralAreaClick(Point p) {
        if (integralRegions.isEmpty()) return false;

        for (IntegralRegion region : integralRegions) {
            if (!region.screenShape.contains(p)) continue;

            String key = region.spec.key();
            if (integralLabels.containsKey(key)) {
                integralLabels.remove(key);
            } else {
                double wx = (p.x - getWidth() / 2.0) / scale - offsetX;
                double wy = -(p.y - getHeight() / 2.0) / scale - offsetY;
                double area = evaluator.evaluate(region.spec.toExpression(), 0.0);
                integralLabels.put(key, new IntegralLabel(key, wx, wy, area));
            }
            return true;
        }

        return false;
    }

    private int indexOfSelectedIntersection(Point2D p) {
        double sx2 = (p.getX() + offsetX) * scale + getWidth() / 2.0;
        double sy2 = (-p.getY() - offsetY) * scale + getHeight() / 2.0;
        for (int i = 0; i < selectedIntersections.size(); i++) {
            Point2D q = selectedIntersections.get(i);
            double sx1 = (q.getX() + offsetX) * scale + getWidth() / 2.0;
            double sy1 = (-q.getY() - offsetY) * scale + getHeight() / 2.0;
            if (Math.hypot(sx1 - sx2, sy1 - sy2) <= 6.0) return i;
        }
        return -1;
    }

    private List<Point2D> computeIntersections() {
        List<Point2D> result = new ArrayList<>();
        if (expressions.size() < 2 || getWidth() <= 2) return result;

        double left = (-getWidth() / 2.0) / scale - offsetX;
        double right = (getWidth() / 2.0) / scale - offsetX;
        double step = (right - left) / getWidth();

        int n = expressions.size();
        for (int i = 0; i < n; i++) {
            ParsedExpression pi = getParsed(expressions.get(i));
            // Axis intercepts for each function/line
            addAxisIntercepts(pi, left, right, step, result);
            for (int j = i + 1; j < n; j++) {
                ParsedExpression pj = getParsed(expressions.get(j));

                if (pi.type == ExprType.VERTICAL && pj.type == ExprType.VERTICAL) {
                    continue;
                }

                if (pi.type == ExprType.VERTICAL || pj.type == ExprType.VERTICAL) {
                    ParsedExpression v = pi.type == ExprType.VERTICAL ? pi : pj;
                    ParsedExpression f = pi.type == ExprType.VERTICAL ? pj : pi;
                    Point2D ip = intersectVerticalWithFunction(v, f, left, right);
                    if (ip != null && !containsNear(result, ip)) result.add(ip);
                    continue;
                }

                double prevX = left;
                double prevF = evaluateParsedFunction(pi, prevX);
                double prevG = evaluateParsedFunction(pj, prevX);
                if (!isValid(prevF) || !isValid(prevG)) {
                    prevF = Double.NaN;
                    prevG = Double.NaN;
                }

                for (double x = left + step; x <= right; x += step) {
                    double y1 = evaluateParsedFunction(pi, x);
                    double y2 = evaluateParsedFunction(pj, x);
                    if (!isValid(y1) || !isValid(y2) || Double.isNaN(prevF) || Double.isNaN(prevG)) {
                        prevX = x;
                        prevF = y1;
                        prevG = y2;
                        continue;
                    }

                    double d0 = prevF - prevG;
                    double d1 = y1 - y2;

                    if (d0 == 0.0 || d0 * d1 < 0.0) {
                        Point2D ip = refineIntersection(pi, pj, prevX, x);
                        if (ip != null && !containsNear(result, ip)) {
                            result.add(ip);
                        }
                    }

                    prevX = x;
                    prevF = y1;
                    prevG = y2;
                }
            }
        }
        return result;
    }

    private Point2D refineIntersection(ParsedExpression f, ParsedExpression g, double a, double b) {
        double fa = evaluateParsedFunction(f, a) - evaluateParsedFunction(g, a);
        double fb = evaluateParsedFunction(f, b) - evaluateParsedFunction(g, b);
        if (!isValid(fa) || !isValid(fb)) return null;

        double left = a;
        double right = b;
        for (int k = 0; k < 18; k++) {
            double mid = (left + right) / 2.0;
            double fm = evaluateParsedFunction(f, mid) - evaluateParsedFunction(g, mid);
            if (!isValid(fm)) return null;

            if (fa == 0.0) {
                left = a;
                right = a;
                break;
            }
            if (fa * fm <= 0) {
                right = mid;
                fb = fm;
            } else {
                left = mid;
                fa = fm;
            }
        }

        double x = (left + right) / 2.0;
        double y = evaluateParsedFunction(f, x);
        if (!isValid(y)) return null;
        return new Point2D.Double(x, y);
    }

    private void addAxisIntercepts(ParsedExpression parsed, double left, double right, double step, List<Point2D> out) {
        if (parsed.type == ExprType.VERTICAL) {
            if (parsed.constraint != null && !parsed.constraint.allowsX(parsed.xConst)) return;
            if (parsed.xConst >= left && parsed.xConst <= right) {
                double y0 = clampYToConstraint(parsed, 0.0);
                if (!Double.isNaN(y0)) {
                    Point2D p = new Point2D.Double(parsed.xConst, y0);
                    if (!containsNear(out, p)) out.add(p);
                }
            }
            return;
        }

        // y-intercept at x = 0 if visible
        if (left <= 0 && right >= 0) {
            double y0 = evaluateParsedFunction(parsed, 0);
            if (isValid(y0)) {
                Point2D p = new Point2D.Double(0, y0);
                if (!containsNear(out, p)) out.add(p);
            }
        }

        // x-intercepts where f(x) == 0
        double prevX = left;
        double prevY = evaluateParsedFunction(parsed, prevX);
        if (!isValid(prevY)) prevY = Double.NaN;

        for (double x = left + step; x <= right; x += step) {
            double y = evaluateParsedFunction(parsed, x);
            if (!isValid(y) || Double.isNaN(prevY)) {
                prevX = x;
                prevY = y;
                continue;
            }

            if (prevY == 0.0 || prevY * y < 0.0) {
                Point2D root = refineRoot(parsed, prevX, x);
                if (root != null && !containsNear(out, root)) out.add(root);
            }

            prevX = x;
            prevY = y;
        }
    }

    private Point2D refineRoot(ParsedExpression parsed, double a, double b) {
        double fa = evaluateParsedFunction(parsed, a);
        double fb = evaluateParsedFunction(parsed, b);
        if (!isValid(fa) || !isValid(fb)) return null;

        double left = a;
        double right = b;
        for (int k = 0; k < 18; k++) {
            double mid = (left + right) / 2.0;
            double fm = evaluateParsedFunction(parsed, mid);
            if (!isValid(fm)) return null;

            if (fa == 0.0) {
                left = a;
                right = a;
                break;
            }
            if (fa * fm <= 0) {
                right = mid;
                fb = fm;
            } else {
                left = mid;
                fa = fm;
            }
        }

        double x = (left + right) / 2.0;
        return new Point2D.Double(x, 0.0);
    }

    private boolean isValid(double v) {
        return !(Double.isNaN(v) || Double.isInfinite(v));
    }

    private boolean containsNear(List<Point2D> pts, Point2D p) {
        double sx = (p.getX() + offsetX) * scale + getWidth() / 2.0;
        double sy = (-p.getY() - offsetY) * scale + getHeight() / 2.0;
        for (Point2D q : pts) {
            double qx = (q.getX() + offsetX) * scale + getWidth() / 2.0;
            double qy = (-q.getY() - offsetY) * scale + getHeight() / 2.0;
            if (Math.hypot(sx - qx, sy - qy) <= 6.0) return true;
        }
        return false;
    }

    private Path2D buildIntegralArea(IntegralSpec spec, double left, double right) {
        double a = evaluator.evaluate(spec.aExpr, 0.0);
        double b = evaluator.evaluate(spec.bExpr, 0.0);
        if (Double.isNaN(a) || Double.isNaN(b)) return null;
        if (a == b) return null;

        double start = Math.min(a, b);
        double end = Math.max(a, b);
        if (end < left || start > right) return null;

        start = Math.max(start, left);
        end = Math.min(end, right);

        int samples = Math.max(2, getWidth());
        double step = (end - start) / samples;
        if (step <= 0) return null;

        Path2D area = new Path2D.Double();
        boolean segment = false;
        double lastX = start;

        for (int i = 0; i <= samples; i++) {
            double x = start + i * step;
            double y = evaluator.evaluate(spec.integrandExpr, x);
            if (Double.isNaN(y) || Double.isInfinite(y)) {
                if (segment) {
                    area.lineTo(lastX, 0);
                    area.closePath();
                    segment = false;
                }
                continue;
            }

            if (!segment) {
                area.moveTo(x, 0);
                area.lineTo(x, y);
                segment = true;
            } else {
                area.lineTo(x, y);
            }
            lastX = x;
        }

        if (segment) {
            area.lineTo(end, 0);
            area.closePath();
        }

        return area;
    }

    private void drawVerticalLine(Graphics2D g2, ParsedExpression parsed, double left, double right, double bottom, double top) {
        if (Double.isNaN(parsed.xConst)) return;
        if (parsed.constraint != null && !parsed.constraint.allowsX(parsed.xConst)) return;
        if (parsed.xConst < left || parsed.xConst > right) return;

        double yMin = bottom;
        double yMax = top;
        if (parsed.constraint != null) {
            if (parsed.constraint.yMin != null) {
                yMin = Math.max(yMin, parsed.constraint.yMin);
                if (!parsed.constraint.yMinInc) yMin = Math.nextUp(yMin);
            }
            if (parsed.constraint.yMax != null) {
                yMax = Math.min(yMax, parsed.constraint.yMax);
                if (!parsed.constraint.yMaxInc) yMax = Math.nextDown(yMax);
            }
        }

        if (yMax < yMin) return;
        g2.draw(new Line2D.Double(parsed.xConst, yMin, parsed.xConst, yMax));
    }

    private double clampYToConstraint(ParsedExpression parsed, double y) {
        if (parsed.constraint == null) return y;
        if (parsed.constraint.yMin != null) {
            double min = parsed.constraint.yMin;
            if (y < min || (y == min && !parsed.constraint.yMinInc)) {
                y = min;
            }
        }
        if (parsed.constraint.yMax != null) {
            double max = parsed.constraint.yMax;
            if (y > max || (y == max && !parsed.constraint.yMaxInc)) {
                y = max;
            }
        }
        return y;
    }

    private Point2D intersectVerticalWithFunction(ParsedExpression v, ParsedExpression f, double left, double right) {
        if (Double.isNaN(v.xConst)) return null;
        if (v.xConst < left || v.xConst > right) return null;
        if (v.constraint != null && !v.constraint.allowsX(v.xConst)) return null;

        double y = evaluateParsedFunction(f, v.xConst);
        if (!isValid(y)) return null;
        if (v.constraint != null && !v.constraint.allowsY(y)) return null;
        return new Point2D.Double(v.xConst, y);
    }

    private double evaluateParsedFunction(ParsedExpression parsed, double x) {
        if (parsed.type != ExprType.FUNCTION) return Double.NaN;
        if (parsed.constraint != null && !parsed.constraint.allowsX(x)) {
            return Double.NaN;
        }

        double y = evaluator.evaluate(parsed.baseExpression, x);
        if (!isValid(y)) return y;

        if (parsed.constraint != null && !parsed.constraint.allowsY(y)) {
            return Double.NaN;
        }

        return y;
    }

    private ParsedExpression getParsed(String expression) {
        ParsedExpression parsed = parsedCache.get(expression);
        if (parsed == null) {
            parsed = expressionParser.parse(expression);
            parsedCache.put(expression, parsed);
        }
        return parsed;
    }

    private void updateFunctionDefinitions() {
        Map<String, String> defs = new HashMap<>();
        for (String expression : expressions) {
            FunctionDefinition def = expressionParser.parseFunctionDefinition(expression);
            if (def != null) {
                defs.put(def.name, def.body);
            }
        }
        evaluator.setFunctions(defs);
    }
}
