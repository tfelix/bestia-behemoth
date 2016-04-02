/**
 * Generates and returns a random guid. Can be used for message referencing.
 */
Bestia.Guid = function() {
	
	return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
    s4() + '-' + s4() + s4() + s4();
	
};

Bestia.Guid._s4 = function() {
	return Math.floor((1 + Math.random()) * 0x10000)
    .toString(16)
    .substring(1);
};