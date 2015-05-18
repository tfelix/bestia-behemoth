var Game = {};

$(document).ready(function() {
	'use strict';

	// Bootstrap the behemoth.

	var config = new Bestia.Config();
	var net = new Bestia.Net(config);
	var inventory = new Bestia.Inventory(net);
	var bestias = new Bestia.BestiaInfoViewModel(net);
	var chat = new Bestia.Chat(Game, 'rocket');
	var engine = new Bestia.Engine();

	Game.config = config;
	Game.net = net;
	Game.inventory = inventory;
	Game.bestias = bestias;
	Game.chat = chat;
	Game.engine = engine;

	// Bind the DOM to the game.
	ko.applyBindings(Game);

	// Simulate the server communication.
	var serverInfo = {
		z : [ 'test-1', 'test-2' ],
		v : '1.0.0-ALPHA1-build1234',
		cp : 3,
		sn : 'Test-Zone',
		res : 'http://localhost/assets'
	};

	Bestia.publish('server.info', serverInfo);

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
		sl : 0,
		cn : 'Blubber',
		bdbn : 'doom_master',
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
		sl : 1,
		cn : 'Bla bla bla',
		bdbn : 'blob',
		se : [],
		img : 'blob.png'
	};

	var bestiaInfo = {
		mid : 'bestia.init',
		bm : bm,
		b : [ bm1, bm ],
		s : 4
	};
	Bestia.publish('bestia.init', bestiaInfo);

	window.setTimeout(function() {
		var bm = {
			pbid : 1337,
			s : 'doommaster.png',
			loc : {
				mid : 20,
				x : 10,
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
				cMana : 45,
				mMana : 45,
				cHp : 56,
				mHp : 56
			},
			sl : 0,
			cn : 'Blubber',
			bdbn : 'doom_master',
			se : [],
			img : 'dommaster.png'
		};
		Bestia.publish('bestia.update', bm);
	}, 3000);

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
		a : 100
	// amount
	};
	Bestia.publish('inventory.add', item1);
	var item2 = {
		iid : 14, // item id
		pid : 17, // player_item_id
		img : 'item.jpg',
		t : 1, // 0: etc, 1: usable, 2: equip
		bq : 0, // bQuestitem
		bs : 0, // bSoulbound
		eqii : null, // Equip item info.
		eqpi : {}, // todo
		a : 13
	// amount
	};
	Bestia.publish('inventory.add', item2);

	// Remove certain item amount.
	var itemRemove = {
		pid : 13, // player item id
		a : 1
	// Amount
	};
	Bestia.publish('inventory.remove', itemRemove);

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

	Bestia.publish('map.load', mapload);
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

	for (var i = 0; i < msgs.length; i++) {
		var msg = msgs[i];
		Bestia.publish('chat.message', msg);
		Bestia.publish('chat.message', msg);
	}

	// Final code.
	i18n.init({
		lng : "de",
		fallbackLng : false
	}, function() {
		$('#trans-test').i18n();
		$('#chat').i18n();
	});
});