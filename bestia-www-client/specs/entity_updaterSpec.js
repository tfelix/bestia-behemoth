describe("Bestia.Engine.EntityUpdater", function() {

	/*
	it("Fires callbacks upon arrival of new entities.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
	});*/
	
	it("Throws error when unknown callback is bound.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
		
		expect(function(){ updater.addHandler('lalal'); }).toThrow();
	});

	it("Holds references to player entities after it has recognized them.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
		
		
		
	});

	it("Holds references to map entities (only UUID) after it has recognized them.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
		
	});

	it("Deletes references to player entities after it has recognized removel.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
		
	});

	it("Deletes references to map entities after (only UUID) it has recognized removel.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
		
	});
});