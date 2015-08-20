jasmine.getFixtures().fixturesPath = 'specs/fixtures';

describe("Bestia.Chat", function() {
	
	var Game = function() {		
		var self = this;
		this.pubsub = new Bestia.PubSub();
		this.config = new Bestia.Config(this.pubsub);
	};

	var chatEle;
	var game;

	beforeEach(function() {
		loadFixtures('chat.html');
		chatEle = $('#chat');
		game = new Game();		
	});

	it("Reacts upon messages from the server.", function() {

		var chat = new Bestia.Chat(chatEle, game);

		game.pubsub.publish('chat.message', {
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

	it("Executes local commands.", function() {
		var chat = new Bestia.Chat(chatEle, game);

		game.pubsub.publish('chat.message', {
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
	
	it("Sends a correct public chat message", function(){
		var chat = new Bestia.Chat(chatEle, game);
		chat.LOCAL_NICKNAME = 'Sam'
		
		var handler = function(_, msg) {
			// {"mid":"chat.message","m":"PUBLIC","txt":"test","rxn":"","sn":"blubber 2","cmid":0}
			 
			expect(msg.mid).toEqual("chat.message");
			expect(msg.m).toEqual("PUBLIC");
			expect(msg.txt).toEqual("HelloWorld");
			expect(msg.rxn).toEqual("");
			expect(msg.sn).toEqual("Sam");
			expect(msg.cmid).toEqual(0);
		};
		
		game.pubsub.subscribe('io.sendMessage', handler);
		
		chat.test('HelloWorld');
		chat.sendChat();
	});
});