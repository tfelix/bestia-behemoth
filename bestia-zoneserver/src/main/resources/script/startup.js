/**
 * Global startup script. This script is once triggered upon first start of the
 * cluster.
 */
function main() {
	Bestia.info("Bestia Behemoth startup script");
	
	Bestia.entity("blob", 12 ,15)

	Bestia.entity()
	  .script()
	  .setInterval('startup:checkEntities', 5000)
}

function checkEntities() {
  Bestia.info("Check entities.");
}