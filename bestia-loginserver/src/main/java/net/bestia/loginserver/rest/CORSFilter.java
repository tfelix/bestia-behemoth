package net.bestia.loginserver.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

/**
 * Allow CORS Requests.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class CORSFilter implements ContainerResponseFilter {

	@Override
	public ContainerResponse filter(ContainerRequest request, ContainerResponse response) {
		ResponseBuilder responseBuilder = Response.fromResponse(response.getResponse());

		// * (allow from all servers)
		responseBuilder.header("Access-Control-Allow-Origin", "*")

		// As a part of the response to a request, which HTTP methods can be used during the actual request.
				.header("Access-Control-Allow-Methods", "GET, POST, PUT, UPDATE")

				// How long the results of a request can be cached in a result cache.
				.header("Access-Control-Max-Age", "151200")

				// As part of the response to a request, which HTTP headers can be used during the actual request.
				.header("Access-Control-Allow-Headers", "x-requested-with,Content-Type");

		String requestHeader = request.getHeaderValue("Access-Control-Request-Headers");

		if (null != requestHeader && !requestHeader.equals(null)) {
			responseBuilder.header("Access-Control-Allow-Headers", requestHeader);
		}

		response.setResponse(responseBuilder.build());
		return response;
	}

}
