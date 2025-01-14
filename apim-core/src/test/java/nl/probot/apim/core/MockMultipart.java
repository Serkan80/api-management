package nl.probot.apim.core;

import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.multipart.FormValue;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static java.util.stream.Collectors.joining;

@Path("/multipart")
public class MockMultipart {

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    public List<JsonObject> multipart(MultipartFormDataInput input) throws IOException {
        var map = input.getValues();
        var result = new ArrayList<JsonObject>();

        for (var entry : map.entrySet()) {
            for (var value : entry.getValue()) {
                result.add(JsonObject.of(
                        "name", entry.getKey(),
                        "size", value.isFileItem() ? value.getFileItem().getFileSize() : value.getValue().length(),
                        "charset", value.getCharset(),
                        "filename", value.getFileItem().getFile(),
                        "inMemory", value.getFileItem().isInMemory(),
                        "isFile", value.isFileItem(),
                        "headers", value.getHeaders()));
            }
        }
        return result;
    }

    @POST
    @Path("/form")
    @Consumes(APPLICATION_FORM_URLENCODED)
    @Produces(APPLICATION_JSON)
    public JsonObject formData(MultipartFormDataInput formData, @Context UriInfo uriInfo) {
        var items = new JsonObject();

        if (formData != null && !formData.getValues().isEmpty()) {
            Log.infof("reading formData from body");
            formData.getValues().entrySet().forEach(entry -> {
                var value = entry.getValue().stream().map(FormValue::getValue).collect(joining());
                items.put(entry.getKey(), value);
            });
        } else {
            Log.infof("reading formData from query params");
            uriInfo.getQueryParameters().entrySet().forEach(entry -> {
                var value = entry.getValue();
                items.put(entry.getKey(), String.join(",", value));
            });
        }
        return items;
    }
}
