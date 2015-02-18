package at.jotschi.quadtree.gui;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.log4j.Logger;

@SuppressWarnings("serial")
public class QuadTreePanel extends JPanel implements KeyListener, MouseListener {

	
	protected static Logger log = Logger.getLogger(QuadTreePanel.class);

	/**
	 * Create a new panel
	 */
	protected QuadTreePanel() {

	}

	protected void setupGui() {
		JFrame f = new JFrame();
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.addKeyListener(this);
		f.addMouseListener(this);
		f.add(this);
		f.setLocationRelativeTo(null);
		f.setSize(700, 700);
		f.setVisible(true);
	}

	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub

	}

	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub

	}

	public void keyReleased(KeyEvent e) {

		if (e.getKeyCode() == 27) {
			System.exit(10);
		}

	}

	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub

	}
}
