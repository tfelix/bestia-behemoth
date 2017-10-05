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