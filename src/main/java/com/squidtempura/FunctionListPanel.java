package com.squidtempura;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FunctionListPanel extends JPanel {

    private GraphPanel graphPanel;
    private JPanel listPanel;
    private List<JTextField> fields = new ArrayList<>();

    public FunctionListPanel(GraphPanel graphPanel) {
        this.graphPanel = graphPanel;
        setLayout(new BorderLayout());

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listPanel);
        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addFunctionField(""));
        add(scroll, BorderLayout.CENTER);
        add(addButton, BorderLayout.SOUTH);
        addFunctionField("x^2");
    }

    private void addFunctionField(String text) {
        JTextField field = new JTextField(text);
        fields.add(field);

        field.addActionListener(e -> updateFunctions());
        listPanel.add(field);

        revalidate();
        repaint();

        updateFunctions();
    }

    private void updateFunctions() {
        List<String> exprs = new ArrayList<>();
        for (JTextField field : fields) {
            String text = field.getText().trim();
            if (!text.isEmpty()) {
                exprs.add(text);
            }
            graphPanel.setExpression(exprs);
        }
    }
}
