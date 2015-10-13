describe("Bestia.Inventory", function() {

	var listMsg = {
		mid : "inventory.list",
		pis : [ {
			pid : 1,
			i : {
				id : 1,
				w : 2,
				idbn : "apple",
				img : "apple.png",
				t : "USABLE"
			},
			a : 5
		}, {
			pid : 2,
			i : {
				id : 2,
				w : 1,
				idbn : "empty_bottle",
				img : "empty_bottle.png",
				t : "ETC"
			},
			a : 130
		} ],
		mw : 0
	};
	
	var updateMsg = {
			mid : "inventory.list",
			pis : [{
				pid : 2,
				i : {
					id : 2,
					w : 1,
					idbn : "empty_bottle",
					img : "empty_bottle.png",
					t : "ETC"
				},
				a : -130
			} ],
			mw : 120
		};

	var pubsub = new Bestia.PubSub();
	var i18n = new Bestia.I18n(pubsub);

	beforeEach(function() {
		pubsub = new Bestia.PubSub();
		i18n = new Bestia.I18n(pubsub);
	});

	it("Does listen to inventory list messages.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		
		expect(inv.items().length).toEqual(2);
	});

	it("Does listen to inventory update messages.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		pubsub.publish(updateMsg.mid, updateMsg);
		
		expect(inv.items().length).toEqual(1);
		expect(inv.items()[0].itemDatabaseName()).toEqual('apple');
	});
	
	it("Calculate weight correctly.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		expect(inv.currentWeight()).toEqual(140);
	});

	it("Does translate items.", function() {

	});

	it("Does send item uses when clicked on usable item.", function() {

	});

	it("Does not send item uses when clicked on non usable item.", function() {

	});

	it("Does filter for category.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		
		inv.categoryFilter('etc');
		
		expect(inv.items()[0].itemDatabaseName()).toEqual('empty_bottle');
	});
	
	it("Does filter text.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		
		inv.searchFilter('appl');
		
		expect(inv.items().length).toEqual(1);
	});
});
