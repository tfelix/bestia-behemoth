/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * Generates and returns a random GUID. Can be used for message referencing.
 */
export default function guid() {
	return _s4 + _s4 + '-' + _s4 + '-' + _s4 + '-' +
	_s4 + '-' + _s4 + _s4 + _s4;
}


function _s4() {
	return Math.floor((1 + Math.random()) * 0x10000)
    .toString(16)
    .substring(1);
}

