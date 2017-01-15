
import Signal from '../../io/Signal.js';

/**
 * State is established if the connection is lost. It will wait for a
 * reconnection event to occure and start the loading phase.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
export default class ConnectingState {
	
	constructor(ctx) {

		this._ctx = ctx;
		
	}
	
	create() {
		var style = {
			font : 'bold 32px Arial',
			fill : '#fff',
			boundsAlignH :'center',
			boundsAlignV : 'middle'
		};

		var bar = this.game.add.graphics();
		bar.beginFill(0xFFFFFF, 0.2);
		bar.drawRect(0, this.game.world.centerY - 80, this.game.world.width, 160);

		var txt = this.game.add.text(this.game.world.centerX, this.game.world.centerY, 'Connecting...', style);
		txt.anchor.set(0.5);
		txt.align = 'center';
		
		this._ctx.pubsub.publish(Signal.IO_CONNECT);
	}
}
