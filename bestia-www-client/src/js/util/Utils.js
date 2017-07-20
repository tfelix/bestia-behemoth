/**
 * @author       Thomas Felix
 * @copyright    2015 Thomas Felix
 */

/**
 * Formats a string with the given arguments. Works similar to javas
 * String.format.
 */
export function strFormat(format) {
	var args = Array.prototype.slice.call(arguments, 1);
	return format.replace(/{(\d+)}/g, function (match, number) {
		return typeof args[number] != 'undefined' ? args[number] : match;
	});
}

/**
 * Checks if a string starts with a certain sub-string.
 * @param {string} str 
 * @param {string} start 
 * @returns {booleans} Returns true if the string starts, false otherwise.
 */
export function strStartsWith(str, start) {
	if(!str) {
		return false;
	}
	return str.slice(0, start.length) == start;
}

/**
 * Calculates the euclidian distance between the two objects. Both must have a x
 * and y property.
 * 
 * @return {float} Distance between the two points.
 */
export function distance(d1, d2) {
	var x = d1.x - d2.x;
	var y = d1.y - d2.y;
	return Math.sqrt(x * x + y * y);
}