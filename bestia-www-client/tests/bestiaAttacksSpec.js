//jasmine.getFixtures().fixturesPath = 'specs/fixtures';

describe("Bestia.BestiaAttacks", function() {

	var responseMsg = {
		mid : "attack.list.response",
		atks : [ {
			a : {
				adbn : "tackle",
				str : 1,
				ele : "NORMAL",
				m : 1,
				r : 1,
				bs : "SPECIAL",
				ct : 100,
				cd : 100
			},
			mlv : 1
		}, {
			a : {
				adbn : "ember",
				str : 5,
				ele : "FIRE",
				m : 10,
				r : 5,
				bs : "NORMAL",
				ct : 400,
				cd : 100
			},
			mlv : 10
		} ]
	};

	var pubsub = new Bestia.PubSub();
	var i18n = new Bestia.I18n(pubsub);

	var lastTopic = '';
	var lastData = {};

	var handler = function(_, data) {
		lastTopic = _;
		lastData = data;
	};

	pubsub.subscribe('io.sendMessage', handler);

	beforeEach(function() {
		lastTopic = '';
		lastData = {};
	});

	it("requests attack list from the server upon method invocation.", function() {
		var attacks = new Bestia.BestiaAttacks(pubsub, i18n);

		attacks.request();

		expect(lastTopic).toEqual('io.sendMessage');
		expect(lastData.mid).toEqual('attack.list.request');
	});

	it("wont requests attack list again after the attacks are cached.", function() {
		var attacks = new Bestia.BestiaAttacks(pubsub, i18n);

		attacks.request();
		pubsub.publish(responseMsg.mid, responseMsg);

		expect(attacks.attacks().length).toEqual(2);
		expect(attacks.attacks()[0].attackDatabaseName()).toEqual('tackle');
		expect(attacks.attacks()[1].attackDatabaseName()).toEqual('ember');

		lastTopic = '';
		attacks.request();
		expect(lastTopic).toMatch('');
	});

	it("sets the flag hasAttacks to true when attacks are loaded.", function() {
		var attacks = new Bestia.BestiaAttacks(pubsub, i18n);

		attacks.request();
		pubsub.publish(responseMsg.mid, responseMsg);

		expect(attacks.isLoaded()).toBeTruthy();
	});
});