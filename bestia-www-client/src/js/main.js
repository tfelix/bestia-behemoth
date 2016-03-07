Bestia.Game = function() {
	this.pubsub = new Bestia.PubSub();
	this.config = new Bestia.Config(this.pubsub);
	this.i18n = new Bestia.I18n(this.pubsub);
	this.urlHelper = new Bestia.UrlHelper(Bestia.Urls.assetsRoot);

	this.inventory = new Bestia.Inventory(this.pubsub, this.i18n);
	this.bestias = new Bestia.BestiaInfoViewModel(this.pubsub, this.urlHelper);
	this.attacks = new Bestia.BestiaAttacks(this.pubsub, this.i18n);
	
	this.chat = new Bestia.Chat($('#chat'), this);
	this.engine = new Bestia.Engine(this.pubsub, this.urlHelper);
	this.connection = new Bestia.Connection(this.pubsub);
	
	var self = this;
	
	/**
	 * Start the connection process.
	 */
	this.pubsub.subscribe(Bestia.Signal.IO_CONNECT, function(){
		self.connection.connect();
	});
	
	/**
	 * Disconnect from the server.
	 */
	this.pubsub.subscribe(Bestia.Signal.IO_DISCONNECT, function(){
		self.connection.disconnect();
	});
	
	/**
	 * Authentication error. Go to logout.
	 */
	this.pubsub.subscribe(Bestia.Signal.AUTH_ERROR, function(){
		window.location.replace(Bestia.Urls.loginHtml);
	});
};


function main() {

	console.log("Starting Bestia Client V." + Bestia.VERSION);

	// Creating the bestia game.
	var game = new Bestia.Game();

	// UI init must wait until dom is loaded and accessible.
	Bestia.page = {
		logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout',
				game.pubsub)
	};

	// Bind the DOM to the game.
	ko.applyBindings(game);

	// Add click handler.
	$('#btn-inventory').click(function() {
		game.attacks.close();
		game.inventory.showWindow(!game.inventory.showWindow());
	});
	
	$('#btn-attacks').click(function() {
		// Hide all others.
		game.inventory.close();	
		game.attacks.showWindow(!game.attacks.showWindow());
		
		if(!game.attacks.isLoaded()) {
			game.attacks.request();
		}
	});
	
	// Export game to global if dev.
	// @ifdef DEVELOPMENT
	window.bestiaGame = game;
	// @endif
}

i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	// Translate document.
	$('body').i18n();

	// Start game.
	$(document).ready(main);
});