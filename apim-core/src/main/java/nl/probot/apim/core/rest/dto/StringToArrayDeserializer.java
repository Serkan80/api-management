package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.Arrays;

public class StringToArrayDeserializer extends JsonDeserializer<String[]> {

    @Override
    public String[] deserialize(JsonParser parser, DeserializationContext deserializationContext) throws IOException {
        var accounts = parser.getValueAsString();
        return Arrays.stream(accounts.split(",")).map(String::strip).toArray(String[]::new);
    }
}
