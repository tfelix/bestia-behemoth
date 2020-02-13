/**
 * Script file contains helper function which should be globally available to all scripts.
 */

/**
 * Creates a rectangular collision shape.
 * 
 * @param x
 *            The x-position of the rect.
 * @param y
 *            The y-position of the rect.
 * @param width
 *            The width of the rect.
 * @param height
 *            The height of the rect.
 * @returns A net.bestia.model.geometry.Rect object for use with the BAPI.
 */
function rect(x, y, z, width, height, depth) {
	return new Rect(x, y, z, width, height, depth);
}

/**
 * Creates a Point shape object for the bestia API.
 * 
 * @param x
 *            The x-coordinate.
 * @param y
 *            The y-coordinate.
 * @returns The created point.
 */
function vec3(x, y, z) {
	return new Vec3(x, y, z);
}