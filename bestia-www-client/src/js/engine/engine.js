import Signal from '../io/Signal.js';
import EngineContext from './core/EngineContext.js';
import BootState from './states/BootState.js';
import ConnectingState from './states/ConnectingState.js';
import GameState from './states/GameState.js';
import InitializeState from './states/InitializeState.js';
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
			musicVolume : ko.observable(100)
		};

		this.bestia = undefined;

		/**
		 * Context to hold very important and shared data between the states or
		 * other classes. Note that this object is only fully initialized after the
		 * engine has started (that means has passed the boot state).
		 */
		this.ctx = new EngineContext(pubsub, this, url);

		// Determine the size of the canvas. And create the game object.
		var height = $(window).height();
		var width = $('#canvas-container').width();

		this.game = new Phaser.Game(width, height, Phaser.AUTO, 'bestia-canvas', null, false, false);

		this.game.state.add('boot', new BootState(this));
		this.game.state.add('connecting', new ConnectingState(this));
		this.game.state.add('initial_loading', new InitializeState(this));
		this.game.state.add('load', new LoadingState(this));
		this.game.state.add('game', new GameState(this));

		// ==== PREPARE HANDLER ====

		// React on bestia selection changes. We need to re-trigger the map loading.
		// This event will fire if we have established a connection.
		pubsub.subscribe(Signal.BESTIA_SELECTED, this._handlerOnBestiaSelected.bind(this));
		pubsub.subscribe(Signal.IO_CONNECTION_LOST, this._handlerOnConnectionLost.bind(this));
		pubsub.subscribe(Signal.ENGINE_BOOTED, this._handlerOnBooted.bind(this));
		pubsub.subscribe(Signal.ENGINE_INIT_LOADED, this._handlerOnInitLoaded.bind(this));
		pubsub.subscribe(Signal.ENGINE_FINISHED_MAPLOAD, this._handlerOnFinishedMapload.bind(this));

		// When everything is setup. Start the engine.
		this.game.state.start('boot');
		
		// We need right click. So hide it.	
		$('#bestia-canvas').bind('contextmenu', function(e){
			e.preventDefault();
		}); 
	}
	
	/**
	 * Triggers a mapload if a bestia was selected.
	 */
	_handlerOnBestiaSelected(_, data) {
		console.debug('New bestia selected. Starting loading process.');
		this.bestia = data;
		this.loadMap(data);
	}

	/**
	 * Shows the "now connecting" screen to visualize connection lost.
	 */
	_handlerOnConnectionLost() {
		this.game.state.start('connecting');
	}

	_handlerOnInitLoaded() {
		this.game.state.start('connecting');
	}

	_handlerOnBooted() {
		this.game.state.start('initial_loading');
	}

	_handlerOnFinishedMapload() {
		this.game.state.start('game');
	}

	/**
	 * Loads a certain map. If the map is different then the current map it will
	 * trigger a complete map reload. Otherwise it will just do a partial load an
	 * shift the active viewport to the newly selected bestia.
	 * 
	 * @param {Bestia.BestiaViewModel}
	 *            bestia - Bestia to use as the player character.
	 * @method Bestia.Engine#loadMap
	 */
	loadMap() {
		console.debug('Loading map.');

		// Check if we can do a partial mapload or a full map reload.
		var world = this.ctx.zone;
		if (world === null || world.name !== this.bestia.location()) {
			// We need to do a full load.
			this.game.state.start('load');
		}
		// else: Partial load only (just switch view to active bestia).
		// TODO
	}
}