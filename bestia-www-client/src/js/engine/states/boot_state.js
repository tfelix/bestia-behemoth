/**
 * First and primary state. It will load all the absolutely necessairy assets to
 * bootstrap the engine.
 * 
 * @constructor
 * @class Bestia.Engine.States.BootState
 */
Bestia.Engine.States.BootState = function() {
	// no op.
};

Bestia.Engine.States.BootState.prototype = {
	preload : function() {
		this.load.image('logo', 'assets/img/logo_small.png');
	},

	create : function() {
		this.logo = this.add.sprite(300, 300, 'logo');
		this.logo.anchor.setTo(0.5, 0.5);
		var tween = this.game.add.tween(this.logo.scale).to({
			x : 3,
			y : 3
		}, 2000, Phaser.Easing.Linear.None);
		tween.start();

		this.game.stateTransition = this.game.plugins.add(Phaser.Plugin.StateTransition);

		this.game.stateTransition.configure({
			duration : Phaser.Timer.SECOND * 0.8,
			ease : Phaser.Easing.Exponential.InOut,
			properties : {
				alpha : 0,
				scale : {
					x : 1.4,
					y : 1.4
				}
			}
		});
	}
};

Bestia.Engine.States.BootState.prototype.constructor = Bestia.Engine.States.BootState;
