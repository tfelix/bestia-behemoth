/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

function _s4() {
	return Math.floor((1 + Math.random()) * 0x10000)
    .toString(16)
    .substring(1);
}

/**
 * Generates and returns a random GUID. Can be used for message referencing.
 */
export function guid() {
	return _s4() + _s4() + '-' + _s4() + '-' + _s4() + '-' +
	_s4() + '-' + _s4() + _s4() + _s4();
}