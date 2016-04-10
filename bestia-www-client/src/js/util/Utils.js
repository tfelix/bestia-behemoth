/**
 * Formats a string with the given arguments. Works similar to javas
 * String.format.
 */
export
function strFormat(format) {
	var args = Array.prototype.slice.call(arguments, 1);
	return format.replace(/{(\d+)}/g, function(match, number) {
		return typeof args[number] != 'undefined' ? args[number] : match;
	});
}

export
function strStartsWith(str, start) {
	return str.slice(0, start.length) == start;
}