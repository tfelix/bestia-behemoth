/*global Phaser */


import Signal from '../io/Signal.js';
import PubSub from '../util/PubSub';
import EngineContext from './EngineContext.js';
import BootState from './states/BootState.js';
import ConnectingState from './states/ConnectingState.js';
import GameState from './states/GameState.js';
import InitializeState from './states/InitializeState';
import LoadingState from './states/LoadingState.js';


const MAX_RECON_TIME = 45;

/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server. It manages the phaserjs states
 * and also the state transitions depending on external events.
 * 
 * @constructor
 * @class Bestia.Engine
 * @param {Bestia.PubSub}
 *            pubsub - Publish/Subscriber interface.
 */
export default class Engine {
	constructor(pubsub, url) {

		this._disconnectCount = 0;

		// Determine the size of the canvas. And create the game object.
		this.game = new Phaser.Game(800, 600, Phaser.AUTO, 'bestia-canvas', null, false, false);
		
		// Prepare the context.
		let ctx = new EngineContext(pubsub, url);
		
		// Create the states.
		this.game.state.add('boot', new BootState(ctx));
		this.game.state.add('initial_loading', new InitializeState(ctx));
		this.game.state.add('connecting', new ConnectingState(ctx));
		this.game.state.add('load', new LoadingState(ctx));
		this.game.state.add('game', new GameState(ctx));

		// ==== PREPARE HANDLER ====

		// React on bestia selection changes. We need to re-trigger the map
		// loading. This event will fire if we have established a connection.
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._handlerOnBestiaSelected, this);
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handlerOnConnectionLost, this);
		pubsub.subscribe(Signal.ENGINE_BOOTED, this._handlerOnBooted, this);
		pubsub.subscribe(Signal.ENGINE_INIT_LOADED, this._handlerOnInitLoaded, this);
		pubsub.subscribe(Signal.ENGINE_FINISHED_MAPLOAD, this._handlerOnFinishedMapload, this);
		
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

	/**
	 * After booting was done. Init the game.
	 */
	_handlerOnBooted() {
		console.debug('Booting finished. Starting load.');
		this.game.state.start('initial_loading');
	}

	_handlerOnFinishedMapload() {
		console.debug('Mapload finished. Starting game.');
		this.game.state.start('game');
	}
}