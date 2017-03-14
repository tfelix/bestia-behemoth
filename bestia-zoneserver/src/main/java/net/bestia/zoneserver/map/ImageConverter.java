package net.bestia.zoneserver.map;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Converts 2D arrays into a gray toned image.
 * 
 * @author tbf
 *
 */
public class ImageConverter {

	/**
	 * <p>
	 * The scale can be used to use multiple pixel for one provided datapoint.
	 * E.g. a scale of 2 will use 2*2 pixel for one pixel of data (doubling the
	 * size of the resulting image in every direction).
	 * </p>
	 * <p>
	 * The file beeing written is in PNG format.
	 * </p>
	 * 
	 * @param filename
	 *            The filename to be created.
	 * @param data
	 *            Containing the raw image data. It is expected to have the data
	 *            in [y][x] order for easier iteration.
	 * @param scale
	 *            The scale counts how many picel are used for one pixel of
	 *            privided image data.
	 * @return TRUE if image creation was successful. FALSE otherwise.
	 */
	public boolean writeGrayscaleImage(String filename, byte[][] data, int scale) {

		if (scale < 1) {
			throw new IllegalArgumentException("Scale must be greater or equal 1.");
		}

		if (data.length < 1) {
			throw new IllegalArgumentException("Byte array must contain at least 1 element.");
		}

		final int baseH = data.length;
		final int baseW = data[0].length;

		for (byte[] b : data) {
			if (b.length != baseW) {
				throw new IllegalArgumentException("Byte array has no constant width.");
			}
		}

		final BufferedImage img = new BufferedImage(baseW * scale, baseH * scale, BufferedImage.TYPE_BYTE_GRAY);
		final WritableRaster raster = img.getRaster();

		// Render the image.
		for (int y = 0; y < baseH; y++) {
			for (int x = 0; x < baseW; x++) {

				final byte gray = data[y][x];
				final int rgb = getRgb(gray);

				for (int sx = 0; sx < scale; sx++) {
					for (int sy = 0; sy < scale; sy++) {

						raster.setSample(x * scale + sx, y * scale + sy, 0, rgb);

					}
				}
			}
		}

		final File f = new File(filename);

		try {
			ImageIO.write(img, "PNG", f);
		} catch (IOException e) {

			return false;
		}

		return true;
	}

	private int getRgb(byte b) {
		final int rgb = b & 0xFF;
		return rgb;
	}

	
	/*
	 * Scale image:
	 * Image scaled = sourceImage.getScaledInstance(-1, height, Image.SCALE_SMOOTH);
  BufferedImage bufferedScaled = new BufferedImage(scaled.getWidth(null),  scaled.getHeight(null), BufferedImage.TYPE_INT_RGB);
  bufferedScaled.getGraphics().drawImage(scaled, 0, 0, null);
  */
}
