/**
 * This javascript object will use the preloader to get an initial set of data
 * from the server which is important for the app to work. This migh be loading
 * screen, important news data to display etc.
 * 
 * Dependencies: connection, preloader
 */

$(document).ready(function() {
	'use strict';

	// Connection created. Bootstrap server information.
	/*
	 * $.subscribe('io.onConnected', function(){
	 * Bestia.io.Connection.sendMessage(new Bestia.message.ServerInfo());
	 * Bestia.io.Connection.sendMessage(new Bestia.message.BestiaInfo()); });
	 */

	// Bestia.io.Connection.init();
	// Simulate the server communication.
	var serverInfo = {
		z : [ 'test-1', 'test-2' ],
		v : '1.0.0-ALPHA1-build1234',
		cp : 3,
		res : 'http://localhost/assets'
	};

	$.publish('server.info', serverInfo);

	// Server sendet welche Bestia selektiert wurde (Bestia master)
	var bm = {
		pbid : 1337,
		s : 'doommaster.png',
		loc : {
			mid : 14,
			x : 4,
			y : 15,
			mn : 'Doomyland'
		},
		spO : {
			atk : 20,
			def : 120,
			spatk : 123,
			spdef : 234,
			arm : 12,
			sparm : 6,
			spd : 5,
			cMana : 12,
			mMana : 45,
			cHp : 45,
			mHp : 56
		},
		sp : {
			atk : 20,
			def : 120,
			spatk : 123,
			spdef : 234,
			arm : 12,
			sparm : 6,
			spd : 5,
			cMana : 12,
			mMana : 45,
			cHp : 45,
			mHp : 56
		},
		cn : 'Blubber',
		bdbn : 'Doommaster of Doom',
		se : [],
		img : 'dommaster.png'
	};
	var bm1 = {
		pbid : 1338,
		s : 'blob.png',
		loc : {
			mid : 14,
			x : 6,
			y : 10,
			mn : 'Doomyland'
		},
		spO : {
			atk : 20,
			def : 120,
			spatk : 123,
			spdef : 234,
			arm : 12,
			sparm : 10,
			spd : 5,
			cMana : 12,
			mMana : 45,
			cHp : 45,
			mHp : 56
		},
		sp : {
			atk : 20,
			def : 120,
			spatk : 123,
			spdef : 234,
			arm : 12,
			sparm : 6,
			spd : 5,
			cMana : 12,
			mMana : 45,
			cHp : 45,
			mHp : 56
		},
		cn : 'Bla bla bla',
		bdbn : 'Blob',
		se : [],
		img : 'blob.png'
	};

	var bestiaInfo = {
		mid : 'bestia.info',
		bm : bm,
		b : [ bm1 ],
		s : 4
	};
	$.publish('bestia.info', bestiaInfo);


	// Add item amount.
	var item1 = {
		iid : 15, // item id
		pid : 17, // player_item_id
		img : 'apfel.jpg',
		t : 1, // 0: etc, 1: usable, 2: equip
		bq : 0, // bQuestitem
		bs : 0, // bSoulbound
		eqii : null, // Equip item info.
		eqpi : {}, // todo
		a : 100 // amount
	};
	$.publish('inventory.add', item1);
	var item2 = {
		iid : 14, // item id
		pid : 17, // player_item_id
		img : 'item.jpg',
		t : 1, // 0: etc, 1: usable, 2: equip
		bq : 0, // bQuestitem
		bs : 0, // bSoulbound
		eqii : null, // Equip item info.
		eqpi : {}, // todo
		a : 13 // amount
	};
	$.publish('inventory.add', item2);

	// Remove certain item amount.
	var itemRemove = {
		pid : 13, // player item id
		a : 1
	// Amount
	};
	$.publish('inventory.remove', itemRemove);

	var mapload = {
		mid : 'map.preload',
		mpid : 123, // map id
		mdbn : 'test-zone1', // tiled map datei.
		tms : [ 'tilemap1.png', 'tilemap2.png' ], // tile maps
		esp : [ 'doommaster', 'blob' ], // entity sprites, Sprites aller
		// aktueller auf der Map befindlicher
		// Bestia und Entities.
		s : [ 'sound1', 'sound2' ]
	// Sounds.
	};

	$.publish('map.load', mapload);

	// TODO Bestia klein schreiben.
	// Create a sample chat.
	//var chat = new Bestia.Chat($('#chat').get(0), 'rocket');

	var msgs = [ {
		mid : 'chat.message',
		m : 'PUBLIC',
		txt : 'Das ist ein Public Chat test.',
		sn : 'rocket'
	}, {
		mid : 'chat.message',
		m : 'PARTY',
		txt : 'Das ist ein Party Chat test.',
		sn : 'rocket'
	}, {
		mid : 'chat.message',
		m : 'GUILD',
		txt : 'Das ist ein Guild Chat test.',
		sn : 'rocket'
	}, {
		mid : 'chat.message',
		m : 'WHISPER',
		txt : 'Das ist ein Whisper Chat test.',
		sn : 'rocket'
	}, {
		mid : 'chat.message',
		m : 'SYSTEM',
		txt : 'Das ist ein System Chat test.',
		sn : ''
	}, {
		mid : 'chat.message',
		m : 'GM_BROADCAST',
		txt : 'Das ist ein GM Chat test.',
		sn : 'rocket'
	}, {
		mid : 'chat.message',
		m : 'ERROR',
		txt : 'Das ist ein Chat test.',
		sn : ''
	}, {
		mid : 'chat.message',
		m : 'COMMAND',
		txt : 'Das ist ein Command Chat test',
		sn : ''
	} ];

	// $.publish('chat.message', {mid: 'chat.message', m: 'PARTY', txt: 'Das ist
	// ein Chat test.', sn: 'rocket'});
	for (var i = 0; i < msgs.length; i++) {
		var msg = msgs[i];
		$.publish('chat.message', msg);
		$.publish('chat.message', msg);
	}
	
	
	// Final code.
	//i18n.init({ lng: "de-DE" });
});