/*global Phaser */
import Signal from '../io/Signal';
import EntityCache from './entities/util/EntityCache';
import DemandLoader from './core/DemandLoader';
import IndicatorManager from './indicator/IndicatorManager';
import EffectsManager from './fx/EffectsManager';
import RenderManager from './renderer/RenderManager';
import EntityFactory from './entities/factory/EntityFactory';
import EntityUpdater from './entities/util/EntityUpdater';
import AnimationManager from './animation/AnimationManager';

export default class EngineContext {
	
	constructor(pubsub, url) {
		
		this._isInit = false;
		
		this._pubsub = pubsub;
		this._urlHelper = url;
		this._playerBestia = null;
		
		this._pubsub.subscribe(Signal.BESTIA_SELECTED, (_, bestia) => this._playerBestia = bestia);
	}
	
	initialize(phaserGame) {
		
		// Since the objects often reference to the engine context inside their 
		// ctor the order of the initialization is really important. Nether the less accessing the
		// methods of the engine ctx inside the object ctor should be avoided to tackle the problem.
		this._isInit = true;
		this._game = phaserGame;		
		this._entityCache = new EntityCache();		
		this._demandLoader = new DemandLoader(this);
		this._indicatorManager = new IndicatorManager(this);
		this._fxManager = new EffectsManager(this);
		this._renderManager = new RenderManager(this);
		this._entityFactory = new EntityFactory(this);
		this._entityUpdater = new EntityUpdater(this);
		this._animationManager = new AnimationManager(this);
	}
	
	_checkInit() {
		if(!this._isInit) {
			throw 'Context not called initialize() yet.';
		}
	}
	
	get game() {
		this._checkInit();
		return this._game;
	}
	
	get animationManager() {
		return this._animationManager;
	}
	
	get playerBestia() {
		return this._playerBestia;
	}
	
	get pubsub() {
		return this._pubsub;
	}
	
	get url() {
		return this._urlHelper;
	}
	
	get game() {
		this._checkInit();
		return this._game;
	}
	
	get indicatorManager() {
		this._checkInit();
		return this._indicatorManager;
	}
	
	get fxManager() {
		this._checkInit();
		return this._fxManager;
	}
	
	get entityFactory() {
		this._checkInit();
		return this._entityFactory;
	}
	
	get entityUpdater() {
		this._checkInit();
		return this._entityUpdater;
	}
	
	get entityCache() {
		this._checkInit();
		return this._entityCache;
	}
	
	get render() {
		this._checkInit();
		return this._renderManager;
	}
	
	/**
	 * Returns the onDemandLoader
	 */
	get loader() {
		this._checkInit();
		return this._demandLoader;
	}
	
}
