import { version } from '../../package.json';
//import ko from 'knockout';
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

alert('test');
//this.i18n = new I18n(this.pubsub);

// Creating all needed components.
let pubsub = new PubSub();
let urlHelper = new UrlHelper('assets/');
let auth = new Authenticator(pubsub);
let engine = new Engine(pubsub, urlHelper);
let connection = new Connection(pubsub);

// we register the component loader.
//ko.components.loaders.unshift(new KoAjaxComponentLoader());

ko.components.register('like-widget', {
    viewModel: function(params) {
        // Data: value is either null, 'like', or 'dislike'
        this.chosenValue = params.value;
         
        // Behaviors
        this.like = function() { this.chosenValue('like'); }.bind(this);
        this.dislike = function() { this.chosenValue('dislike'); }.bind(this);
    },
    template:
        '<div class="like-or-dislike" data-bind="visible: !chosenValue()">\
            <button data-bind="click: like">Like it</button>\
            <button data-bind="click: dislike">Dislike it</button>\
        </div>\
        <div class="result" data-bind="visible: chosenValue">\
            You <strong data-bind="text: chosenValue"></strong> it\
        </div>'
});


// === Register the components ===
ko.components.register('test', {
	viewModel: function(){
		this.text = ko.observable('Hello World');	
	},
	template: { fromUrl: 'test.html' }
});


/*
ko.components.register('bestia-chat', {
	createViewModel: (params, componentInfo) => new Chat(pubsub),
	template: { fromUrl: 'chat.html' }
});

ko.components.register('bestia-inventory', {
	createViewModel: function () {
		return new Inventory(pubsub, urlHelper);
	},
	template: { fromUrl: 'inventory.html' }
});

ko.components.register('bestia-attacks', {
	createViewModel: function () {
		return new AttackView(pubsub, null);
	},
	template: { fromUrl: 'attacks.html' }
});

/*
ko.components.register('bestia-overview', {
	createViewModel: function () {
		return new AttackView(pubsub, null);
	},
	template: { fromUrl: 'bestia_overview.html' }
});

ko.components.register('bestia-selected', {
	createViewModel: function () {
		return new AttackView(pubsub, null);
	},
	template: { fromUrl: 'bestia_selected.html' }
});*/

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
