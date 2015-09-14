//jasmine.getFixtures().fixturesPath = 'specs/fixtures';

describe("Bestia.BestiaAttacks", function() {

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

	it("Requests attack list from the server upon method invocation.", function() {
		var attacks = new Bestia.BestiaAttacks(pubsub);
		
		attacks.request();
		
		expect(lastTopic).toEqual('io.sendMessage');
		expect(lastData.id).toEqual('attack.list.request');
	});
	
	/*
	it("Wont requests attack list after the attacks are cached.", function() {
		var attacks = new Bestia.BestiaAttacks(pubsub);
		
		attacks.request();
		
		// TODO answer with list of attacks.
		
		expect(lastTopic).toEqual('io.sendMessage');
		expect(lastData.id).toEqual('attack.list.request');

	});*/
	
	/*
	it("Resets (requests again) after setting of language.", function() {


	});*/
});