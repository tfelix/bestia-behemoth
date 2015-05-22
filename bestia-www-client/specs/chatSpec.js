describe("Bestia.Chat", function() {

	it("Reacts upon messages from the server.", function() {
		var game = {};
		var chat = new Bestia.Chat(game, "test");

		Bestia.publish('chat.message', {
			mid : 'chat.message',
			m : 'PARTY',
			txt : 'Das ist ein Party Chat test.',
			sn : 'rocket'
		});
		
		expect(chat.messages().length).toEqual(1);
	});

	/*
	it("Changes mode on specific input.", function() {

	});

	it("Changes mode via direct click input.", function() {

	});

	it("Executes commands.", function() {

	});*/
});