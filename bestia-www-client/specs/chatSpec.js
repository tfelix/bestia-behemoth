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

	it("Changes chat modes on specific input.", function() {
		var game = {};
		var chat = new Bestia.Chat(game, "test");

		chat.text('/g lalala');
		expect(chat.mode()).toEqual('GUILD');
		chat.text('/p lalala');
		expect(chat.mode()).toEqual('PARTY');
		chat.text('/s lalala');
		expect(chat.mode()).toEqual('PUBLIC');
	});

	it("Can whisper to a user.", function() {
		var game = {};
		var chat = new Bestia.Chat(game, "test");

		chat.text('/w John test1234');
		expect(chat.whisperNick()).toEqual('John');
		expect(chat.text()).toEqual('test1234');
		chat.text('/s lalala');
		expect(chat.mode()).toEqual('PUBLIC');
	});

	it("Changes mode via direct click input.", function() {
		var game = {};
		var chat = new Bestia.Chat(game, "test");

		chat.changeMode('GUILD');
		expect(chat.mode()).toEqual('GUILD');
	});

	it("Registers commands.", function() {
		var game = {};
		var chat = new Bestia.Chat(game, "test");

		// Exclude the basic command.
		expect(chat._localCommands.length).toBe(Object.keys(Bestia.Chat.Commands).length - 1);
	});

	it("Executes commands.", function() {
		var game = {};
		var chat = new Bestia.Chat(game, "test");

		Bestia.publish('chat.message', {
			mid : 'chat.message',
			m : 'PARTY',
			txt : 'Das ist ein Party Chat test.',
			sn : 'rocket'
		});
		chat.text('/help');
		chat.sendChat();
		expect(chat.messages().length).toBeGreaterThan(1);
		chat.text('/clear');
		chat.sendChat();
		expect(chat.messages().length).toEqual(0);
	});
});