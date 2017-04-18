import Chat from '../../../src/js/ui/chat/Chat';

describe('Chat', function(){
    describe('ctor', function(){
        it('Throws when no pubsub obj is given.', function(){

        });

        it('Works when pubsub is given.', function(){

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

        });
    });

    describe('changeMode', function(){
        it('Sets the chat into a new chat mode', function(){

        });

        it('Does not set the mode if a unknown mode is given', function(){

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