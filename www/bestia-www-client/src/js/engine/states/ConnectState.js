import * as Phaser from 'phaser';

/**
 * State is established if the connection is lost. It will wait for a
 * reconnection event to occure and start the loading phase.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
export default class ConnectState extends Phaser.Scene {

	constructor(config) {
		super(config);

		Phaser.Scene.call(this, {
			key: 'connect'
		});
	}

	create() {
		const style = {
			font: 'bold 32px Arial',
			fill: '#fff',
			boundsAlignH: 'center',
			boundsAlignV: 'middle'
		};

		var txt = this.add.text(this.game.config.width / 2, 
			this.game.config.height / 2, 
			'Connecting...', 
			style);
		txt.setOrigin(0.5);
		txt.align = 'center';
	}
}