import BestiaView from './bestia/BestiaView';
import AttackView from './attack/AttackView';
import Chat from './chat/Chat';
import Inventory from './inventory/Inventory';
import Config from './Config';

/**
 * This class manages the shortcuts of the game for the player. It listens to
 * keyboard inputs and if a apropriate key was pressed it will trigger the
 * associated action with the key.
 */
export default class ShortcutView {
	
	constructor(pubsub) {
		if(!pubsub){
			throw 'Pubsub can not be empty.';
		}
		
		this._pubsub = pubsub;
	}
	
}