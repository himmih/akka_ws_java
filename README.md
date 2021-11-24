This code for testing websockets on akka streams. 
Here I'm testing lots parallel akka websockets clients with akka websockets server.

This is sbt project!

Websockets server (WSServer) sends to websockets clients (WSClient) numbers from 1 to 10 every 30 seconds each.
WSClient - just write getting data to file /tmp/ws.log 

Just run WSServer & WSClient. You can use IDE for this.

Code quite dirty. In comments there are some interesting ideas )

_PS: WSClient and WSServer never stops - you have to stop them by yourself._

