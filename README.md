# slackbot
While this is not really a Webapp, I still chose to implemented it in a way, 
where the final artifact is simply a war file that can be throw into any 
web-container (like Tomcat for instance) where it will bootstrap itself. 
I use the open source Simple Slack API project, which seemed well 
maintained and also important, uses Tyrus, an the open source JSR 356 - 
Java API as the WebSocket implementation. Implementing the bot integration 
as an JAX-RS application and using the Simple Slack API turns this into 
an rather short and simple piece of code. 

More details: 
https://wolfpaulus.com/journal/software/slackbot/
