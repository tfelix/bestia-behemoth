import ko from 'knockout';

/**
 * Holds the data to display a shortcut to the user.
 */
export default class Shortcut {

	constructor(keycode, key, image, action) {

		this.keycode = keycode;
		this._action = action;

		this.key = ko.observable('A');

		this.imageSrc = ko.observable(image);
		this.imageAlt = ko.observable('http://placehold.it/32x32');

		/**
		 * @member {boolean} Is TRUE if the shortcut is currently triggered via a key 
		 * press or push event. Can be used to change the css files based on triggering state.
		 */
		this.isTriggered = ko.observable(false);

		this.triggerTopic = null;
		this.triggerData = null;
	}

	/**
	 * Checks if the shortcut was triggered via this keycode. 
	 * If it was the saved action is performed.
	 */
	trigger(keycode, pubsub) {

		if(this.keycode !== keycode && this.triggerTopic !== null) {
			return;
		}

		pubsub.publish(this.triggerTopic, this.triggerData);
	}

}