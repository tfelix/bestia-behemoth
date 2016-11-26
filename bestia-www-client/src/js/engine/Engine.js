/*global Phaser */

import Signal from '../io/Signal.js';
import PubSub from '../util/PubSub';
import EngineContext from './core/EngineContext.js';
import BootState from './states/BootState.js';
import ConnectingState from './states/ConnectingState.js';
import GameState from './states/GameState.js';
import InitialLoadingState from './states/InitialLoadingState';
import LoadingState from './states/LoadingState.js';

/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server.
 * 
 * @constructor
 * @class Bestia.Engine
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
export default class Engine {
	constructor(pubsub, url) {
		this.options = {
			enableMusic : ko.observable('true'),
			musicVolume : ko.observable(100),
			debug : ko.observable(false)
		};
		
		// Internal pubsub to let came components communicate with each other.
		this._gamePubSub = new PubSub();

		// Determine the size of the canvas. And create the game object.
		
		// var height = $(window).height();
		// var width = $('#canvas-container').width();

		this.game = new Phaser.Game(800, 600, Phaser.AUTO, 'bestia-canvas', null, false, false);
		
		this._ctx = new EngineContext(this.game, pubsub, url);

		this.game.state.add('boot', new BootState(this._ctx));
		this.game.state.add('connecting', new ConnectingState(this._ctx));
		this.game.state.add('initial_loading', new InitialLoadingState(this._ctx));
		this.game.state.add('load', new LoadingState(this._ctx));
		this.game.state.add('game', new GameState(this._ctx));

		// ==== PREPARE HANDLER ====

		// React on bestia selection changes. We need to re-trigger the map
		// loading. This event will fire if we have established a connection.
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._handlerOnBestiaSelected.bind(this));
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handlerOnConnectionLost.bind(this));
		pubsub.subscribe(Signal.ENGINE_BOOTED, this._handlerOnBooted.bind(this));
		pubsub.subscribe(Signal.ENGINE_INIT_LOADED, this._handlerOnInitLoaded.bind(this));
		pubsub.subscribe(Signal.ENGINE_FINISHED_MAPLOAD, this._handlerOnFinishedMapload.bind(this));
		
		// We need right click. So hide it.
		$('#bestia-canvas').bind('contextmenu', function(e){
			e.preventDefault();
		}); 
		
		// When everything is setup. Start the engine.
		this.game.state.start('boot');
	}
	
	/**
	 * Triggers a mapload if a bestia was selected.
	 */
	_handlerOnBestiaSelected(_, data) {
		console.debug('New bestia selected. Starting loading process.');
		
		// Check if we can go without loading: we must be inside view range AND
		// have the multi sprite cached. Currently not supported.
		this._ctx.playerBestia = data;
		this.game.state.start('load');		
	}

	/**
	 * Shows the "now connecting" screen to visualize connection lost.
	 */
	_handlerOnConnectionLost() {
		console.debug('Connection lost. Trying to reconnect.');
		this.game.state.start('connecting');
	}

	_handlerOnInitLoaded() {
		console.debug('Init finished. Starting to connect..');
		this.game.state.start('connecting');
	}

	_handlerOnBooted() {
		console.debug('Booting finished. Starting load.');
		this.game.state.start('initial_loading');
	}

	_handlerOnFinishedMapload() {
		console.debug('Mapload finished. Starting game.');
		this.game.state.start('game');
	}
}