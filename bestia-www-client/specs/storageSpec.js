describe("Bestia.Storage", function() {
	
	var testObj = {test: 123, hello: 'lalal'};

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