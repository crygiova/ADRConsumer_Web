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

	<a href="startCons">Start ADR Consumers</a>
	<a href="stopCons">Stop ADr Consumers</a>
	<a href="home">HOME</a>
	
	<P>Notes: ${notes}.</P>
	
	<P>The time on the server is ${serverTime}.</P>
	<a href="consumers" target="_blank">ConsumersStats</a>
</body>
</html>
