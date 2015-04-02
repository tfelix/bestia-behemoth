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
	
	// Connection created. Bootstrap server information.
	/*$.subscribe('io.onConnected', function(){
		Bestia.io.Connection.sendMessage(new Bestia.message.ServerInfo());
		Bestia.io.Connection.sendMessage(new Bestia.message.BestiaInfo());
	});*/
	
	//Bestia.io.Connection.init();
	
	
	// Simulate the server communication.
	var serverInfo = {
		z: ['test-1', 'test-2'],
		v: '1.0.0-ALPHA1-build1234',
		cp: 3,
		res: 'http://localhost:8080/resource'
	};
	
	$.publish('server.info', serverInfo);
	
	// Server sendet welche Bestia selektiert wurde (Bestia master)
	var bm = {
		pbid: 1337,
		s: 'doommaster.png',
		loc: {mid: 14, x: 4, y: 15, mn: 'Doomyland'},
		spO: {atk: 20, def: 120, spatk: 123, spdef: 234, arm: 12, sparm: 6, spd: 5, cMana: 12, mMana: 45, cHp: 45, mHp: 56},
		sp: {atk: 20, def: 120, spatk: 123, spdef: 234, arm: 12, sparm: 6, spd: 5, cMana: 12, mMana: 45, cHp: 45, mHp: 56},
		cn: 'Blubber',
		bdbn: 'Doommaster of Doom',
		se: [],
		img: 'dommaster.png'
	};
	var bm1 = {
			pbid: 1338,
			s: 'blob.png',
			loc: {mid: 14, x: 6, y: 10, mn: 'Doomyland'},
			spO: {atk: 20, def: 120, spatk: 123, spdef: 234, arm: 12, sparm: 10, spd: 5, cMana: 12, mMana: 45, cHp: 45, mHp: 56},
			sp: {atk: 20, def: 120, spatk: 123, spdef: 234, arm: 12, sparm: 6, spd: 5, cMana: 12, mMana: 45, cHp: 45, mHp: 56},
			cn: 'Bla bla bla',
			bdbn: 'Blob',
			se: [],
			img: 'blob.png'
		};
	
	var bestiaInfo = {
		mid: 'bestia.info',
		bm: bm,
		b: [bm1],
		s: 4
	}
	$.publish('bestia.info', bestiaInfo);
	
	/*
	var item = {
		iid: 12, // item id
		pid: 15, // player_item_id
		img: 'item.jpg',
		t: 0, // 0: equip, 1: usable, 2: equip
		bq: 0, // bQuestitem
		bs: 0, // bSoulbound
		eqii: {ulv: 0, f: null, bb: 0}, // Equip item info. upgrade_level, forger: , b_broken
		eqpi: {}, // todo
		a: 1, //amount
		name: 'Blubber'	
	};*/
	
	
	var mapPreload = {
		mid: 'map.preload',
		mpid: 123, // map id
		mdbn: 'meine_map', // tiled map datei.
		tms: ['tilemap1.png, tilemap2.png'], // tile maps
		esp: ['doommaster', 'blob'], // entity sprites, Sprites aller aktueller auf der Map befindlicher Bestia und Entities.
		s: ['sound1', 'sound2'] // Sounds.
	};
	
	
	// Server weißt den Client an eine Map zu laden.
	var mapLoad = {
			mid: 123,
			tms: ['tilemap1.png', 'tilemap2.png'],
			pm: 0,
			name: 'Hello World Map'
	};
	
	$.publish('map.load', mapLoad);
	
});