describe("Bestia.Inventory.EquipItemInfoViewModel", function() {
	
	var equipItemInfo;
	var forger;
	
	beforeEach(function() {
	    equipItemInfo = {
				ulv : 0,
				f : null,
				bb : 0
			};
	    forger = {id: 1337, n: 'Heinz Heino'};
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
	it("Shows correct forger information.", function() {
		var eqii = new Bestia.Inventory.EquipItemInfoViewModel();
		equipItemInfo.f = forger;
		eqii.update(equipItemInfo);
		expect(eqii.bBroken()).toBe(false);
	});*/
});

describe("Bestia.Inventory.ItemViewModel", function() {
	
	var item;
	
	beforeEach(function(){
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
	});
	
	it("Generates correct image url.", function() {
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.type().toBe('http://localhost/assets/img/items/item.png'));
	});
	
	it("Shows correct type (etc).", function() {
		item.t = 0;
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.type()).toBe('etc');
	});
	
	it("Shows correct type (usable).", function() {
		item.t = 1;
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.type()).toBe('usable');
	});
	
	it("Shows correct type (equip).", function() {
		item.t = 2;
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.type()).toBe('equip');
	});
	
	it("EquipItemInfo is updated.", function() {
		item.eqii = {ulv: 4, f: null, bb: 1};
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.equipItemInfo.upgradeLevel()).toBe('+4');
		expect(vm.equipItemInfo.bBroken()).toBe(true);
	});
	
	/*
	it("Shows translated name.", function() {
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.name()).toBe('DEUTSCH');
	});
	
	it("Shows translated description.", function() {
		var vm = new Bestia.Inventory.ItemViewModel(item);
		expect(vm.description()).toBe('DEUTSCH');
	});*/
});