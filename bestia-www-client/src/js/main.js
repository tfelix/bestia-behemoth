Bestia.Game = function() {
	this.pubsub = new Bestia.PubSub();
	this.config = new Bestia.Config(this.pubsub);

	this.inventory = new Bestia.Inventory(this.pubsub);
	this.bestias = new Bestia.BestiaInfoViewModel(this.pubsub);
	this.chat = new Bestia.Chat($('#chat'), this);
	this.engine = new Bestia.Engine(this.pubsub, this.config);
	this.connection = new Bestia.Connection(this.pubsub);

	this.connection.init();
};

// Final code.
i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	$('body').i18n();
});

/**
 * Das gefällt mir hier noch nicht. Das sollte eigentlich vielmehr in die Page Sektion von Bestia und das Bestia.Game ist eine eigene Klasse.
 */
$(document).ready(function() {
	
	console.log("Starting Bestia Client V." + Bestia.VERSION);

	// Creating the bestia game.
	var game = new Bestia.Game();

	// UI init must wait until dom is loaded and accessible.
	Bestia.page = {
		logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout', game.pubsub)
	};

	// Bind the DOM to the game.
	ko.applyBindings(game);
	
	// Add click handler.
	$('#btn-inventory').click(function(){
		$('#modal-inventory').modal('show');
	});
	
	// Add click handler.
	$('#btn-playground').click(function(){
		$('#modal-playground').modal('show');
	});
});