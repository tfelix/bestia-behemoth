import KoAjaxComponentLoader from './ui/KoAjaxComponentLoader';

import PubSub from './util/PubSub';
import UrlHelper from './util/UrlHelper';
import createModel from './ui/CreateModel';
import BestiaGame from './BestiaGame';
import VERSION from './Version';

let pubSub = new PubSub();
let urlHelper = new UrlHelper('assets/');

let model = createModel(pubSub, urlHelper);

//Creating the bestia game.
let game = new BestiaGame(pubSub, urlHelper);



ko.components.register('bestia-chat', {
    viewModel: { instance: model.chat },
    template: { fromUrl: 'chat.html' }
});

// DOM Ready
$(function(){
	// Bind the DOM to the game.
	ko.applyBindings(model);
});

// Export game to global if dev.
// @ifdef DEVELOPMENT
window.bestiaGame = game;
//window.bestiaPages = pages;
// @endif


function main() {

	console.log('Starting Bestia Client V.' + VERSION);


	// UI init must wait until dom is loaded and accessible.
	/*var pages = {
		logoutDialog : new LogoutDialog('#modal-logout', game.pubsub)
	};*/

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
