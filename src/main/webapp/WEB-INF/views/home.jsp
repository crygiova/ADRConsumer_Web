<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ page session="false"%>
<html>
<head>
<title>CONSUMERS</title>
</head>
<body>
	<h1>ADREM Consumers Simulation!</h1>

	<h4>Consumers Simulation Home Page</h4>

	<p>Consumers Simulation Started: ${simStarted}</p>

	<a href="http://localhost:8081/adrem_consumer/startCons">Start ADR Consumers</a>
	<a href="http://localhost:8081/adrem_consumer/stopCons">Stop ADr Consumers</a>
	<a href="http://localhost:8081/adrem_consumer">HOME</a>
	
	<P>Notes: ${notes}.</P>
	
	<P>The time on the server is ${serverTime}.</P>
	<a href="http://localhost:8081/adrem_consumer/consumers" target="_blank">ConsumersStats</a>
</body>
</html>
