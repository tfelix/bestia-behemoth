this.Bestia = this.Bestia || {};
/**
 * Main message module. Responsible for sending messages to the server and to
 * receive them and rebroadcast them into the client.
 * 
 * @module Bestia.Engine
 */
(function(app, $, createjs, ko) {

	'use strict';

	var config = {
		tileSize : 32
	};

	function DebugView() {
		var self = this;
		
		self.fps = ko.observable(0);
		self.entities = ko.observable(0);
		self.showHeat = ko.observable(false);
		self.showHumidity = ko.observable(false);
		
		self.lastTick = 0;
		self.frameCounter = 0;
		
		
		self.tickFPS = function() {
			self.frameCounter++;
			
			if (Date.now() - self.lastTick < 1000) {
				return;
			}

			self.fps(self.frameCounter);
			self.frameCounter = 0;
			self.lastTick = Date.now();
		}
	}
	

	function MousePointer(stage, displObject) {
		var that = this;
		this.pointer = displObject;
		stage.getChildByName('gui').addChild(displObject);
		stage.on('stagemousemove', function(e) {
			that.mouseMoveHandler(e);
		});
	}
	var p = MousePointer.prototype;

	p.mouseMoveHandler = function(event) {
		var tileX = Math.floor(event.stageX / config.tileSize);
		var tileY = Math.floor(event.stageY / config.tileSize);

		this.pointer.x = tileX * config.tileSize;
		this.pointer.y = tileY * config.tileSize;
	};

	app.Engine = {

		debugView : new DebugView(),
		canvas : null,
		stage : null,
		isDebug : true,
		tileContainer : null,

		/**
		 * Renders one layer to the canvas.
		 */
		renderLayer : function(layerData, tilesetSheet, tilewidth, tileheight) {
			for (var y = 0; y < layerData.height; y++) {
				for (var x = 0; x < layerData.width; x++) {
					// create a new Bitmap for each cell
					var cellBitmap = new createjs.Sprite(tilesetSheet);
					// layer data has single dimension array
					var idx = x + y * layerData.width;
					// tilemap data uses 1 as first value, EaselJS uses 0 (sub 1
					// to load correct tile)
					cellBitmap.gotoAndStop(layerData.data[idx] - 1);
					// isometrix tile positioning based on X Y order from Tiled
					cellBitmap.x = x * tilewidth;
					cellBitmap.y = y * tileheight;
					// add bitmap to stage
					app.Engine.tileContainer.addChild(cellBitmap);
				}
			}
			// Cache the tiles.
			//app.Engine.tileContainer.cache(0, 0, layerData.width * tilewidth, layerData.height * tileheight);
		},

		/**
		 * Orders the engine to load a certain map.
		 */
		onLoadHandler : function(_, msg) {

			// Pause the renderer.

			// Show loading screen.

			// Gather all needed information about the new map.
			var files = [];
			var mapUrl = Bestia.server.Config.resourceURL() + '/maps/' + msg.mdbn + '/' + msg.mdbn + '.json';

			var test = [ {
				id : "map",
				src : mapUrl
			}, {
				id : msg.tms[0],
				src : Bestia.server.Config.resourceURL() + '/maps/' + msg.mdbn + '/' + msg.tms[0]
			} ];

			$.publish('io.preloader.load', {
				result : 'map',
				files : test
			});
		},

		onLoadFinishedHandler : function(_, event) {

			// A map was loaded. It is accessable under its ID get it
			// and start the loading of its tilemaps.
			var map = Bestia.io.Preloader.queue.getResult('map');

			// compose EaselJS tileset from image (fixed 64x64 now, but can be
			// parametized)
			var w = map.tilesets[0].tilewidth;
			var h = map.tilesets[0].tileheight;

			console.debug('Loaded mapfile.  [' + w + ',' + h + ']');

			var imageData = {
				images : [ event.files[1].src ],
				frames : {
					width : w,
					height : h
				}
			};
			// create spritesheet
			var tilesetSheet = new createjs.SpriteSheet(imageData);

			// loading each layer at a time
			for (var idx = 0; idx < map.layers.length; idx++) {
				var layerData = map.layers[idx];
				if (layerData.type == 'tilelayer') {
					app.Engine.renderLayer(layerData, tilesetSheet, map.tilewidth, map.tileheight);
				}
			}

			app.Engine.startRender();

		},

		/**
		 * Setup the engine.
		 */
		init : function() {
			app.Engine.canvas = $('#bestia-canvas');
			app.Engine.stage = new createjs.Stage("bestia-canvas");

			// Resize canvas to max. screen width.
			resizeCanvasHandler();

			// Setup the different layer to draw onto. The tile stage e.g.
			app.Engine.tileContainer = new createjs.Container();
			app.Engine.tileContainer.name = 'tiles';
			app.Engine.stage.addChild(app.Engine.tileContainer);
			app.Engine.stage.setChildIndex(app.Engine.tileContainer, 0);

			// Container for GUI elements. Will be drawn upon everything else.
			var guiContainer = new createjs.Container();
			guiContainer.name = 'gui';
			app.Engine.stage.addChild(guiContainer);
			app.Engine.stage.setChildIndex(guiContainer, 1);

			var cursor = new createjs.Shape();
			cursor.graphics.beginFill("#72EB46").drawRect(0, 0, config.tileSize, config.tileSize);
			cursor.alpha = 0.65;
			var pointer = new MousePointer(app.Engine.stage, cursor);
			
			createjs.Tween.get(cursor, {loop: true}).to({alpha: 0.65}, 1000).to({alpha: 0.35}, 1000);

			createjs.Ticker.timingMode = createjs.Ticker.RAF;
			createjs.Ticker.setFPS(60);
		},

		/**
		 * Stops the render loop.
		 */
		stop : function() {
			createjs.Ticker.removeEventListener("tick", app.Engine.renderTick);
		},

		startRender : function() {
			createjs.Ticker.addEventListener("tick", app.Engine.renderTick);
		},

		/**
		 * Main render loop.
		 */
		renderTick : function(event) {
			// Get the gathered data.

			// Render the tile map if it has changed cache it.

			// render entities.

			// render brightness

			// render particles

			// render lights

			// render debug infos.
			app.Engine.renderDebug(event);

			app.Engine.stage.update(event);
		},

		renderDebug : function(event) {
			if (!app.Engine.isDebug) {
				return;
			}

			app.Engine.debugView.tickFPS();
		}
	}

	$.subscribe('map.load', app.Engine.onLoadHandler);

	/**
	 * Resizes the canvas to the screen size.
	 */
	function resizeCanvasHandler() {
		var height = $(document).height();
		var width = $('#canvas-container').width();

		app.Engine.canvas.attr('width', width);
		app.Engine.canvas.attr('height', height);
	}

	// Subscribe to all needed message listener.
	$.subscribe('io.preloader.onloadfinished', app.Engine.onLoadFinishedHandler);

	$(document).ready(function() {
		app.Engine.init();
		ko.applyBindings(app.Engine.debugView, $('#canvas-debug').get(0));
	});

	// Resize the canvas to the windows size on window resize events.
	$(window).resize(app.Engine.resizeWindowHandler);

})(Bestia, jQuery, createjs, ko);