/**
 DESC: Spawn a single attackable stone to the world.
 */
function main() {
    api.newEntity()
        .setVisual('emperium')
        .setPosition(TARGET_POSITION)
        .setLivetime(360000)
        .setHp(500)
        .spawn();
}