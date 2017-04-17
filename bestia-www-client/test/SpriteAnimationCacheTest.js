import SpriteAnimationCache from '../src/js/engine/animation/SpriteAnimationCache';

describe('SpriteAnimationCache', function(){
	
	describe('addSpriteDescription', function(){
		it('fails if it is called with null/undefined', function(){
			var cache = new SpriteAnimationCache();
			(function(){
				cache.addSpriteDescription();
			}).should.throw();
		});

		it('adds a sprite description', function(){
			var cache = new SpriteAnimationCache();
			(function(){
				cache.addSpriteDescription();
			}).should.not.throw();
		});
	});

	describe('addOffsetDescription', function(){
		// TODO
	});

	describe('getOffsetDescription', function(){
		// TODO
	});

	describe('clear', function(){
		// TODO
	});
});