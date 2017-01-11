import KoAjaxComponentLoader from './ui/KoAjaxComponentLoader';

import BestiaGame from './BestiaGame';
import LogoutDialog from './dialog/LogoutDialog';
import VERSION from './Version';

//Creating the bestia game.
var game = new BestiaGame();

ko.components.loaders.unshift(new KoAjaxComponentLoader(game.pubsub));

ko.components.register('bestia-chat', {
    viewModel: { test: 124 },
    template: { fromUrl: 'chat.html' }
});

function main() {

	console.log('Starting Bestia Client V.' + VERSION);


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
