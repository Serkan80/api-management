package nl.probot.api.management.utils;

import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static io.micrometer.common.util.StringUtils.isNotBlank;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;

/***
 * Helps writing dynamic queries when parameters are optional.
 *
 * It's intended for Panache, but it can also be used outside Panache.
 *
 * Terminology:
 * - StaticStatement = statement that has only 1 parameter that will be converted into: param = :param
 * - DynamicStatement = statement that can contain more than 1 parameter and it will be used as-is. Example: p1 = :p1 and p2 > 1
 * - WhereStatement = is similar to DynamicStatement, and it should only be used in the .buildXyz(...) method.
 */
public class PanacheDyanmicQueryHelper {

    public static final String AND = " and ";
    public static final String OR = " or ";
    public static final String COMMA = ", ";

    boolean allowBlankValues = false;
    List<Statement> statements = new ArrayList<>();

    /**
     * Allows setting of empty values. Mostly useful for the update query.
     */
    public PanacheDyanmicQueryHelper allowBlankValues() {
        this.allowBlankValues = true;
        return this;
    }

    public PanacheDyanmicQueryHelper statements(Statement... statements) {
        Arrays.stream(statements)
                .filter(stmt -> !stmt.getClass().equals(WhereStatement.class))
                .filter(stmt -> isNotNull(stmt.param()))
                .forEach(this.statements::add);

        return this;
    }

    /**
     * @return will return something like: p1 = ?1 and p2 = ?2 ...
     */
    public String buildWhereStatement() {
        return buildWhereStatement(AND);
    }

    /**
     * @param separator can be: AND, OR
     * @see PanacheDyanmicQueryHelper#buildWhereStatement()
     */
    public String buildWhereStatement(String separator) {
        return buildQuery(separator, Optional.empty(), Optional.empty());
    }

    /**
     * @param whereStatement optionally a where statement in the form of: w1 = :w1 and w2 = :w2 ...
     * @return will return something like: set p1 = ?1, p2 = ?2, ... (where w1 = ?n ... )
     */
    public String buildUpdateStatement(WhereStatement whereStatement) {
        if (whereStatement != null && isNotNull(whereStatement.param())) {
            this.statements.add(whereStatement);
        }

        return buildQuery(
                COMMA,
                Optional.of("set "),
                Optional.ofNullable(whereStatement)
                        .filter(where -> isNotNull(where.param()))
                        .map(where -> " where %s".formatted(where.statement())));
    }

    public Object[] values() {
        return this.statements.stream()
                .flatMap(st -> {
                    var params = st.param();
                    if (params instanceof List l) {
                        return l.stream();
                    } else {
                        return Stream.of(params);
                    }
                })
                .toList()
                .toArray();
    }

    // we need to replace :param with ?number, because the update statement only support numbers
    private String buildQuery(String separator, Optional<String> prefix, Optional<String> suffix) {
        var paramCounter = new AtomicInteger(1);
        var result = this.statements.stream()
                .map(stmt -> switch (stmt) {
                    case DynamicStatement ds -> "%s%s%s".formatted(
                            separator.indexOf(',') > -1 ? "" : "(",
                            replaceQueryParams(paramCounter, ds.statement()),
                            separator.indexOf(',') > -1 ? "" : ")");
                    case StaticStatement ss -> "%s = ?%d".formatted(ss.statement(), paramCounter.getAndIncrement());
                    case WhereStatement ws -> "";
                })
                .filter(st -> !st.isBlank())
                .collect(joining(
                        separator,
                        prefix.filter(this::containsStatements).orElse(""),
                        suffix.map(stmt -> replaceQueryParams(new AtomicInteger(this.statements.size()), stmt)).orElse("")))
                .trim();

        Log.debugf("Produced query: %s", result);
        return result;
    }

    private String replaceQueryParams(AtomicInteger counter, String statement) {
        var result = statement;

        while (result.contains(":")) {
            result = result.replaceFirst(":\\w+", "?%d".formatted(counter.getAndIncrement()));
        }
        return result;
    }

    private boolean containsStatements(String statement) {
        return !(this.statements.isEmpty() || this.statements.get(0).getClass().equals(WhereStatement.class));
    }

    private boolean isNotNull(Object value) {
        if (this.allowBlankValues) {
            return Objects.nonNull(value);
        }

        return switch (value) {
            case null -> false;
            case String s -> !s.isBlank();
            case List l -> !l.isEmpty() && l.stream().allMatch(e -> nonNull(e) && isNotBlank(e.toString()));
            default -> !value.toString().isBlank();
        };
    }

    public sealed interface Statement permits StaticStatement, DynamicStatement, WhereStatement {

        /**
         * the query statement, example: p1 = :p1 and p2 > :start or p3 <= :end.
         */
        String statement();

        /*
         * the parameter values, when this is null, then the statement will be skipped and not used in the final query.
         */
        Object param();
    }

    public record StaticStatement(String statement, Object param) implements Statement {
    }

    public record DynamicStatement(String statement, List<Object> param) implements Statement {
        public DynamicStatement(String statement, Object value) {
            this(statement, List.of(value));
        }
    }

    public record WhereStatement(String statement, List<Object> param) implements Statement {
        public WhereStatement(String statement, Object value) {
            this(statement, List.of(value));
        }
    }
}
