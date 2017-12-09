import * as Phaser from 'phaser';
import Signal from '../io/Signal.js';
import BootState from './states/BootState.js';
import ConnectState from './states/ConnectState.js';
import GameState from './states/GameState.js';
import InitializeState from './states/InitializeState';
import LoadState from './states/LoadState.js';
import {
	engineContext
} from './EngineData';
import LOG from '../util/Log';
import EntityCacheEx from './entities/EntityCacheEx';
import EntityComponentUpdater from './entities/EntityComponentUpdater';

/**
 * Bestia Graphics engine. Responsible for displaying the game collecting user
 * input and sending these data to the server. It manages the phaserjs states
 * and also the state transitions depending on external events.
 * 
 * @constructor *           
 * @class Bestia.Engine
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
			scene: [BootState, InitializeState, ConnectState, LoadState],
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

		// ==== Create the states ====
		//this.game.state.add('boot', new BootState());
		//this.game.state.add('initial_loading', new InitializeState());
		//this.game.state.add('connecting', new ConnectingState());
		//this.game.state.add('load', new LoadingState());
		//this.game.state.add('game', new GameState());

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
	_handlerOnBestiaSelected(_, data) {
		LOG.info('A new bestia selected. Starting loading process.');

		// TODO Check if we can go without loading: we must be inside view range AND
		// have the multi sprite cached. Currently not supported.
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