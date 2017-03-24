import { version } from '../../package.json';
import ko from 'knockout';
import KoAjaxComponentLoader from './ui/KoAjaxComponentLoader';
import PubSub from './util/PubSub';
import UrlHelper from './util/UrlHelper';
import BestiaGame from './BestiaGame';
import Chat from './ui/chat/Chat';
import LOG from './util/Log';

let pubSub = new PubSub();
let urlHelper = new UrlHelper('assets/');

// we register the component loader.
ko.components.loaders.unshift(new KoAjaxComponentLoader());

//Creating the bestia game.
let game = new BestiaGame(pubSub, urlHelper);

// === Register the components ===
ko.components.register('bestia-chat', {
	createViewModel: function(){
		return new Chat(pubSub);	
	},
	template: { fromUrl: 'chat.html' }
});

/*
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
*/
// DOM Ready
document.addEventListener('DOMContentLoaded', function () {
	LOG.info('Starting Bestia Client V.' + version);

	// Bind the DOM to the game.
	ko.applyBindings();

	// Add click handler.
	/*$('#btn-inventory').click(function() {
		model.attacks.show(false);
		model.inventory.show(!model.inventory.show());
	});

	$('#btn-attacks').click(function() {
		// Hide all others.
		model.inventory.show(false);
		model.attacks.show(!model.attacks.show());
	});*/
});


// Export game to global if dev.
window.bestiaGame = game;
