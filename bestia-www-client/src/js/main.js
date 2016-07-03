import BestiaGame from './bestia.js';

function main() {

	console.log("Starting Bestia Client V." + Bestia.VERSION);

	// Creating the bestia game.
	var game = new BestiaGame();

	/*
	// UI init must wait until dom is loaded and accessible.
	Bestia.page = {
		logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout', game.pubsub)
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

		if (!game.attacks.isLoaded()) {
			game.attacks.request();
		}
	});*/
	
	// Export game to global if dev.
	// @ifdef DEVELOPMENT
	window.bestiaGame = game;
	// @endif
}


/*
i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	// Translate document.
	$('body').i18n();

	// Start game.
	$(document).ready(main);
});*/

$(document).ready(main);