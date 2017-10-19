import ClientVarManager from '../../src/js/ui/ClientVarManager';


describe('ClientVarManager', function() {
	
	it('Throws error when no pubsub given.', function() {
		expect(function(){
			new ClientVarManager(null);
		}).to.throw();
	});

    it('Sends out server message if received signal.', function() {
		
	});

    it('Calls error func after server does not reply in time.', function() {
		
	});

    it('Calls callback func if server does reply.', function() {
		
	});

    it('Dont calls callback twice if server reply with same key.', function() {
		
	});

    it('Dont calls error callback if the mode was set or delete.', function() {
		
	});
});