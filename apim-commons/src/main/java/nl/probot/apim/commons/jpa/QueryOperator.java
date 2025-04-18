package nl.probot.apim.commons.jpa;

public enum QueryOperator {
    AND(" and "),
    OR(" or "),
    COMMA(", ");

    public final String value;

    QueryOperator(String value) {
        this.value = value;
    }
}
