describe("Bestia.Storage", function() {
	
	var testObj = {username: 'blubber 2', token: '12398244837489347823423432454354', id: 5};
	var store = new Bestia.Storage();
	
	// TODO Die Cookie lib ist SCHEIßE! Unit Testet nicht. Bitte eine eigene schreiben.
	
/*
	it("Can store and retrieve arbitrary objects with getAuth.", function() {
		store.clear();
		store.storeAuth(testObj);
		var obj = store.getAuth();
		
		//expect(JSON.stringify(obj)).toEqual(JSON.stringify(testObj));
		//expect(true).toBeTruthy();
	});

	it("Returns null if no object was found.", function() {

		var store = new Bestia.Storage();
		store.clear();
		
		var test = store.getAuth();
		
		expect(test).toBe(null);
	});
*/
});