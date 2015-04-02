/**
 * The preloader awaits a list of elements to load from the server. It will
 * retrieve this elements store them in an apropriate way and will display a
 * loading screen to the user.
 */
(function($, Bestia, ko) {
	'use strict';
	var Preloader = {

		View : {
			isLoading : ko.observable(false),
			percent : ko.observable(0),
			currentFile : ko.observable('')
		},

		queue : new window.createjs.LoadQueue(),

		loadFiles : function(files, handler) {
			// Show the loading screen.
			$.publish('io.preloader.onload', {});

			Preloader.queue.loadManifest(files);
		},

		handleError : function(event) {
			console.error('Could not load file: ' + event.data.src);
			Preloader.View.isLoading(false);
		},

		handleComplete : function(event, data) {
			console.log('File loading completed: ' + event);
			$.publish('io.preloader.onloadfinished', data);
			Preloader.View.isLoading(false);
		},

		handleProgress : function(event) {
			var perc = Math.ceil(event.loaded * 100 / event.total);
			Preloader.View.percent(perc);
		},

		handleFilestart : function(event) {
			Preloader.View.currentFile(event.item);
		},

		init : function() {
			Preloader.queue.on("error", Preloader.handleError, Preloader);
			Preloader.queue.on("filestart", Preloader.handleFilestart, Preloader);
			Preloader.queue.on("progress", Preloader.handleProgress, Preloader);
		},

		handleCommand : function(_, data) {
			Preloader.View.percent(0);
			Preloader.View.isLoading(true);

			Preloader.queue.on('complete', Preloader.handleComplete, Preloader, true, data);

			Preloader.loadFiles(data.files);
		}
	};

	Bestia.io = Bestia.io || {};
	Bestia.io.Preloader = Preloader;

	Preloader.init();

	// Register at the mediator.
	$.subscribe('io.preloader.load', Preloader.handleCommand);

})(jQuery, Bestia, ko);