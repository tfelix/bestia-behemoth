/**
 * Creates a rectangular collision shape.
 * 
 * @returns
 */
function rect() {

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
function point(x, y) {
	return new net.bestia.model.geometry.Point(x, y);
}