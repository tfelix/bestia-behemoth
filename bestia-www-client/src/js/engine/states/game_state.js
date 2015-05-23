/**
 * Central game state for controlling the games logic.
 * 
 * @constructor
 * @class Bestia.Engine.States.GameState
 */
Bestia.Engine.States.GameState = function() {
	this.marker = null;
	this.groundLayer = null;
	this.map = null;

	/**
	 * Holds AStar plugin reference to calculate paths of the bestias when
	 * clicked by the user. The other bestias are controlled by the server. But
	 * user movement will be controlled by the client.
	 * 
	 * @private
	 * @property
	 */
	this.astar = null;

	this.config = {
		mapNameStyle : {
			font : "65px Arial",
			fill : "#ff0044",
			align : "center"
		},
		tileSize : 32,
		debug : {
			renderCollision : true
		}
	};
};

Bestia.Engine.States.GameState.prototype = {

	preload : function() {
		// TODO Sollte ausgelagert werden in den Loading State.
		this.load.image('logo', 'assets/img/logo_small.png');
		this.load.tilemap('map', 'assets/map/test-zone1/test-zone1.json', null, Phaser.Tilemap.TILED_JSON);
		this.load.image('tiles', 'assets/map/test-zone1/tilemap1.png');

		// Sprites.
		this.load.image('1_F_ORIENT_01', 'assets/sprite/1_F_ORIENT_01.png');
		this.load.image('1_M_BARD', 'assets/sprite/1_M_BARD.png');

		this.load.audio('bg_theme', 'assets/sound/theme/prontera_fields.mp3');

	},

	create : function() {

		this.gfxCollision = this.add.graphics(0, 0);
		this.gfxCollision.beginFill(0xFF0000, 0.5);

		var map = this.game.add.tilemap('map');
		this.map = map;

		// Extract map properties.
		var props = map.properties;
		props.isPVP = (props.isPVP === "true");

		map.addTilesetImage('Berge', 'tiles');
		// Ground layer MUST be present.
		this.groundLayer = map.createLayer('layer_0');
		this.groundLayer.resizeWorld();

		// Now check how many layer there are and then create them.
		// Get the names of all layer.
		var layerNames = map.layers.map(function(x) {
			return x.name;
		});
		for (var i = 1; i < map.layers.length; i++) {
			var curLayer = 'layer_'+i;
			if(layerNames.indexOf(curLayer) === -1) {
				continue;
			}
			map.createLayer(curLayer);
		}

		// Prepare the AStar plugin.
		// this.astar = this.game.plugins.add(Phaser.Plugin.AStar);
		// this.astar.setAStarMap(map, 'maze', 'claytus');

		// Our painting marker
		this.marker = this.game.add.graphics();
		this.marker.lineStyle(2, 0xffffff, 1);
		this.marker.drawRect(0, 0, 32, 32);

		this.game.input.addMoveCallback(this.updateMarker, this);

		// Music.
		// this.game.add.audio('bg_theme').play();

		// Single Sprites
		this.game.add.sprite(160, 320, '1_F_ORIENT_01');
		this.game.add.sprite(320, 320, '1_M_BARD');

		// Text test.
		var style = {
			font : "18px Arial",
			fill : "#ffffff",
			align : "center",
			stroke : '#000000',
			strokeThickness : 3
		};

		var text = this.game.add.text(500, 600, "123", style);
		var tween = this.game.add.tween(text).to({
			x : [ 475, 450 ],
			y : [ 510, 615 ]
		}, 1000);
		tween.interpolation(function(v, k) {
			return Phaser.Math.bezierInterpolation(v, k);
		});
		tween.repeat(Infinity);
		this.game.add.tween(text).to({
			alpha : 0
		}, 100, Phaser.Easing.Linear.None, true, 900).start();
		tween.start();

		// ========== DISPLAY MAP NAME ==============
		var mapName = i18n.t('map.' + props.mapDbName);
		text = this.game.add.text(this.game._width / 2, this.game._height / 2 - 100, mapName);
		text.align = 'center';
		text.anchor.setTo(0.5);
		// Font style
		text.font = 'Arial';
		text.fontSize = 50;
		text.fontWeight = 'bold';

		// Stroke color and thickness
		text.stroke = '#525252';
		text.strokeThickness = 4;
		if (props.isPVP) {
			text.fill = '#D9B525';
		} else {
			text.fill = '#2ED925';
		}
		text.alpha = 0;

		this.game.add.tween(text).to({
			alpha : 1
		}, 2000, Phaser.Easing.Linear.None, false, 1000).to({
			alpha : 0
		}, 2000, Phaser.Easing.Linear.None, false, 2500).start();

	},

	render : function() {

	},

	updateMarker : function() {

		this.marker.x = this.groundLayer.getTileX(this.game.input.activePointer.worldX) * 32;
		this.marker.y = this.groundLayer.getTileY(this.game.input.activePointer.worldY) * 32;

	},

	getTilePos : function(x) {
		return Math.floor(x / this.config.tileSize);
	},

	renderCollisions : function() {

		// Loop over all visible tiles and check if they are walkable. if not
		// render a block.
		var x = 0;
		var y = 0;
		this.gfxCollision.drawRect(x, y, this.config.tileSize, this.config.tileSize);
	}
};

Bestia.Engine.States.GameState.prototype.constructor = Bestia.Engine.States.GameState;