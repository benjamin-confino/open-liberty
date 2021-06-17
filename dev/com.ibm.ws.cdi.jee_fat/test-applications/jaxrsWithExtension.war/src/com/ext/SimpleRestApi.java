package com.ext;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/simplerestapi")
public class SimpleRestApi
{

    @Inject
    @GenericDelegateQualifier
    private MyInterface anInterface;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response hello() {
        String message = anInterface.proceed();

        return Response
          .status(Response.Status.OK)
          .entity(message)
          .build();
    }

}
