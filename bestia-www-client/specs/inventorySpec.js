describe("Bestia.Inventory.EquipItemInfoViewModel", function() {

	var equipItemInfo;
	var forger;

	beforeEach(function() {
		equipItemInfo = {
			ulv : 0,
			f : null,
			bb : 0
		};
		forger = {
			id : 1337,
			n : 'Heinz Heino'
		};
	});

	it("Shows upgrade level like ''.", function() {
		var eqii = new Bestia.Inventory.EquipItemInfoViewModel();
		equipItemInfo.ulv = 0;
		eqii.update(equipItemInfo);
		expect(eqii.upgradeLevel()).toBe('');
	});

	it("Shows upgrade level like +5.", function() {
		var eqii = new Bestia.Inventory.EquipItemInfoViewModel();
		equipItemInfo.ulv = 5;
		eqii.update(equipItemInfo);
		expect(eqii.upgradeLevel()).toBe('+5');
	});

	it("Equipment not broken.", function() {
		var eqii = new Bestia.Inventory.EquipItemInfoViewModel();
		eqii.update(equipItemInfo);
		expect(eqii.bBroken()).toBe(false);
	});

	/*
	 * it("Shows correct forger information.", function() { var eqii = new
	 * Bestia.Inventory.EquipItemInfoViewModel(); equipItemInfo.f = forger;
	 * eqii.update(equipItemInfo); expect(eqii.bBroken()).toBe(false); });
	 */
});

describe("Bestia.Inventory.ItemViewModel", function() {

	var item;
	var net = new Bestia.Net(new Bestia.Config());
	net.getItemImageUrl = function(name) {
		return 'http://localhost/assets/img/items/' + name;
	};

	beforeEach(function() {
		item = {
			iid : 12, // item id
			pid : 15, // player_item_id
			img : 'item.png',
			t : 0, // 0: etc, 1: usable, 2: equip
			bq : 0, // bQuestitem
			bs : 0, // bSoulbound
			eqii : { // Equip item info. upgrade_level, forger: , b_broken
				ulv : 0,
				f : null,
				bb : 0
			},
			a : 10, // amount
			name : 'Apfel'
		};

		spyOn(net, 'getItemImageUrl').and.callThrough();
		;
	});

	it("Throws an error on no Bestia.Net instance.", function() {
		expect(function() {
			new Bestia.Inventory.ItemViewModel(null);
		}).toThrow();
	});

	it("Generates correct image url.", function() {
		var vm = new Bestia.Inventory.ItemViewModel(net, item);
		expect(vm.imageURL()).toBe('http://localhost/assets/img/items/item.png');
		expect(net.getItemImageUrl).toHaveBeenCalledWith('item.png');
	});

	it("Shows correct type (etc).", function() {
		item.t = 0;
		var vm = new Bestia.Inventory.ItemViewModel(net, item);
		expect(vm.type()).toBe('etc');
	});

	it("Shows correct type (usable).", function() {
		item.t = 1;
		var vm = new Bestia.Inventory.ItemViewModel(net, item);
		expect(vm.type()).toBe('usable');
	});

	it("Shows correct type (equip).", function() {
		item.t = 2;
		var vm = new Bestia.Inventory.ItemViewModel(net, item);
		expect(vm.type()).toBe('equip');
	});

	it("EquipItemInfo is updated.", function() {
		item.eqii = {
			ulv : 4,
			f : null,
			bb : 1
		};
		var vm = new Bestia.Inventory.ItemViewModel(net, item);
		expect(vm.equipItemInfo.upgradeLevel()).toBe('+4');
		expect(vm.equipItemInfo.bBroken()).toBe(true);
	});

	/*
	 * it("Shows translated name.", function() { var vm = new
	 * Bestia.Inventory.ItemViewModel(item); expect(vm.name()).toBe('DEUTSCH');
	 * });
	 * 
	 * it("Shows translated description.", function() { var vm = new
	 * Bestia.Inventory.ItemViewModel(item);
	 * expect(vm.description()).toBe('DEUTSCH'); });
	 */
});

describe("Bestia.Inventory.Inventory", function() {

	var items;
	var net = new Bestia.Net(new Bestia.Config());
	net.getItemImageUrl = function(name) {
		return 'http://localhost/assets/img/items/' + name;
	};

	beforeEach(function() {
		items = [ {
			iid : 12,
			pid : 15,
			img : 'item.jpg',
			t : 0,
			bq : 0,
			bs : 0,
			eqii : {
				ulv : 0,
				f : null,
				bb : 0
			},
			a : 10
		}, {
			iid : 13,
			pid : 16,
			img : 'equip_item.jpg',
			t : 2,
			bq : 0,
			bs : 0,
			eqii : {
				ulv : 4,
				f : {
					id : 123,
					n : 'Heino'
				},
				bb : 0
			},
			a : 1
		}, {
			iid : 14,
			pid : 17,
			img : 'item.jpg',
			t : 1,
			bq : 0,
			bs : 0,
			eqii : null,
			eqpi : {},
			a : 13
		} ];
	});

	it("Deletes an item on the given message.", function() {

	});

	it("Adds an existing (normal) item on the given message.", function() {
		var inv = new Bestia.Inventory.Inventory(net);
		Bestia.PubSub.publish('inventory.init', items);
		var item = {
			iid : 13,
			pid : 16,
			img : 'equip_item.jpg',
			t : 2,
			bq : 0,
			bs : 0,
			eqii : {
				ulv : 4,
				f : {
					id : 123,
					n : 'Heino'
				},
				bb : 0
			},
			a : 1
		};
		expect(inv.items().length).toEqual(3);
		Bestia.PubSub.publish('inventory.add', item);
		expect(inv.items().length).toEqual(4);
		expect(inv._findItem(item.pid).amount()).toEqual(1);
	});

	it("Adds an existing (equip) item on the given message.", function() {
		var inv = new Bestia.Inventory.Inventory(net);
		Bestia.PubSub.publish('inventory.init', items);
		var item = {
			iid : 12,
			pid : 15,
			img : 'item_etc.jpg',
			t : 0,
			bq : 0,
			bs : 0,
			eqii : {
				ulv : 0,
				f : null,
				bb : 0
			},
			a : 10
		};
		expect(inv.items().length).toEqual(3);
		Bestia.PubSub.publish('inventory.add', item);
		expect(inv.items().length).toEqual(3);
		expect(inv._findItem(item.pid).amount()).toEqual(20);
	});

	it("Sends to the server on init.", function() {
		var inv = new Bestia.Inventory.Inventory(net);

		var i = 0;
		Bestia.PubSub.subscribe('io.send', function(_, msg) {
			if (msg.mid == 'inventory.request') {
				i++;
			}
		});
		inv.init();
		expect(i).toEqual(1);
	});

	it("Initializes on a server message.", function() {
		var inv = new Bestia.Inventory.Inventory(net);
		Bestia.PubSub.publish('inventory.init', items);
		expect(inv.items().length).toEqual(3);
	});

});