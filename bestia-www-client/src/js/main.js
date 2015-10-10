Bestia.Game = function() {
	this.pubsub = new Bestia.PubSub();
	this.config = new Bestia.Config(this.pubsub);

	this.inventory = new Bestia.Inventory(this.pubsub);
	this.bestias = new Bestia.BestiaInfoViewModel(this.pubsub);
	this.attacks = new Bestia.BestiaAttacks(this.pubsub);
	
	this.chat = new Bestia.Chat($('#chat'), this);
	this.engine = new Bestia.Engine(this.pubsub, this.config);
	this.connection = new Bestia.Connection(this.pubsub);

	this.connection.init();
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
		$('#modal-inventory').modal('show');
	});
	
	$('#btn-attacks').click(function() {
		$('#modal-attacks').modal('show');
		game.attacks.request();
	});

	// Add click handler.
	$('#btn-playground').click(function() {
		$('#modal-playground').modal('show');
	});
	
	// +++ PLAYGROUND
	$('#btn-useattack').click(function() {
		var msg = new Bestia.Message.AttackUse(1, 10, 10);
		game.pubsub.publish('io.sendMessage', msg);
	});
	
	$('#btn-request').click(function() {
		game.attacks.request();
	});
	
	// +++ Export game to global if dev.
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