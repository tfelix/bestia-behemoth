import {guid} from '../src/js/util/Guid';

describe('Guid', function() {
	
	var pattern = /\w{8}-\w{4}-\w{4}-\w{4}-\w{12}/;
	
	it('Can generate random number in the RFC4122 spec', function() {
		var id = guid();
		id.should.match(pattern);
	});

});