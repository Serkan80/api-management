package nl.probot.apim.core;

import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.jboss.resteasy.reactive.server.multipart.MultipartFormDataInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Path("/multipart")
public class MockMultipart {

    @POST
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    public List<JsonObject> multipart(MultipartFormDataInput input) throws IOException {
        var map = input.getValues();
        var items = new ArrayList<JsonObject>();

        for (var entry : map.entrySet()) {
            for (var value : entry.getValue()) {
                items.add(JsonObject.of(
                        "name", entry.getKey(),
                        "size", value.isFileItem() ? value.getFileItem().getFileSize() : value.getValue().length(),
                        "charset", value.getCharset(),
                        "filename", value.getFileItem().getFile(),
                        "inMemory", value.getFileItem().isInMemory(),
                        "isFile", value.isFileItem(),
                        "headers", value.getHeaders()));
            }
        }
        return items;
    }
}
