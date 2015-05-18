/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

/**
 * ViewModel for the Status points.
 * 
 * @class Bestia.StatusPointViewModel
 * @constructor
 */
Bestia.StatusPointViewModel = function(msg) {
	this.curMana = ko.observable(0);
	this.maxMana = ko.observable(0);
	this.curHp = ko.observable(0);
	this.maxHp = ko.observable(0);
	this.manaWidth = ko.computed(function() {
		return (this.maxMana() === 0) ? '0%' : (Math.floor(this.curMana() * 100 / this.maxMana())) + '%';
	}, this);
	this.hpWidth = ko.computed(function() {
		return (this.maxHp() === 0) ? '0%' : (Math.floor(this.curHp() * 100 / this.maxHp())) + '%';
	}, this);

	if (msg !== undefined) {
		this.update(msg);
	}
};
/**
 * ViewModel for the Status points.
 * 
 * @method Bestia.StatusPointViewModel#update
 * @param {Object}
 *            msg - Message from the server to fill the model.
 */
Bestia.StatusPointViewModel.prototype.update = function(msg) {
	this.curMana(msg.cMana);
	this.maxMana(msg.mMana);
	this.curHp(msg.cHp);
	this.maxHp(msg.mHp);
};

/**
 * ViewModel of a Bestia.
 * 
 * @class Bestia.BestiaViewModel
 * @param {Object}
 *            msg - Optional. Server message object to initialize the model with
 *            values.
 * @param {Bestia.Net}
 *            net - Net helper object to generate URLs within the model.
 * @constructor
 */
Bestia.BestiaViewModel = function(net, msg) {

	if (net === undefined) {
		throw "Net param is not optional.";
	}

	this._net = net;
	this.playerBestiaId = ko.observable();
	this.equip = [];
	this.location = ko.observable();
	this.customName = ko.observable('');
	this.databaseName = ko.observable();
	this.statusEffects = [];
	this.image = ko.observable();
	this.slot = ko.observable();
	this.statusPoints = new Bestia.StatusPointViewModel();

	if (msg !== undefined) {
		this.update(msg);
		this.statusPoints.update(msg.sp);
	}
};

/**
 * Updates the model with new data from the server.
 * 
 * @method Bestia.BestiaViewModel#update
 * @param {Object}
 *            msg - Message object from the server.
 */
Bestia.BestiaViewModel.prototype.update = function(msg) {
	var self = this;
	this.playerBestiaId(msg.pbid);
	this.location('');
	this.customName(msg.cn);
	this.databaseName(msg.bdbn);
	this.statusEffects = [];
	this.image(self._net.getMobImageUrl(self.databaseName()).img);
	this.slot(msg.sl);
	this.statusPoints.update(msg.sp);

	self.selectBestia = function(bestia) {
		console.log("Selecting bestia: " + bestia);
	};
};

/**
 * Holds complete overview of all selected bestias.
 * 
 * @class Bestia.BestiaInfoViewModel
 * @constructor
 * @param {Bestia.Net}
 *            net - Net object to create URLs.
 */
Bestia.BestiaInfoViewModel = function(net) {

	if (net === undefined) {
		throw "Net is not an optional parameter.";
	}

	var self = this;

	this._net = net;
	this.masterBestia = new Bestia.BestiaViewModel(net);
	this.selectedBestia = new Bestia.BestiaViewModel(net);
	this.bestias = ko.observableArray([]);
	this.slots = ko.observable();

	/**
	 * Handler to fill out the data. We get this data from the bestia.update
	 * messages.
	 */
	var onInitHandler = function(_, msg) {
		console.debug('Init bestia model with data.');
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
	}

	// Register for messages from the server.
	Bestia.subscribe('bestia.init', onInitHandler);
	Bestia.subscribe('bestia.update', onUpdateHandler);
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
		var model = new Bestia.BestiaViewModel(self._net, val);
		self.bestias.push(model);
	});

	this.slots(msg.s);
};
