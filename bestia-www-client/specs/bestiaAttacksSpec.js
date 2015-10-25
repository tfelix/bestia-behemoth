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

	it("Requests attack list from the server upon method invocation.",
			function() {
				var attacks = new Bestia.BestiaAttacks(pubsub);

				attacks.request();

				expect(lastTopic).toEqual('io.sendMessage');
				expect(lastData.mid).toEqual('attack.list.request');
			});

	it("Wont requests attack list again after the attacks are cached.",
			function() {
				var attacks = new Bestia.BestiaAttacks(pubsub);

				attacks.request();	
				pubsub.publish(responseMsg.mid, responseMsg);

				expect(attacks.attackList().length).toEqual(2);
				expect(attacks.attackList()[0].attackDatabaseName()).toEqual('tackle');
				expect(attacks.attackList()[1].attackDatabaseName()).toEqual('ember');
				
				lastTopic =  '';
				attacks.request();	
				expect(lastTopic).toEqual('');
			});

	/*
	 * it("Requests again after setting of language.", function() {
	 * 
	 * 
	 * });
	 */

	// Sends translation request for uncached attack desc and names.
});