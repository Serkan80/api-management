package nl.probot.apim.core.commons.jpa;

import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.DynamicStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.Statement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static nl.probot.apim.commons.jpa.QuerySeparator.OR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.commons.util.StringUtils.isNotBlank;

class PanacheDyanmicQueryHelperTest {

    @Test
    void empty() {
        var helper = new PanacheDyanmicQueryHelper();
        var helper2 = new PanacheDyanmicQueryHelper();
        var query1 = helper.buildWhereStatement();
        var query2 = helper.buildUpdateStatement(null);
        var query3 = helper2.buildUpdateStatement(new WhereStatement("w1 = :w1", 1));

        assertThat(query1).isBlank();
        assertThat(query2).isBlank();
        assertThat(query3).isBlank();
        assertThat(helper.values()).isEmpty();
        assertThat(helper2.values()).isEmpty();
    }

    @Test
    void allowBlankValues() {
        var helper = new PanacheDyanmicQueryHelper()
                .allowBlankValues()
                .statements(new StaticStatement("p1", ""), new StaticStatement("p2", null), new StaticStatement("p3", "hello"));

        assertThat(helper.buildWhereStatement()).isEqualTo("p1 = ?1 and p3 = ?2");
        assertThat(helper.buildUpdateStatement(null)).isEqualTo("set p1 = ?1, p3 = ?2");
        assertThat(helper.buildUpdateStatement(new WhereStatement("w1 = :w1", null))).isEqualTo("set p1 = ?1, p3 = ?2");
        assertThat(helper.values()).hasSize(2).contains("", "hello");
        assertThat(helper.buildUpdateStatement(new WhereStatement("w1 = :w1", ""))).isEqualTo("set p1 = ?1, p3 = ?2 where w1 = ?3");
        assertThat(helper.values()).hasSize(3).contains("", "hello");
    }

    @Test
    @DisplayName("""
                Where statement is not allowed in .statements(..) and there should only be 1 where statement. 
                It is only allowed within the .buildXyz(..) method.
            """)
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

        // when there are no statements, then the where stmt should not be generated
        assertThat(query).contains("%s %s ".formatted(result, isNotBlank(result) ? "where" : "").trim());
        assertThat(helper.values()).hasSize(paramSize + (isNotBlank(result) ? 2 : 0));
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