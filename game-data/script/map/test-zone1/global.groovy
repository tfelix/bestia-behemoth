
log("Hello World.");

def callback = { 
	println "Callback Hello World!"
}

apiZone.setTimer(5000, callback)