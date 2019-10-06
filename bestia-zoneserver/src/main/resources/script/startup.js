/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

Bestia.info("Bestia Behemoth startup script");
Bestia.spawnMob("blob", 12 ,15, 10)

Bestia.newEntity()
    .script()
    .setInterval('checkEntities', 5000)

function checkEntities() {
    Bestia.info("in checkEntities()")
}