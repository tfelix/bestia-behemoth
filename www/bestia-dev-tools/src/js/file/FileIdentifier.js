
/**
 * Identifies a sprite file and returns the type of file used.
 * 
 * @export
 * @class FileIdentifier
 */
export default class FileIdentifier {

	constructor() {

	}

	/**
	 * Identifies if the given data object is a sprite description.
	 * @param {any} data 
	 * @returns {boolean} TRUE if the given JSON data is a sprite description.
	 * @memberof FileIdentifier
	 */
	isSpriteDescription(data) {

		return data.hasOwnProperty('name') && data.hasOwnProperty('type') && data.hasOwnProperty('version');

	}

	/**
	 * Identifies if the given data object is a offset file.
	 * 
	 * @param {any} data 
	 * @returns {boolean} TRUE if the given file is a offset file. FALSE otherwise.
	 * @memberof FileIdentifier
	 */
	isOffset(data) {

		return data.hasOwnProperty('targetSprite') && data.hasOwnProperty('defaultCords') && data.hasOwnProperty('offsets');

	}

	/**
	 * Identifies if the given data object is a animation file.
	 * 
	 * @param {any} data 
	 * @returns {boolean} TRUE if the given file is a animation file. FALSE otherwise.
	 * @memberof FileIdentifier
	 */
	isAnimation(data) {

		return data.hasOwnProperty('frames');

	}
};
