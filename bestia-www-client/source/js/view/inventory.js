/**
* @author       Thomas Felix <thomas.felix@tfelix.de>
* @copyright    2015 Thomas Felix
*/

(function(globals){
	"use strict";
	globals.Bestia = {};
	globals.Bestia.Inventory = {};
}(this));

Bestia.Inventory.EquipItemInfoViewModel = function() {
	var self = this;
	self._upgrade_lv = ko.observable(0);
	self.upgradeLevel = ko.pureComputed(function(){
		if(self._upgrade_lv > 0) {
			return '+'+self._upgrade_lv;
		} else {
			return '';
		}
	});
	self.forger = null;
	self.bBroken = ko.observable(false);
};

Bestia.Inventory.EquipItemInfoViewModel.prototype.update = function(msg) {
	
};

/**
 * In this module all methods, views for the display and the 
 * user interaction with the inventory will be defined.
 * 
 * @class Bestia.Inventory.ItemViewModel
 * @constructor
 * @param {Phaser.Sprite} sprite - A reference to the Game Object that owns this AnimationManager.
 */
Bestia.Inventory.ItemViewModel = function() {
	var self = this;
	self.itemId = ko.observable(0);
	self.playerItemId = ko.observable(0);
	self.img = ko.observable('');
	self._type = '';
	self.type = ko.pureComputed(function(){
		switch(self._type) {
		case 0:
			return 'etc';
		case 1:
			return 'usable';
		case 2:
			return 'equip';
		}
	});
	self.bQuestItem = ko.observable(true);
	self.bSoulbound = ko.observable(true);
	self.amount = ko.observable(0);
	self.name = ko.observable('');
};

Bestia.Inventory.ItemViewModel.prototype.update = function(msg) {
	
};