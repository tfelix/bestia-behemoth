'use strict';
/**
 * This message is send by the server if some data regarding a bestia are
 * changing. We will take the data and update the view model with all the
 * information.
 * 
 * @module Server.Config
 */
var Bestia = window.Bestia = window.Bestia || {};
(function(app, $, ko) {
	
	app.view = app.view || {};
	app.view.bestias = [];
	
	function BestiaViewModel(msg) {
		this.model = {
			playerBestiaId: ko.observable(),
			sprite: ko.observable(),
			equip: [],
			location: ko.observable(),			
			customName: ko.observable(''),
			name: ko.observable(),
			statusEffects: [],
			image: ko.observable(),
			slot: ko.observable()
		};
	}
	BestiaViewModel.prototype.update = function(msg) {
		this.model.playerBestiaId(msg.pbid);
		this.model.sprite(app.server.Config.makeUrl('sprite', msg.s));
		this.model.location({mapId: msg.loc.mid, x: msg.loc.x, y: msg.loc.y});
		//this.model.location.mapId(msg.loc.mid);
		//this.model.location.x(msg.loc.x);
		//this.model.location.y(msg.loc.y);
		this.model.customName(msg.cn);
		this.model.name(msg.n);
		this.model.image(app.server.Config.makeUrl('image', msg.img));
		this.model.slot(msg.sl);
	}
	
	// Create new view models for each bestia.
	app.view.bestias.push(new BestiaViewModel());
	app.view.bestias.push(new BestiaViewModel());
	app.view.bestias.push(new BestiaViewModel());
	app.view.bestias.push(new BestiaViewModel());
	app.view.bestias.push(new BestiaViewModel());
	app.view.bestias.push(new BestiaViewModel());
	
	// Apply bindings AFTER the DOM has loaded.
	/*$(document).ready(function(){
		ko.applyBindings(app.view.bestias[0].model, $('#selected-bestia').get(0));
	});*/
	
	
	
	/**
	 * Central configuration variables.
	 */
	var onMessageHandler = function(_, msg) {
		console.log('Update Bestia. player_bestia_id: ' + msg.pbid);
		
		var slot = msg.sl;
		
		app.view.bestias[slot].update(msg);
	}

	// Register for messages.
	$.subscribe('bestia.update', onMessageHandler);
})(Bestia, jQuery, ko);