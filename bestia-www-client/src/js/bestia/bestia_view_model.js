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
Bestia.BestiaViewModel = function(msg) {
	var self = this;
	
	this.playerBestiaId = ko.observable();
	this.databaseName = ko.observable('');
	this.equip = [];
	this.location = ko.observable('');
	this.posX = ko.observable(0);
	this.posY = ko.observable(0);
	this.saveLocation = ko.observable();
	this.customName = ko.observable('');
	this.sprite = ko.observable('');
	this.statusEffects = [];
	this.iconUrl = ko.pureComputed(function() {
		return Bestia.Urls.assetsMobIcon + self.databaseName() + '.png';
	});
	this.level = ko.observable(1);
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
	this.playerBestiaId(msg.id);
	this.location(msg.cl.mdbn);
	this.posX(msg.cl.x);
	this.posY(msg.cl.y);
	this.saveLocation(msg.sl.mdbn);
	this.customName(msg.cn);
	this.level(msg.lv);
	this.databaseName(msg.b.bdbn);
	this.sprite(msg.b.s);
	//this.statusEffects = [];
	//this.slot(msg.sl);
	this.statusPoints.update(msg.sp);

	self.selectBestia = function(bestia) {
		console.log("Selecting bestia: " + bestia);
	};
};