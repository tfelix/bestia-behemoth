var BG = {};

function bootstrap() {
	// Bootstrap the behemoth.
	BG.config = new Bestia.Config();
	BG.net = new Bestia.Net(BG.config);
	BG.inventory = new Bestia.Inventory(BG.net);
	BG.bestias = new Bestia.BestiaInfoViewModel(BG.net);	
	BG.chat = new Bestia.Chat($('#chat'), BG);
	BG.engine = new Bestia.Engine(BG.config);
	BG.connection = new Bestia.Connection();
	
	BG.connection.init();

	// UI init must wait until dom is loaded and accessible.
	$(document).ready(function() {
		BG.page = {
			logoutDialog : new Bestia.Page.LogoutDialog('#modal-logout')
		};
		
		// Bind the DOM to the game.
		ko.applyBindings(BG);
		
		//$('#modal-inventory').modal('show');
	});

}

function simulate() {


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
		//Bestia.publish('chat.message', msg);
		//Bestia.publish('chat.message', msg);
		//Bestia.publish('chat.message', msg);
	}
}

// Final code.
i18n.init({
	lng : "de",
	fallbackLng : false
}, function() {
	$('body').i18n();

	// Setup the game object.
	bootstrap();
	simulate();
});