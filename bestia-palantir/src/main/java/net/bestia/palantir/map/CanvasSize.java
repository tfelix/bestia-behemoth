package net.bestia.palantir.map;

/**
 * Controls the displayed proportion of the tilemap data.
 * 
 * @author Thomas Felix
 *
 */
public class CanvasSize {

	private double width;
	private double height;

	public double getWidth() {
		return width;
	}

	public void setWidth(double canvasWidth) {
		this.width = canvasWidth;
	}

	public double getHeight() {
		return height;
	}

	public void setHeight(double canvasHeight) {
		this.height = canvasHeight;
	}

	@Override
	public String toString() {
		return String.format("CanvasPos[w: %.1f, h: %.1f]", width, height);
	}
}
