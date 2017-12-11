import * as Phaser from 'phaser';

/**
 * This scene just shows an error if a connection to the bestia server was not possible.
 * 
 * @constructor
 * @class ErrorState
 */
export default class ErrorState extends Phaser.Scene {

	constructor(config) {
		super(config);

		Phaser.Scene.call(this, {
			key: 'error'
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
			'Error while connecting to server.', 
			style);
		txt.setOrigin(0.5);
		txt.align = 'center';
	}
}