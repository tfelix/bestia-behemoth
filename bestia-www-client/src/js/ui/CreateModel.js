import BestiaView from './bestia/BestiaView';
import AttackView from './attack/AttackView';
import Chat from './chat/Chat';
import Inventory from './inventory/Inventory';
import Config from './Config';

/**
 * Contains a function which creates the skeleton of the view model and returns
 * it.
 */

export default function createModel(pubsub, urlHelper) {
	
	if(!pubsub) {
		throw 'PubSub can not be null.';
	}
	
	// ==== Bestia View Model
	var model = {
		
		bestias : new BestiaView(pubsub, urlHelper),
		
		chat : new Chat(pubsub),
		
		attacks : new AttackView(pubsub),
		
		inventory : new Inventory(pubsub, urlHelper),
		
		config : new Config(pubsub),
		
		dialog : {
			//logout : new LogoutDialog()
		}
	}
	
	return Object.freeze(model);
}