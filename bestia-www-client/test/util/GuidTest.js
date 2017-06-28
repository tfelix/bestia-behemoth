import {guid} from '../../src/js/util/Guid';

describe('Guid', function() {
	
	var pattern = /\w{8}-\w{4}-\w{4}-\w{4}-\w{12}/;
	
	it('Generates a uuid id as RFC spec.', function() {
		var id = guid();
		id.should.match(pattern);
	});

});