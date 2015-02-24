/**
 * The preloader awaits a list of elements to load from the server. It will
 * retrieve this elements store them in an apropriate way and will display a
 * loading screen to the user.
 */
(function($, Bestia, window) {
	'use strict';

	var Preloader = {

		queue : new window.createjs.LoadQueue(),

		loadFiles : function(files, handler) {
			// Show the loading screen.
			$.publish('io.preloader.onload', {});

			Preloader.queue.loadManifest(files);
		},

		handleError : function(event) {
			console.error('Could not load file: ' + event.data.src);
		},

		handleComplete : function(event) {
			$.publish('io.preloader.onloadfinished', {});
			
		},

		handleProgress : function(event) {
			var perc = Math.ceil(event.loaded * 100 / event.total);
			$.publish('io.preloader.onprogress', {percent : perc});
			
		},

		init : function() {
			Preloader.queue.on("error", Preloader.handleError, Preloader);
			Preloader.queue.on("complete", Preloader.handleComplete, Preloader);
			Preloader.queue.on("progress", Preloader.handleProgress, Preloader);
		},

		handleCommand : function(manifest, id) {
			Preloader.loadFiles(manifest);
		}
	};

	var getResult = function(id) {
		return Preloader.queue.getResult(id, true);
	};
	
	Bestia.io = Bestia.io | {};
	Bestia.io.Preloader = {};
	//Bestia.io.Preloader.getResult = getResult;

	Preloader.init();

	// Register at the mediator.
	$.subscribe('io.preloader.load', Preloader.handleCommand);

})(jQuery, Bestia, this);