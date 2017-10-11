package net.bestia.palantir.map;

import java.util.Objects;

import javafx.scene.canvas.Canvas;

public class TilemapRenderer {

	private static final int TILE_SIZE = 32;

	private CanvasSize canvasSize;
	private MapPosition mapPosition;

	// private TilemapProvider

	public void setMapPosition(MapPosition mapPosition) {
		this.mapPosition = Objects.requireNonNull(mapPosition);
	}

	/**
	 * Renders the current visible spot of the tilemap to the canvas.
	 * 
	 * @param canvas
	 *            The canvas to draw onto.
	 */
	public void render(Canvas canvas) {

		// Extract the current size of the canvas.
		canvasSize.setHeight(canvas.getHeight());
		canvasSize.setWidth(canvas.getWidth());

		final long pxWidth = mapPosition.getMapWidth() * TILE_SIZE;
		final long pxHeight = mapPosition.getMapHeight() * TILE_SIZE;

		double scaleH = canvasSize.getHeight() / pxHeight;
		double scaleW = canvasSize.getWidth() / pxWidth;

		double scale = Math.min(scaleH, scaleW);

		// If the scale is bigger then 1 we need to extend so all tiles are
		// shown.
		if (scale > 1) {
			mapPosition.setMapHeight((long) (canvasSize.getHeight() / scale) + 1);
			mapPosition.setMapWidth((long) (canvasSize.getWidth() / scale) + 1);
			render(canvas);
			return;
		}
		
		// If the scale is so small that tiles are smaller then 1 px we need to skip tiles until at least tiles with 1 px in
		// size are drawn.
		int skip = 0;
		while(scale * TILE_SIZE < 1) {
			
		}
	}

}
