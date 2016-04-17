/**
 DESC: Spawn a single attackable stone to the world.
 */
 
 api.entity()
	.setSprite('emperium')
	.setPosition(targetX, targetY)
	.setHp(100)
	.setTickCallback(5000, { 
		kill(); })
	.spawn();