import Chat from '../../../src/js/ui/chat/Chat';
import Pubsub from '../../../src/js/util/PubSub';

describe('Chat', function(){
    
    describe('ctor', function(){
        it('Throws when no pubsub obj is given.', function(){
            (function(){
                new Chat();
            }).should.throw;
        });

        it('Works when pubsub is given.', function(){
            new Chat(new Pubsub);
        });
    });

    describe('recognizeSpeech', function(){
        it('Only works if enable flag is set.', function(){

        });
    });

    describe('sendChat', function(){
        it('Sends the text to the server.', function(){

        });

        it('Executes local commands', function(){

        });

        it('Sends commands to the server', function(){

        });
    });

    describe('clear', function(){
        it('Clears all messages', function(){
            var chat = new Chat(new Pubsub);
            chat.addLocalMessage('Das ist ein Test', 'PUBLIC');
            chat.clear();
            chat.messages().length.should.equal(0);
        });
    });

    describe('changeMode', function(){
        it('Sets the chat into a new chat mode', function(){

        });

        it('Does not set the mode if a unknown mode is given', function(){
            var chat = new Chat(new Pubsub);
            chat.mode('public');
            chat.mode('bla');
            chat.mode().should.equal('public');
        });
    });

    describe('addMessage', function(){
        it('Adds a new message to the chat.', function(){

        });

        it('Listens to pubsub published messages', function(){

        });
    });

    describe('scrollToBottom', function() {
        it('Scrolls the chat to the latest messages, to the bottom', function(){

        });
    });

     describe('addLocalMessage', function() {
        it('Adds a message to the chat called locally.', function(){

        });
    });
});