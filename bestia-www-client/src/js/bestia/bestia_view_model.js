/**
 * @author       Thomas Felix <thomas.felix@tfelix.de>
 * @copyright    2015 Thomas Felix
 */

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