import * as Phaser from 'phaser';
import Signal from '../../io/Signal.js';
import LOG from '../../util/Log';

/**
 * State is triggered once when the game starts. It will preload all the REALLY
 * important and needed assets in order to to a proper loading screen and do
 * some basic (but very important!) game setup. Other asset loadings should go
 * into the InitializeState which will then show the user a proper loading
 * screen.
 * 
 * @constructor
 */
export default class BootState extends Phaser.Scene {

	constructor(config) {
		super(config);
		Phaser.Scene.call(this, {
			key: 'boot'
		});
	}

	preload() {
		this.load.image('bestia-logo', 'assets/img/bestia-logo.png');
	}

	create() {
		// Setup the game context.
		LOG.info('Booting finished. Starting to initialize.');
		//engineContext.pubsub.publish(Signal.ENGINE_BOOTED);

		this.scene.start('initialize', 'test');
	}
}