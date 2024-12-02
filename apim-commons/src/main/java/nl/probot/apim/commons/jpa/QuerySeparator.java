package nl.probot.apim.commons.jpa;

public enum QuerySeparator {
    AND(" and "),
    OR(" or "),
    COMMA(", ");

    public final String value;

    QuerySeparator(String value) {
        this.value = value;
    }
}
