describe("Bestia.Config", function() {
	
	it("Updates upon server.info message arrival.", function() {
		
		var conf = new Bestia.Config();
		
		// Simulate the server communication.
		var serverInfo = {
			z : [ 'test-1', 'test-2' ],
			v : '1.0.0-ALPHA1-build1234',
			cp : 3,
			res : 'http://localhost/assets'
		};

		Bestia.PubSub.publish('server.info', serverInfo);
		
		expect(conf.zones()).toEqual(serverInfo.z);
		expect(conf.version()).toBe(serverInfo.v);
		expect(conf.connectedPlayer()).toBe(serverInfo.cp);
		expect(conf.resourceURL()).toBe(serverInfo.res);
	});
});