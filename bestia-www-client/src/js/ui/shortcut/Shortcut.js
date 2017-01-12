import BestiaView from './bestia/BestiaView';
import AttackView from './attack/AttackView';
import Chat from './chat/Chat';
import Inventory from './inventory/Inventory';
import Config from './Config';

/**
 * Holds the data for the shortcut.
 */
export default class Shortcut {
	
	constructor(keycode, key, image, action) {
		
		this.keycode = keycode;
		this._action = action;
		
		this.key = ko.observable(key);
		this.image = ko.observable(image);
		
	}
	
	/**
	 * Triggers the associated action with this shortcut.
	 */
	triggerAction() {
		this._action();
	}
	
}