Bestia.Game = function() {
	this.pubsub = new Bestia.PubSub();
	this.config = new Bestia.Config(this.pubsub);

	//this.inventory = new Bestia.Inventory(this.net);
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

$(document).ready(function() {

	var game = new Bestia.Game();

	// UI init must wait until dom is loaded and accessible.
	Bestia.page = {
		logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout')
	};

	// Bind the DOM to the game.
	ko.applyBindings(game);

	// $('#modal-inventory').modal('show');
});