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


// === Register the components.
ko.components.register('bestia-chat', {
    viewModel: { instance: model.chat },
    template: { fromUrl: 'chat.html' }
});
ko.components.register('bestia-inventory', {
    viewModel: { instance: model.inventory },
    template: { fromUrl: 'inventory.html' }
});
ko.components.register('bestia-attacks', {
    viewModel: { instance: model.attacks },
    template: { fromUrl: 'attacks.html' }
});
ko.components.register('bestia-shortcuts', {
    viewModel: { instance: model.inventory },
    template: { fromUrl: 'inventory.html' }
});

// DOM Ready
$(function(){
	console.log('Starting Bestia Client V.' + VERSION);
	
	// Bind the DOM to the game.
	ko.applyBindings(model);
	
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
});

// Export game to global if dev.
// @ifdef DEVELOPMENT
window.bestiaGame = game;
//window.bestiaPages = pages;
// @endif


/*
i18n.init({
	lng : 'de',
	fallbackLng : false
}, function() {
	// Translate document.
	$('body').i18n();

	// Start game.
	$(document).ready(main);
});*/
