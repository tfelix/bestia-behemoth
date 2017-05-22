
function test() {
	print('WTF?!');
}

function onTouch() {
	Bestia.info('Hello from script.');
}

function bla(){
	Bestia.findCollidingEntities(aoe).forEach(function(entity){
		entity.takeDamage('fire', 100);
	});
}

function main() {
	
	// Sprite
	// Animation
	// Duration
	// Position x
	// onEnter
	// onInterval
	// onLeave
	var aoe = Bestia.createSpellEntity(null, 'fireSprite', 15000);
	
	aoe.setOnTouch(function(entity){
		Bestia.Entity.playAnimation(aoe, 'explosion');
	});

	aoe.setInterval(1000, 'onTouch');
}
