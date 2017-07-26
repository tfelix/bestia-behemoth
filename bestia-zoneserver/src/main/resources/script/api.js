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
	var Point = Java.type('net.bestia.model.geometry.Point');
	return new Point(x, y);
}