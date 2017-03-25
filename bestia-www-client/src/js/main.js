import { version } from '../../package.json';
import ko from 'knockout';
import KoAjaxComponentLoader from './ui/KoAjaxComponentLoader';
import PubSub from './util/PubSub';
import UrlHelper from './util/UrlHelper';
import Chat from './ui/chat/Chat';
import LOG from './util/Log';
import Authenticator from './io/Authenticator';
import Connection from './io/Connection.js';
import Engine from './engine/Engine.js';
import Inventory from './ui/inventory/Inventory';
import AttackView from './ui/attack/AttackView';
import BestiaView from './ui/bestia/BestiaView';

// Creating all needed components.
let pubsub = new PubSub();
let urlHelper = new UrlHelper('assets/');
let auth = new Authenticator(pubsub);
let engine = new Engine(pubsub, urlHelper);
let connection = new Connection(pubsub);

// Some views share a view model.
let bestiaView = new BestiaView(pubsub, urlHelper);

// we register the component loader.
ko.components.loaders.unshift(new KoAjaxComponentLoader());


// === Register the components ===
ko.components.register('bestia-chat', {
	viewModel: {
		createViewModel: function (params, componentInfo) {
			return new Chat(pubsub);
		}
	},
	template: { fromUrl: 'chat.html' }
});

ko.components.register('bestia-inventory', {
	viewModel: {
		createViewModel: function () {
			return new Inventory(pubsub, urlHelper);
		}
	},
	template: { fromUrl: 'inventory.html' }
});


ko.components.register('bestia-attacks', {
	viewModel: {
		createViewModel: function () {
			return new AttackView(pubsub, null);
		}
	},
	template: { fromUrl: 'attacks.html' }
});


ko.components.register('bestia-overview', {
	viewModel: { instance: bestiaView },
	template: { fromUrl: 'bestia_overview.html' }
});

ko.components.register('bestia-selected', {
	viewModel: { instance: bestiaView },
	template: { fromUrl: 'bestia_selected.html' }
});

// DOM Ready
document.addEventListener('DOMContentLoaded', function () {
	LOG.info('Starting Bestia Client V.' + version);

	// Bind the DOM to the game.
	ko.applyBindings({});

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
