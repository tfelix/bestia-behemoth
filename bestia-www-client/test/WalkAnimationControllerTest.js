import WalkAnimationController from '../src/js/engine/animation/WalkAnimationController';

var anim = new WalkAnimationController();

describe('WalkAnimationController', function(){
	describe('getWalkAnimation', function(){
		it('returns walk_right', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 2, y: 4}).should.equal('walk_right');
		});
		it('returns walk_up', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 1, y: 3}).should.equal('walk_up');
		});
		it('returns walk_down', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 1, y: 5}).should.equal('walk_down');
		});
		it('returns walk_left', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 0, y: 4}).should.equal('walk_left');
		});
		it('returns walk_up_right', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 2, y: 3}).should.equal('walk_up_right');
		});
		it('returns walk_down_right', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 2, y: 5}).should.equal('walk_down_right');
		});
		it('returns walk_down_left', function(){
			anim.getWalkAnimationName({x: 1, y: 4}, {x: 0, y: 5}).should.equal('walk_down_left');
		});
		it('returns walk_up_left', function(){
			anim.getWalkAnimationName({x: 2, y: 4}, {x: 1, y: 3}).should.equal('walk_up_left');
		});
	});

	describe('getStandAnimationName', function(){
		it('returns stand_right', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 2, y: 4}).should.equal('stand_right');
		});
		it('returns stand_up', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 1, y: 3}).should.equal('stand_up');
		});
		it('returns stand_down', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 1, y: 5}).should.equal('stand_down');
		});
		it('returns stand_left', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 0, y: 4}).should.equal('stand_left');
		});
		it('returns stand_up_right', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 2, y: 3}).should.equal('stand_up_right');
		});
		it('returns stand_down_right', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 2, y: 5}).should.equal('stand_down_right');
		});
		it('returns stand_down_left', function(){
			anim.getStandAnimationName({x: 1, y: 4}, {x: 0, y: 5}).should.equal('stand_down_left');
		});
		it('returns stand_up_left', function(){
			anim.getStandAnimationName({x: 2, y: 4}, {x: 1, y: 3}).should.equal('stand_up_left');
		});
	});
});