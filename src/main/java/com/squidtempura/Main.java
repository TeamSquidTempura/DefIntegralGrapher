package com.squidtempura;

import javax.swing.*;
import java.awt.*;

public class Main {

    public static void main(String[] args) {
        JFrame frame = new JFrame("Integrax");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        GraphPanel graphPanel = new GraphPanel();
        FunctionListPanel functionPanel = new FunctionListPanel(graphPanel);
        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                functionPanel,
                graphPanel
        );
        split.setDividerLocation(260);
        frame.add(split);
        frame.setLocationRelativeTo(null);

        DisplayMode display = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDisplayMode();
        frame.setSize(display.getWidth() / 2, display.getHeight() / 2);

        attachDisplayAwareResizer(frame, split);
        frame.setVisible(true);
    }

    private static void attachDisplayAwareResizer(JFrame frame, JSplitPane split) {
        final GraphicsDevice[] currentDevice = new GraphicsDevice[1];
        currentDevice[0] = frame.getGraphicsConfiguration().getDevice();

        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) {
                GraphicsDevice device = frame.getGraphicsConfiguration().getDevice();
                if (device == currentDevice[0]) return;
                currentDevice[0] = device;

                DisplayMode mode = device.getDisplayMode();
                frame.setSize(mode.getWidth() / 2, mode.getHeight() / 2);
                split.setDividerLocation(260);
                frame.revalidate();
                frame.repaint();
            }
        });
    }
}
