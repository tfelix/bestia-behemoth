import ko from 'knockout';

/**
 * Holds the data to display a shortcut to the user.
 */
export default class Shortcut {
	
	constructor(keycode, key, image, action) {
		
		/**
		 * Image to be displayed for this shortcut.
		 */
		this.img = ko.observable('');

		/**
		 * Text which will be displayed for this shortcut.
		 * 
		 * @property string
		 */
		this.text = ko.observable('');

		/**
		 * The human readable key which contains this shortcut.
		 */
		this.key = ko.observable('');

		/**
		 * Data is send back to the caller if a shortcut action is triggered.
		 */
		this._data = {};

		/**
		 * Reply topic is used to signal a change back to the original caller.
		 */
		this._replyTopic = '';

		/**
		 * The keycode which triggers this shortcut action.
		 * 
		 * @public
		 * @member {number} 
		 */
		this.keyCode = 0;
	}
	
	/**
	 * Triggers the associated action with this shortcut.
	 */
	triggerAction() {
		this._action();
	}
	
}