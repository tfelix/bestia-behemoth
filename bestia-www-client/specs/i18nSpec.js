/**
 * 
 */
describe("Bestia.I18n", function() {

	var pubsub = new Bestia.PubSub();

	var sendToken;
	var sendItems;
	var called;
	var hasSend = false;

	var TRANSLATED = "TRANSLATED!";

	var handler = function(_, data) {
		sendToken = data.t;
		sendItems = data.is;
		hasSend = true;
	};
	pubsub.subscribe('io.sendMessage', handler);

	var sendResponse = function() {
		var msg = {
			t : sendToken,
			is : []
		};

		for (var i = 0; i < sendItems.length; i++) {
			var sendItem = sendItems[i];
			msg.is.push({
				c : sendItem.c,
				k : sendItem.k,
				v : TRANSLATED
			});
		}

		pubsub.publish('translation.response', msg);
	};

	beforeEach(function() {
		called = undefined;
		hasSend = false;
	});

	it("Sends a message to the server if receives unknown single key.",
			function(done) {

				var i18n = new Bestia.I18n(pubsub);

				i18n.t('cat.key', function(t) {
					expect(t()).toEqual(TRANSLATED);
					called = true;
					done();
				});

				sendResponse();

				expect(called).toBe(true);
			});

	it("It sends no message if all keys are already cached.", function(done) {

		var i18n = new Bestia.I18n(pubsub);

		i18n.t('cat.key', function(t) {
			expect(t()).toEqual(TRANSLATED);
		});

		sendResponse();
		
		hasSend = false;

		i18n.t('cat.key', function(t) {
			expect(t()).toEqual(TRANSLATED);
			done();
		});

		expect(hasSend).toBe(false);
	});

	it("It resets cache if language changes.", function(done) {

		var i18n = new Bestia.I18n(pubsub);

		i18n.t('cat.key', function(t) {
			expect(t('cat.key')).toEqual(TRANSLATED);
		});

		sendResponse();
		
		hasSend = false;

		pubsub.publish('i18n.lang', 'de');

		i18n.t('cat.key', function(t) {
			expect(t('cat.key')).toEqual(TRANSLATED);
			done();
		});

		expect(hasSend).toBe(true);
	});
	
	/*
	it("It can translate multiple items and access them via callback.", function() {

		var i18n = new Bestia.I18n(pubsub);

		i18n.t('cat.key', function(t) {
			expect(t('cat.key')).toEqual(TRANSLATED);
		});

		hasSend = false;

		pubsub.publish('i18n.lang', 'de');

		i18n.t('cat.key', function(t) {
			expect(t('cat.key')).toEqual(TRANSLATED);
			done();
		});

		expect(hasSend).toBe(true);
	});*/

});