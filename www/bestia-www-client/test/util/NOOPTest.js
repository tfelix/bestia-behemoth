import NOOP from '../../src/js/util/NOOP';

describe('NOOP', function () {
	it('Is a function and can be executed without effects.', function () {
		NOOP.should.be.Function;
		NOOP();
	});
});