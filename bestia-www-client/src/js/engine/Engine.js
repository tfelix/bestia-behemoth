/*global Phaser */

import Signal from '../io/Signal.js';
import BootState from './states/BootState.js';
import ConnectingState from './states/ConnectingState.js';
import GameState from './states/GameState.js';
import InitializeState from './states/InitializeState';
import LoadingState from './states/LoadingState.js';
import { engineContext } from './EngineData';
import LOG from '../util/Log';

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

		// Setup the static/global data for the components to fetch.
		engineContext.game = this.game;
		engineContext.pubsub = pubsub;
		engineContext.url = url;

		// Create the states.
		this.game.state.add('boot', new BootState(this._ctx));
		this.game.state.add('initial_loading', new InitializeState(this._ctx));
		this.game.state.add('connecting', new ConnectingState(this._ctx));
		this.game.state.add('load', new LoadingState(this._ctx));
		this.game.state.add('game', new GameState(this._ctx));

		// ==== PREPARE HANDLER ====

		// React on bestia selection changes. We need to re-trigger the map
		// loading. This event will fire if we have established a connection.
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._handlerOnBestiaSelected, this);
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handlerOnConnectionLost, this);
		pubsub.subscribe(Signal.ENGINE_FINISHED_MAPLOAD, this._handlerOnFinishedMapload, this);

		// When everything is setup. Start the engine.
		this.game.state.start('boot');
	}

	/**
	 * Triggers a mapload if a bestia was selected.
	 */
	_handlerOnBestiaSelected(_, data) {
		LOG.info('A new bestia selected. Starting loading process.');

		// TODO Check if we can go without loading: we must be inside view range AND
		// have the multi sprite cached. Currently not supported.

		this.game.state.start('load');
	}

	/**
	 * Shows the "now connecting" screen to visualize connection lost.
	 */
	_handlerOnConnectionLost() {
		LOG.info('Connection lost switching to: connecting state.');
		this.game.state.start('connecting');
	}

	_handlerOnFinishedMapload() {
		LOG.info('Mapload finished. Starting game.');
		this.game.state.start('game');
	}
}