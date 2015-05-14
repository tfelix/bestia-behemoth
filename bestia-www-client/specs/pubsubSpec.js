describe("Bestia.PubSub", function() {

	it("Can subscribe and publish.", function() {

		var pubsub = new Bestia.Util.PubSub();

		var call1 = null;
		var call2 = null;

		pubsub.subscribe('test', function(a, b) {
			call1 = a;
			call2 = b;
		});

		pubsub.publish('test', 'hello');

		expect(call1).toEqual('test');
		expect(call2).toEqual('hello');
	});

	it("Can unsubscribe one function.", function() {

		var pubsub = new Bestia.Util.PubSub();

		var call1 = null;
		var call2 = null;

		var handler = function(a, b) {
			call1 = a;
			call2 = b;
		};

		pubsub.subscribe('test', handler);
		pubsub.unsubscribe('test', handler);

		pubsub.publish('test', 'hello');

		expect(call1).toEqual(null);
		expect(call2).toEqual(null);
	});
	
	it("Can unsubscribe all functions.", function() {

		var pubsub = new Bestia.Util.PubSub();

		var call1 = null;
		var call2 = null;

		var handler1 = function(a, b) {
			call1 = a;
			call2 = b;
		};
		
		var handler2 = function(a, b) {
			call1 = a;
			call2 = b;
		};

		pubsub.subscribe('test', handler1);
		pubsub.subscribe('test', handler2);
		pubsub.unsubscribe('test');

		pubsub.publish('test', 'hello');

		expect(call1).toEqual(null);
		expect(call2).toEqual(null);
	});
});
