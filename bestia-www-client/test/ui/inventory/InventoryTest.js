describe("Bestia.Inventory", function() {
	
	var pubsub = null;
	var i18n = null;
	var urlHelper = null;

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
		pis : [ {
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

	var translationMsg = {
		mid : "translation.response",
		t : null,
		is : [ {
			c : 'ITEM',
			k : 'apple',
			v : 'Apfel'
		}, {
			c : 'ITEM',
			k : 'apple_desc',
			v : 'Ein Apfel.'
		}, {
			c : 'ITEM',
			k : 'empty_bottle',
			v : 'Leere Flasche'
		}, {
			c : 'ITEM',
			k : 'emtpy_bottle_desc',
			v : 'Ene leere Flasche.'
		} ]
	};

	beforeEach(function() {
		pubsub = new Bestia.PubSub();
		// Catch the token from the request.
		pubsub.subscribe('io.sendMessage', function(_, data){
			translationMsg.t = data.t;
		});
		
		i18n = new Bestia.I18n(pubsub);
		urlHelper = new Bestia.UrlHelper('http://localhost/assets');
	});

	it("Does listen to inventory list messages.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n, urlHelper);
		pubsub.publish(listMsg.mid, listMsg);

		expect(inv.items().length).toEqual(2);
	});

	/*
	it("Does listen to inventory update messages.", function() {
		// TODO Problem liegt bei i18n wenn antwortende Kategorie klein ist. Irgendwas mit den cases stimmt nicht.
		
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		pubsub.publish(updateMsg.mid, updateMsg);

		expect(inv.items().length).toEqual(1);
		expect(inv.items()[0].itemDatabaseName()).toEqual('apple');
	});*/

	it("Calculate weight correctly.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n, urlHelper);
		pubsub.publish(listMsg.mid, listMsg);
		expect(inv.currentWeight()).toEqual(140);
	});

	/*
	 * it("Does translate items.", function() {
	 * 
	 * });
	 * 
	 * it("Does send item uses when clicked on usable item.", function() {
	 * 
	 * });
	 * 
	 * it("Does not send item uses when clicked on non usable item.", function() {
	 * 
	 * });
	 */

	it("Does filter for category.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n, urlHelper);
		pubsub.publish(listMsg.mid, listMsg);

		inv.categoryFilter('etc');

		expect(inv.items()[0].itemDatabaseName()).toEqual('empty_bottle');
	});

	/*
	it("does filter text.", function() {
		var inv = new Bestia.Inventory(pubsub, i18n);
		pubsub.publish(listMsg.mid, listMsg);
		pubsub.publish(translationMsg.mid, translationMsg);

		inv.searchFilter('apfe');

		expect(inv.items().length).toEqual(1);
	});*/
});
