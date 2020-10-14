/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */

function main() {
    Bestia.info("Bestia Behemoth STARTUP script");

    Bestia.newEntity()
        .script()
        .setInterval(5000, 'checkEntities')
}

function checkEntities() {
    Bestia.info("in checkEntities()")
}