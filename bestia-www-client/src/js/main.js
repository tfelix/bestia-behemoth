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