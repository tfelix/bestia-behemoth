'use strict';

/**
 * This javascript object will use the preloader to get an initial set of data
 * from the server which is important for the app to work. This migh be loading
 * screen, important news data to display etc.
 * 
 * Dependencies: connection, preloader
 */


$(document).ready(function(){

	// Load 
	var screens = [{id: 'loading_screen_1', src: 'img/loadingscreen/ls1.jpg'},
			       {id: 'loading_screen_1', src: 'img/loadingscreen/ls2.jpg'},
			       {id: 'loading_screen_2', src: 'img/loadingscreen/ls3.jpg'}];
	
	Bestia.io.Connection.init();
	
	// Simulate the server communication.
	var serverInfo = {
		z: ['test-1', 'test-2'],
		v: '1.0.0-ALPHA1-build1234',
		cp: 3,
		res: 'http://localhost/resource'
	};
	
	$.publish('server.info', serverInfo);
	
	// Server sendet welche Bestia selektiert wurde (Bestia master)
	var bs = {
		pbid: 1337,
		s: 'doommaster.png',
		eq: [1,4,6,2,4,-1],
		mid: 123,
		loc: {x: 4, y: 15}
	};
	$.publish('bestia.select', bs);
	
	
	// Server weißt den Client an eine Map zu laden.
	var mapLoad = {
			mid: 123,
			tms: ['tilemap1.png', 'tilemap2.png'],
			pm: 0,
			name: 'Hello World Map'
	};
	
	$.publish('map.load', mapLoad);
	
});