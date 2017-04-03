
/**
 * Holds the data for the shortcut.
 */
export default class Shortcut {
	
	constructor(keycode, key, image, action) {
		
		this.keycode = keycode;
		this._action = action;
		
		this.key = ko.observable(key);
		
		this.imageSrc = ko.observable(image);
		this.imageAlt = ko.observable('');

		/**
		 * @member {boolean} Is TRUE if the shortcut is currently triggered via a key 
		 * press or push event.
		 */
		this.isTriggered = ko.observable(false);
		
	}	
}