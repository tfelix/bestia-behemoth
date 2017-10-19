import Chat from '../../../src/js/ui/chat/Chat';
import Pubsub from '../../../src/js/util/PubSub';

describe('Chat', function () {

    describe('ctor', function () {
        it('Throws when no pubsub obj is given.', function () {
            (function () {
                new Chat();
            }).should.throw;
        });

        it('Works when pubsub is given.', function () {
            new Chat(new Pubsub);
        });
    });

    describe('recognizeSpeech', function () {
        it('Only works if enable flag is set.', function () {

        });
    });

    it("Reacts upon messages from the server.", function () {

        var chat = new Bestia.Chat(chatEle, game);

        game.pubsub.publish('chat.message', {
            mid: 'chat.message',
            m: 'PARTY',
            txt: 'Das ist ein Party Chat test.',
            sn: 'rocket'
        });

        expect(chat.messages().length).toEqual(1);
    });

    it("Changes chat modes on specific input.", function () {
        var chat = new Bestia.Chat(chatEle, game);

        chat.text('/g lalala');
        expect(chat.mode()).toEqual('GUILD');
        chat.text('/p lalala');
        expect(chat.mode()).toEqual('PARTY');
        chat.text('/s lalala');
        expect(chat.mode()).toEqual('PUBLIC');
    });

    it("Can whisper to a user.", function () {
        var chat = new Bestia.Chat(chatEle, game);

        chat.text('/w John test1234');
        expect(chat.whisperNick()).toEqual('John');
        expect(chat.text()).toEqual('test1234');
        chat.text('/s lalala');
        expect(chat.mode()).toEqual('PUBLIC');
    });

    it("Changes mode via direct click input.", function () {
        var chat = new Bestia.Chat(chatEle, game);

        chat.changeMode('GUILD');
        expect(chat.mode()).toEqual('GUILD');
    });

    describe('sendChat', function () {
        it('Sends the text to the server.', function () {
            var chat = new Bestia.Chat(chatEle, game);
            chat.LOCAL_NICKNAME = 'Sam'
            
            var handler = function(_, msg) {
                // {"mid":"chat.message","m":"PUBLIC","txt":"test","rxn":"","sn":"blubber 2","cmid":0}
                 
                expect(msg.mid).toEqual("chat.message");
                expect(msg.m).toEqual("PUBLIC");
                expect(msg.txt).toEqual("HelloWorld");
                expect(msg.rxn).toEqual("");
                expect(msg.sn).toEqual("Sam");
            };
            
            game.pubsub.subscribe('io.sendMessage', handler);
            
            chat.text('HelloWorld');
            chat.sendChat();
        });

        it('Executes local commands', function () {
            var chat = new Bestia.Chat(chatEle, game);

            game.pubsub.publish('chat.message', {
                mid: 'chat.message',
                m: 'PARTY',
                txt: 'Das ist ein Party Chat test.',
                sn: 'rocket'
            });
            chat.text('/help');
            chat.sendChat();
            expect(chat.messages().length).toBeGreaterThan(1);
            chat.text('/clear');
            chat.sendChat();
            expect(chat.messages().length).toEqual(0);
        });

        it('Sends commands to the server', function () {

        });
    });

    describe('clear', function () {
        it('Clears all messages', function () {
            var chat = new Chat(new Pubsub);
            chat.addLocalMessage('Das ist ein Test', 'PUBLIC');
            chat.clear();
            chat.messages().length.should.equal(0);
        });
    });

    describe('changeMode', function () {
        it('Sets the chat into a new chat mode', function () {

        });

        it('Does not set the mode if a unknown mode is given', function () {
            var chat = new Chat(new Pubsub);
            chat.mode('public');
            chat.mode('bla');
            chat.mode().should.equal('public');
        });
    });

    describe('addMessage', function () {
        it('Adds a new message to the chat.', function () {

        });

        it('Listens to pubsub published messages', function () {

        });
    });

    describe('scrollToBottom', function () {
        it('Scrolls the chat to the latest messages, to the bottom', function () {

        });
    });

    describe('addLocalMessage', function () {
        it('Adds a message to the chat called locally.', function () {

        });
    });
});