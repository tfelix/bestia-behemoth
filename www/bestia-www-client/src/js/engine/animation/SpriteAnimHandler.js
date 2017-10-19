import LOG from '../../util/Log';

export default class SpriteAnimHandler {
	
	constructor(animName, duration = -1) {
		
		this._animName = animName;
		this._duration = duration;
		
	}
	
	execute(entity) {
		
		if(this._duration !== -1) {
			entity.playAnimation(this._animName, this._duration);
		} else {
			entity.playAnimation(this._animName);
		}
		
	}
	
}
