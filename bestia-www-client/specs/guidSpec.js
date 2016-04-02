describe("Bestia.Guid", function() {
	
	var testObj = {username: 'blubber 2', token: '12398244837489347823423432454354', id: 5};
	var store = new Bestia.Storage();
	
	var pattern = /\w{8}-\w{4}-\w{4}-\w{4}-\w{12}/
	
	it("Can generate random number in the RFC4122 spec.", function() {
		var id = Bestia.Guid();
		expect(id.match(pattern)).toBe(true);
	});

});