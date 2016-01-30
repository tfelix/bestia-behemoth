

def callback = { 
	log.info("Hello World.");
}

apiZone.setTimer(5000, callback)