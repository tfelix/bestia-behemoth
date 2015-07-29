describe("Bestia.Config", function() {
	
	it("Updates upon server.info message arrival.", function() {
		var pubsub = new Bestia.PubSub();
		var conf = new Bestia.Config(pubsub);
		
		// Simulate the server communication.
		var serverInfo = {
			z : [ 'test-1', 'test-2' ],
			v : '1.0.0-ALPHA1-build1234',
			cp : 3,
			res : 'http://localhost/assets'
		};

		pubsub.publish('server.info', serverInfo);
		
		expect(conf.zones()).toEqual(serverInfo.z);
		expect(conf.version()).toBe(serverInfo.v);
		expect(conf.connectedPlayer()).toBe(serverInfo.cp);
		expect(conf.resourceURL()).toBe(serverInfo.res);
	});
	
	it("Updates upon server.auth message arrival.", function() {
		var pubsub = new Bestia.PubSub();
		var conf = new Bestia.Config(pubsub);
		
		// Simulate the server communication.
		var authMsg = {username: "thomas", accId: 1337}
		pubsub.publish('system.auth', authMsg);
		
		expect(conf.userName()).toEqual(authMsg.username);
		expect(conf.accountId()).toBe(authMsg.accId);
	});
});