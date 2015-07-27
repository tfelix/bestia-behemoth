jasmine.getFixtures().fixturesPath = 'specs/fixtures';

describe("Bestia.Chat", function() {

	var chatEle;
	var game = {
		config : {
			userName : 'Test'
		}
	};

	beforeEach(function() {
		loadFixtures('chat.html');
		chatEle = $('#chat');
	});

	it("Reacts upon messages from the server.", function() {

		var chat = new Bestia.Chat(chatEle, game);

		Bestia.publish('chat.message', {
			mid : 'chat.message',
			m : 'PARTY',
			txt : 'Das ist ein Party Chat test.',
			sn : 'rocket'
		});

		expect(chat.messages().length).toEqual(1);
	});

	it("Changes chat modes on specific input.", function() {
		var chat = new Bestia.Chat(chatEle, game);

		chat.text('/g lalala');
		expect(chat.mode()).toEqual('GUILD');
		chat.text('/p lalala');
		expect(chat.mode()).toEqual('PARTY');
		chat.text('/s lalala');
		expect(chat.mode()).toEqual('PUBLIC');
	});

	it("Can whisper to a user.", function() {
		var chat = new Bestia.Chat(chatEle, game);

		chat.text('/w John test1234');
		expect(chat.whisperNick()).toEqual('John');
		expect(chat.text()).toEqual('test1234');
		chat.text('/s lalala');
		expect(chat.mode()).toEqual('PUBLIC');
	});

	it("Changes mode via direct click input.", function() {
		var chat = new Bestia.Chat(chatEle, game);

		chat.changeMode('GUILD');
		expect(chat.mode()).toEqual('GUILD');
	});

	it("Registers commands.", function() {
		var chat = new Bestia.Chat(chatEle, game);

		// Exclude the basic command.
		expect(chat._localCommands.length).toBe(Object.keys(Bestia.Chat.Commands).length - 1);
	});

	it("Executes commands.", function() {
		var chat = new Bestia.Chat(chatEle, game);

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