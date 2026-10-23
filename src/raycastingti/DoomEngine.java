package raycastingti;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class DoomEngine extends JFrame {
    private GamePanel gamePanel;

    public DoomEngine() {
        setTitle("RayCastingTI - Tech School (IA Local)");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                ProfessorAI.shutdownServer();
                dispose();
                System.exit(0);
            }
        });

        gamePanel = new GamePanel();
        add(gamePanel);
        pack();

        setLocationRelativeTo(null);
        setVisible(true);
        gamePanel.requestFocusInWindow();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(DoomEngine::new);
    }
}
