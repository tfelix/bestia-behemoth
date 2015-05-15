

var States = {
	loading : {
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
			tween.onComplete.addOnce(function() {
				this.game.state.start('game');
			}, this);
			tween.start();
		},

		update : function() {
		}
	},

	game : {
		marker : null,
		groundLayer : null,

		preload : function() {
			this.load.image('logo', 'assets/img/logo_small.png');
			this.load.tilemap('map', 'assets/maps/test-zone1/test-zone1.json', null, Phaser.Tilemap.TILED_JSON);
			this.load.image('tiles', 'assets/maps/test-zone1/tilemap1.png');
		},

		create : function() {
			var map = this.game.add.tilemap('map');
			map.addTilesetImage('Berge', 'tiles');
			this.groundLayer = map.createLayer('Boden');
			this.groundLayer.resizeWorld();
			map.createLayer('Berge');

			// Our painting marker
			this.marker = this.game.add.graphics();
			this.marker.lineStyle(2, 0xffffff, 1);
			this.marker.drawRect(0, 0, 32, 32);
			
			this.game.input.addMoveCallback(this.updateMarker, this);
		},

		update : function() {
			
		},

		updateMarker : function() {

			this.marker.x = this.groundLayer.getTileX(this.game.input.activePointer.worldX) * 32;
		    this.marker.y = this.groundLayer.getTileY(this.game.input.activePointer.worldY) * 32;

		}
	}
};

Bestia.Engine = function() {

	 this.info = {};
	 this.info.fps = ko.observable(0);
	 this.info.fps.extend({rateLimit: 1000});
	 

	function preload() {
	}

	function create() {
	}

	function update() {

		// Update Ticker.
		// info.fps(game.time.fps);
	}

	// Determine the size of the canvas.

	var game = new Phaser.Game(800, 600, Phaser.AUTO, 'bestia-canvas', {
		preload : preload,
		create : create,
		update : update
	});

	game.state.add('loading', States.loading);
	game.state.add('game', States.game);

	game.state.start('loading');
};
