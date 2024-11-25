package nl.probot.api.management.entities;

import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

public class PanacheDyanmicQueryHelper {

    public static final String AND = " and ";
    public static final String OR = " or ";
    public static final String COMMA = ", ";

    public List<Statement> statements = new ArrayList<>();

    public PanacheDyanmicQueryHelper statements(Statement... statements) {
        Arrays.stream(statements)
                .filter(Objects::nonNull)
                .filter(st -> isNotNull(st.value()))
                .forEach(this.statements::add);

        return this;
    }

    public String buildWhereStatement() {
        return buildWhereStatement(AND);
    }

    public String buildWhereStatement(String separator) {
        return buildQuery(separator, Optional.empty(), Optional.empty());
    }

    public String buildUpdateStatement(WhereStatement whereStatement) {
        var where = new AtomicReference<>(whereStatement.statement);

        if (whereStatement != null && isNotNull(whereStatement.value())) {
            this.statements.add(whereStatement);
            var maxIndex = this.statements.size();

            for (var i = maxIndex; where.get().contains(":"); i++) {
                where.set(where.get().replaceFirst(":\\w+", "?%d".formatted(i)));
            }
        }

        return buildQuery(
                COMMA,
                Optional.of("set "),
                Optional.ofNullable(whereStatement).map(s -> " where %s".formatted(where.get())));
    }

    public Object[] values() {
        return this.statements.stream()
                .flatMap(st -> {
                    var params = st.value();
                    if (params instanceof List l) {
                        return l.stream();
                    } else {
                        return Stream.of(params);
                    }
                })
                .peek(param -> System.out.printf("%s, %s\n", param, param.getClass()))
                .toList()
                .toArray();
    }

    private static boolean isNotNull(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private String buildQuery(String separator, Optional<String> prefix, Optional<String> suffix) {
        var result = IntStream.range(0, this.statements.size())
                .mapToObj(i -> switch (this.statements.get(i)) {
                    case DynamicStatement ds -> ds.statement();
                    case StaticStatement ss -> "%s = ?%d".formatted(ss.statement(), i + 1);
                    case WhereStatement ws -> "";
                })
                .filter(st -> !st.isBlank())
                .collect(joining(separator, prefix.orElse(""), suffix.orElse("")));

        Log.debugf("Build query: %s", result);
        return result;
    }

    public sealed interface Statement permits StaticStatement, DynamicStatement, WhereStatement {

        String statement();

        Object value();
    }

    public record StaticStatement(String statement, Object value) implements Statement {
    }

    public record DynamicStatement(String statement, Object value) implements Statement {
    }

    public record WhereStatement(String statement, List<Object> value) implements Statement {

        public WhereStatement(String statement, Object value) {
            this(statement, List.of(value));
        }
    }
}
