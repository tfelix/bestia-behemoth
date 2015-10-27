describe("Bestia.Storage", function() {
	
	var testObj = {username: 'blubber 2', token: '12398244837489347823423432454354', id: 5};

	it("Can store and retrieve arbitrary objects with getAuth.", function() {

		var store = new Bestia.Storage();
		store.storeAuth(testObj);
		
		var obj = store.getAuth();
		
		expect(obj).toEqual(testObj);
	});

	it("Returns null if no object was found.", function() {

		var store = new Bestia.Storage();
		store.clear();
		
		var test = store.getAuth();
		
		expect(test).toBe(null);
	});

});