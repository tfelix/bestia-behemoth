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

	this.selectedBestia = ko.observable(0);
	
	this.masterBestia = new Bestia.BestiaViewModel(pubsub);
	this.selectedBestia = new Bestia.BestiaViewModel(pubsub);
	this.bestias = ko.observableArray([]);
	this.slots = ko.observable();

	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	var onInitHandler = function(_, msg) {
		console.debug('Update bestia model with data.');
		self.update(msg);
	};
	
	/**
	 * Handler is called if an update is send for one bestia.
	 */
	var onUpdateHandler = function(_, msg) {
		// Find the bestia which should be updated.
		var bestias = self.bestias();
		
		for(var i = 0; i < bestias.length; i++) {
			if(bestias[i].playerBestiaId() !== msg.pbid) {
				continue;
			}
			bestias[i].update(msg);
			break;
		}
		
		// Check aswell for the bestia master.
		if(self.selectedBestia.playerBestiaId() === msg.pbid) {
			self.selectedBestia.update(msg);
		}
	};

	// Register for messages from the server.
	pubsub.subscribe('bestia.info', onInitHandler);
	pubsub.subscribe('bestia.update', onUpdateHandler);
};

/**
 * Updates the bestia info view model with new data from the server.
 * 
 * @method BestiaInfoViewModel#update
 * @param {Object}
 *            msg - New message object from the server.
 */
Bestia.BestiaInfoViewModel.prototype.update = function(msg) {
	this.selectedBestia.update(msg.bm);

	var self = this;
	
	// Sort the bestias into slot order.
	msg.b.sort(function(a, b){
		if(a.sl == b.sl) {
			return 0;
		}
		
		return (a.sl > b.sl) ? 1 : -1;		
	});
	
	$(msg.b).each(function(_, val ) {
		var model = new Bestia.BestiaViewModel(self._pubsub, val);
		self.bestias.push(model);
	});

	this.slots(msg.s);
};
