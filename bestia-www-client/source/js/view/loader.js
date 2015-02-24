/**
 * This UI module is responsible for showing the overlay and displaying a
 * loading bar to the player. It will listen to the following broadcast:
 * /view/loader the arguments should be an object telling the module what to do: {
 * cmd: show | hide, percent: 0-100 }
 * 
 * 
 * @param $
 * @param App
 * @param window
 */
(function($, App, window) {
	'use strict';

	var Loader = {

		isSplashDisplayed : true,	
		
		show : function() {
			Loader.hideSplashscreen();
			$('#overlay').show();
			$('body').addClass('noscroll');
		},

		hide : function() {
			$('overlay').hide();
			$('body').removeClass('noscroll');
		},
		
		displayPercent : function(perc) {
			Loader.hideSplashscreen();
			var width = $('.loading-bar-wrap').width();
			var prcWidth = Math.ceil(width * perc / 100);
			$('.loading-bar').css('width', prcWidth);
			
			if(perc == 100) {
				Loader.hide();
			}
		},

		centerLoading : function() {
			var div = $('#loading');
			var width = $('#loading-content img').width();
			var height = $('#loading-content img').height();
			div.css('margin-left', -1 * width / 2);
			div.css('margin-top', -1 * height / 2);
		},
		
		hideSplashscreen : function(img) {
			if(!Loader.isSplashDisplayed) {
				return;
			}
			Loader.isSplashDisplayed = false;
			$('#loading-content').fadeOut('slow');
		},

		hide : function() {
			$('#loading').hide();
			$('#overlay').fadeOut();
		},

		handleCommand : function(cmd) {
			if(!cmd.cmd) {
				return;
			}
			
			if (cmd.cmd == 'show') {
				Loader.show();
			} else if(cmd.cmd == 'percent') {
				Loader.displayPercent(cmd.percent);
			} else {
				Loader.hide();
			}
		}

	};

	// Subscribe to the mediator.
	$.subscribe('io.preloader.onload', Loader.handleCommand);

})(jQuery, Bestia, this);
