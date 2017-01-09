import BestiaGame from './BestiaGame.js';
import LogoutDialog from './dialog/LogoutDialog.js';
import VERSION from './Version.js';

/*
ko.components.register('like-widget', {
	viewModel: function(params) {
        // Data: value is either null, 'like', or 'dislike'
        this.chosenValue = params.value;
         
        // Behaviors
        this.like = function() { this.chosenValue('like'); }.bind(this);
        this.dislike = function() { this.chosenValue('dislike'); }.bind(this);
    },
    template: { require: 'text!comp/test.html' }
});*/

function main() {

	console.log('Starting Bestia Client V.' + VERSION);

	// Creating the bestia game.
	var game = new BestiaGame();


	// UI init must wait until dom is loaded and accessible.
	var pages = {
		logoutDialog : new LogoutDialog('#modal-logout', game.pubsub)
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
	});
	
	// Export game to global if dev.
	// @ifdef DEVELOPMENT
	window.bestiaGame = game;
	window.bestiaPages = pages;
	// @endif
}

i18n.init({
	lng : 'de',
	fallbackLng : false
}, function() {
	// Translate document.
	$('body').i18n();

	// Start game.
	$(document).ready(main);
});
