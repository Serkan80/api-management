package nl.probot.api.management.entities;

import nl.probot.api.management.utils.PanacheDyanmicQueryHelper;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper.DynamicStatement;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper.Statement;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper.WhereStatement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static nl.probot.api.management.utils.PanacheDyanmicQueryHelper.OR;
import static org.assertj.core.api.Assertions.assertThat;

class PanacheDyanmicQueryHelperTest {

    @Test
    void empty() {
        var helper = new PanacheDyanmicQueryHelper();
        var query1 = helper.buildWhereStatement();
        var query2 = helper.buildUpdateStatement(null);

        assertThat(query1).isBlank();
        assertThat(query2).isBlank();
        assertThat(helper.values()).hasSize(0);
    }

    @Test
    void shouldNotContainWhereStmt() {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper
                .statements(new StaticStatement("p1", "hello world"), new WhereStatement("w1 = :w1", "test"))
                .buildWhereStatement();

        assertThat(query).isEqualTo("p1 = ?1");
        assertThat(helper.values()).hasSize(1).contains("hello world");
    }

    @MethodSource
    @ParameterizedTest
    void buildQueries(String result, int paramSize, List<Statement> statements) {
        var helper1 = new PanacheDyanmicQueryHelper();
        var helper2 = new PanacheDyanmicQueryHelper();
        var query1 = helper1.statements(statements.toArray(Statement[]::new)).buildWhereStatement();
        var query2 = helper2.statements(statements.toArray(Statement[]::new)).buildWhereStatement(OR);

        assertThat(query1).isEqualTo(result.replace("_", ""));
        assertThat(query2).isEqualTo(result.replace("_and", "or"));
        assertThat(helper1.values()).hasSize(paramSize);
    }

    @ParameterizedTest
    @MethodSource("buildUpdateQueries")
    void buildUpdateQueriesWithoutWhere(String result, int paramSize, List<Statement> statements) {
        var helper = new PanacheDyanmicQueryHelper();
        var helper2 = new PanacheDyanmicQueryHelper();
        var helper3 = new PanacheDyanmicQueryHelper();
        var query = helper.statements(statements.toArray(Statement[]::new)).buildUpdateStatement(null);
        var query2 = helper2.statements(statements.toArray(Statement[]::new)).buildUpdateStatement(new WhereStatement("x = :x", ""));
        var query3 = helper3.statements(statements.toArray(Statement[]::new)).buildUpdateStatement(new WhereStatement("x = :x", null));

        assertThat(query).isEqualTo(result);
        assertThat(query2).isEqualTo(result);
        assertThat(query3).isEqualTo(result);
        assertThat(helper.values()).hasSize(paramSize);
        assertThat(helper2.values()).hasSize(paramSize);
        assertThat(helper3.values()).hasSize(paramSize);
    }

    @ParameterizedTest
    @MethodSource("buildUpdateQueries")
    void buildUpdateQueriesWithWhere(String result, int paramSize, List<Statement> statements) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper
                .statements(statements.toArray(Statement[]::new))
                .buildUpdateStatement(new WhereStatement("w1 = :w1 or w2 < :w2 and w3 = true", List.of(1, 2)));

        assertThat(query).contains("%s where ".formatted(result).trim());
        assertThat(helper.values()).hasSize(paramSize + 2);
    }

    private static List<Arguments> buildQueries() {
        var nullList = new ArrayList<>();
        nullList.add(1);
        nullList.add(null);

        return List.of(
                Arguments.of("id = ?1 _and (p2 > ?2 or p3 < ?3) _and p4 = ?4", 4, List.of(
                        new StaticStatement("id", 1),
                        new DynamicStatement("p2 > :p2 or p3 < :p3", List.of(2, 3)),
                        new StaticStatement("p4", 4)
                )),
                Arguments.of("p4 = ?1", 1, List.of(
                        new StaticStatement("id", null),
                        new DynamicStatement("p2 > :p2 or p3 < :p3", nullList),
                        new StaticStatement("x", ""),
                        new StaticStatement("p4", 1)
                )),
                Arguments.of("", 0, List.of(
                        new StaticStatement("id", ""),
                        new StaticStatement("p4", null)
                )),
                Arguments.of("(p1 = true and p2 > ?1 or p3 < ?2) _and p4 = ?3", 3, List.of(
                        new DynamicStatement("p1 = true and p2 > :p2 or p3 < :p3", List.of(2, 3)),
                        new StaticStatement("p4", 4)
                ))
        );
    }

    private static List<Arguments> buildUpdateQueries() {
        return List.of(
                Arguments.of("set id = ?1, p2 = true, p3 = ?2, p4 = ?3", 3, List.of(
                        new StaticStatement("id", 1),
                        new DynamicStatement("p2 = true, p3 = :p3", 3),
                        new StaticStatement("p4", 4)
                )),
                Arguments.of("", 0, List.of(
                        new StaticStatement("id", null),
                        new StaticStatement("p2", "")
                ))
        );
    }
}