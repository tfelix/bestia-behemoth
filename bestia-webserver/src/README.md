==== Bestia Websocket Server ====

This is the frontend component of the bestia server infractructure. 

The server should be as slim as possible. It will take incoming websocket connections verify a correct login token via questioning the backbone interserver and then accept or deny the connection. Incoming messages are then routed to the bestia backbone messaging system via interserver. The zones which are communicating about this backbone aswell might send messages to the connected account.