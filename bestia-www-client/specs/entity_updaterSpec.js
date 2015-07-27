describe("Bestia.Engine.EntityUpdater", function() {

	/*
	it("Fires callbacks upon arrival of new entities.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub);
	});*/
	
	var onAppearHandler = function() {
		
	};
	
	it("Throws for no onAppear callback.", function(){
		var pubsub = new Bestia.Util.PubSub();
		expect(function(){ new Bestia.Engine.EntityUpdater(pubsub) }).toThrow();
	});
	
	it("Throws error when unknown callback is bound.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub, onAppearHandler);
		
		expect(function(){ updater.addHandler('lalal'); }).toThrow();
	});

	it("Holds references to player entities after it has recognized them.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub, onAppearHandler);
		
		
		
	});

	it("Holds references to map entities (only UUID) after it has recognized them.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub, onAppearHandler);
		
	});

	it("Deletes references to player entities after it has recognized removel.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub, onAppearHandler);
		
	});

	it("Deletes references to map entities after (only UUID) it has recognized removel.", function() {
		var pubsub = new Bestia.Util.PubSub();
		var updater = new Bestia.Engine.EntityUpdater(pubsub, onAppearHandler);
		
	});
});