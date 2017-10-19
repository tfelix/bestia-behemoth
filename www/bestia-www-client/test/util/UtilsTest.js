import * as utils from '../../src/js/util/Utils';

describe('strFormat', function () {
	it('replaces placeholder in the correct order', function(){
		var str = utils.strFormat('Das {1} ein {0}', 'Test', 'ist');
		str.should.be.equal('Das ist ein Test');
	});
});

describe('strStartsWith', function () {
	it('returns true if the string starts with the same substring', function () {
		utils.strStartsWith('bestiaGame', 'bestia').should.be.true;
	});
	it('returns false if the string starts not with the same substring', function () {
		utils.strStartsWith('bestiaGame', 'ladida').should.be.true;
	});
});

describe('distance', function () {
	it('returns the euclidian distance between to points.', function () {
		utils.distance({
			x: 10,
			y: 12
		}, {
			x: 11,
			y: 12
		}).should.equal(1);
	});
});