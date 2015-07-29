Bestia.Game = function() {
	
	var self = this;

	this.pubsub = new Bestia.PubSub();
	this.config = new Bestia.Config(this.pubsub);

	this.config = new Bestia.Config();
	this.net = new Bestia.Net(this.config);
	this.inventory = new Bestia.Inventory(this.net);
	this.bestias = new Bestia.BestiaInfoViewModel(this.net);
	this.chat = new Bestia.Chat($('#chat'), this);
	this.engine = new Bestia.Engine(this.config);
	this.connection = new Bestia.Connection();

	this.connection.init();

	// UI init must wait until dom is loaded and accessible.
	$(document).ready(function() {
		self.page = {
			logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout')
		};

		// Bind the DOM to the game.
		ko.applyBindings(self);

		// $('#modal-inventory').modal('show');
	});
};

// Final code.
i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	$('body').i18n();
});