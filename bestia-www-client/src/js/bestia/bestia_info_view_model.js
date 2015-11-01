/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * Holds complete overview of all selected bestias.
 * 
 * @class Bestia.BestiaInfoViewModel
 * @constructor
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
Bestia.BestiaInfoViewModel = function(pubsub) {
	
	if(!(pubsub instanceof Bestia.PubSub)) {
		throw "Bestia.BestiaInfoViewModel: Pubsub is not optional.";
	}
	
	var self = this;
	this._pubsub = pubsub;

	this.selectedBestia = ko.observable();
	
	this.masterBestia = ko.observable(); 
	this.selectedBestia = ko.observable();	
	
	this.bestias = ko.observableArray([]);
	
	this.slots = ko.observable(0);

	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	var onMessageHandler = function(_, msg) {
		console.debug('Update bestia model with data.');
		
		var bestia = new Bestia.BestiaViewModel(self._pubsub, msg.b);
		
		// Check if the bestia is already inside our cache.
		for(var i = 0; i < self.bestias().length; i++) {
			if(self.bestias()[i].playerBestiaId() === bestia.playerBestiaId()) {
				// Just update it.
				self.bestias()[i].update(msg.b);
				return;
			}
		}
		
		// If the bestia was not found however to the extended logic. First add it.
		self.bestias.push(bestia);
		
		// Check if we have unselected master.
		if(self.masterBestia() === undefined && msg.im === true) {
			self.masterBestia(bestia);
			self.selectBestia(bestia);
		}
	};

	// Register for messages from the server.
	pubsub.subscribe('bestia.info', onMessageHandler);
};

Bestia.BestiaInfoViewModel.prototype.selectBestia = function(bestia) {
	this.selectedBestia(bestia);
	this._pubsub.publish('client.selectedBestia', this.selectedBestia());
};
