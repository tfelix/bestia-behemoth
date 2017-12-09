import * as Phaser from 'phaser';
import Signal from '../io/Signal.js';
import BootState from './states/BootState.js';
import ConnectState from './states/ConnectState.js';
import GameState from './states/GameState.js';
import InitializeState from './states/InitializeState';
import LoadState from './states/LoadState';
import LOG from '../util/Log';
import EntityCacheEx from './entities/EntityCacheEx';
import EntityComponentUpdater from './entities/EntityComponentUpdater';

/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server. It manages the phaserjs states
 * and also the state transitions depending on external events.
 *          
 * @class Engine
 * @param {PubSub} pubsub - Publish/Subscriber interface.
 */
export default class Engine {
	constructor(pubsub, url) {

		this._disconnectCount = 0;

		const config = {
			type: Phaser.WEBGL,
			width: 800,
			height: 600,
			backgroundColor: '#000000',
			parent: 'bestia-canvas',
			scene: [BootState, InitializeState, ConnectState, LoadState, GameState],
			title: 'Bestia - The Browsergame',
			url: 'http://bestia-game.net'
		};

		let entityCache = new EntityCacheEx();
		this._entityCompUpdater = new EntityComponentUpdater(pubsub, entityCache);

		// Determine the size of the canvas. And create the game object.
		this.game = new Phaser.Game(config);

		// Setup the static/global data for the components to fetch.
		engineContext.game = this.game;
		engineContext.pubsub = pubsub;
		engineContext.url = url;

		// ==== PREPARE HANDLER ====

		// React on bestia selection changes. We need to re-trigger the map
		// loading. This event will fire if we have established a connection.
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._handlerOnBestiaSelected, this);
		pubsub.subscribe(Signal.IO_DISCONNECTED, this._handlerOnConnectionLost, this);
		pubsub.subscribe(Signal.ENGINE_FINISHED_MAPLOAD, this._handlerOnFinishedMapload, this);

		// When everything is setup. Start the engine.
		this.game.scene.start('boot');
	}

	/**
	 * Triggers a mapload if a bestia was selected.
	 */
	_handlerOnBestiaSelected(_, bestia) {
		LOG.info('A new bestia selected. Starting loading process.');
		this.game.scene.start('load');
	}

	/**
	 * Shows the "now connecting" screen to visualize connection lost.
	 */
	_handlerOnConnectionLost() {
		LOG.info('Connection lost switching to: connecting state.');
		this.game.scene.start('connect');
	}

	_handlerOnFinishedMapload() {
		LOG.info('Mapload finished. Starting game.');
		this.game.scene.start('game');
	}
}