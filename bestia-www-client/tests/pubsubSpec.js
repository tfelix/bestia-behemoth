describe("Bestia.PubSub", function() {

	it("can subscribe and publish.", function() {

		var pubsub = new Bestia.PubSub();

		var call1 = null;
		var call2 = null;

		pubsub.subscribe('test', function(a, b) {
			call1 = a;
			call2 = b;
		});

		pubsub.publish('test', 'hello');

		expect(call1).toMatch('test');
		expect(call2).toMatch('hello');
	});

	it("can unsubscribe one function.", function() {

		var pubsub = new Bestia.PubSub();

		var call1 = null;
		var call2 = null;

		var handler = function(a, b) {
			call1 = a;
			call2 = b;
		};

		pubsub.subscribe('test', handler);
		pubsub.unsubscribe('test', handler);

		pubsub.publish('test', 'hello');

		expect(call1).toBeNull();
		expect(call2).toBeNull();
	});
	
	it("can unsubscribe all functions.", function() {

		var pubsub = new Bestia.PubSub();

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

		expect(call1).toBeNull();
		expect(call2).toBeNull();
	});
});
