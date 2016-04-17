/**
 DESC: Spawn a single attackable stone to the world.
 */
 
 inventory
 
 api.entity()
	.setSprite('emperium')
	.setPosition(targetX, targetY)
	.setHp(100)
	.setTickCallback(5000, {})
	.spawn();

/*	
api.mob()
	.setSprite('poring')
	.setPosition(targetX, targetY)
	.setHp(100)
	.setTickCallback(5000, {})
	*/