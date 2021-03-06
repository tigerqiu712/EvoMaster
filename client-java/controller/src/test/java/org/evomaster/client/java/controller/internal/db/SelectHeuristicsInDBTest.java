package org.evomaster.client.java.controller.internal.db;

import io.restassured.http.ContentType;
import org.evomaster.client.java.controller.db.DataRow;
import org.evomaster.client.java.controller.db.SqlScriptRunner;
import org.evomaster.client.java.controller.InstrumentedSutStarter;
import org.evomaster.client.java.controller.db.DatabaseTestTemplate;
import org.evomaster.client.java.controller.db.QueryResult;
import org.evomaster.client.java.controller.api.dto.SutRunDto;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.evomaster.client.java.controller.api.ControllerConstants.*;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

public class SelectHeuristicsInDBTest extends DatabaseTestTemplate {




    @Test
    public void testBase() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");

        QueryResult res = SqlScriptRunner.execCommand(getConnection(), "select * from Foo");
        assertTrue(res.isEmpty());

        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (4)");

        res = SqlScriptRunner.execCommand(getConnection(), "select * from Foo");
        assertFalse(res.isEmpty());
    }


    @Test
    public void testParentheses() throws Exception{

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (5)");

        QueryResult res = SqlScriptRunner.execCommand(getConnection(), "select * from Foo where x = (5)");
        assertFalse(res.isEmpty());
    }


    @Test
    public void testConstants() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (4)");

        String select = "select x, 1 as y, null as z, 'bar' as w from Foo";

        QueryResult res = SqlScriptRunner.execCommand(getConnection(), select);
        assertFalse(res.isEmpty());

        DataRow row = res.seeRows().get(0);
        assertEquals(4, row.getValue(0));
        assertEquals(1, row.getValue(1));
        assertEquals(null, row.getValue(2));
        assertEquals("bar", row.getValue(3));
    }

    @Test
    public void testNested() throws Exception{

        String select = "select t.a, t.b from (select x as a, 1 as b from Foo where x<10) t where a>3";

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (1)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (4)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (7)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (20)");

        QueryResult res = SqlScriptRunner.execCommand(getConnection(), select);
        assertEquals(2, res.size());
    }


    @Test
    public void testHeuristic() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x) VALUES (10)");

        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);

            given().accept(ContentType.JSON)
                    .get(url + TEST_RESULTS)
                    .then()
                    .statusCode(200)
                    .body("data.extraHeuristics.size()", is(1))
                    .body("data.extraHeuristics[0].toMinimize.size()", is(0));

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), "SELECT x FROM Foo WHERE x = 12");
            SqlScriptRunner.execCommand(getConnection(), "SELECT x FROM Foo WHERE x = 10");

            given().accept(ContentType.JSON)
                    .get(url + TEST_RESULTS)
                    .then()
                    .statusCode(200)
                    .body("data.extraHeuristics.size()", is(1))
                    .body("data.extraHeuristics[0].toMinimize.size()", is(2))
                    .body("data.extraHeuristics[0].toMinimize[0]", greaterThan(0f))
                    .body("data.extraHeuristics[0].toMinimize[1]", is(0f));

            startNewActionInSameTest(url, 1);

            SqlScriptRunner.execCommand(getConnection(), "SELECT x FROM Foo WHERE x = 13");

            given().accept(ContentType.JSON)
                    .get(url + TEST_RESULTS)
                    .then()
                    .statusCode(200)
                    .body("data.extraHeuristics.size()", is(2))
                    .body("data.extraHeuristics[0].toMinimize.size()", is(2))
                    .body("data.extraHeuristics[0].toMinimize[0]", greaterThan(0f))
                    .body("data.extraHeuristics[0].toMinimize[1]", is(0f))
                    .body("data.extraHeuristics[1].toMinimize.size()", is(1))
                    .body("data.extraHeuristics[1].toMinimize[0]", greaterThan(0f));
        } finally {
            starter.stop();
        }
    }


    @Test
    public void testVarNotInSelect() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(x INT, y INT)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x, y) VALUES (0, 0)");

        int y = 42;
        String select = "select f.x from Foo f where f.y=" + y;


        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), select);

            double a = getFirstAndStartNew(url);
            assertTrue(a > 0d);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (x, y) VALUES (1, " + y + ")");
            SqlScriptRunner.execCommand(getConnection(), select);

            double b = getFirstAndStartNew(url);
            assertTrue(b < a);
            assertEquals(0d, b, 0.0001);

        } finally {
            starter.stop();
        }
    }

    @Test
    public void testInnerJoin() throws Exception {

        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Bar(id INT Primary Key, value INT)");
        SqlScriptRunner.execCommand(getConnection(), "CREATE TABLE Foo(id INT Primary Key, value INT, bar_id INT, " +
                "CONSTRAINT fk FOREIGN KEY (bar_id) REFERENCES Bar(id) )");

        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Bar (id, value) VALUES (0, 0)");
        SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, value, bar_id) VALUES (0, 0, 0)");

        int x = 10;
        int y = 20;

        String select = "select f.id, f.value, f.bar_id  from Foo f inner join Bar b on f.bar_id=b.id " +
                "where f.value=" + x + " and b.value=" + y + " limit 1";

        InstrumentedSutStarter starter = getInstrumentedSutStarter();

        try {
            String url = start(starter);
            url += BASE_PATH;

            startNewTest(url);

            SqlScriptRunner.execCommand(getConnection(), select);

            double a = getFirstAndStartNew(url);
            assertTrue(a > 0d);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, value, bar_id) VALUES (1, " + x + ", 0)");
            SqlScriptRunner.execCommand(getConnection(), select);

            double b = getFirstAndStartNew(url);
            assertTrue(b < a);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Bar (id, value) VALUES (1, " + y + ")");
            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, value, bar_id) VALUES (2, 0, 1)");
            SqlScriptRunner.execCommand(getConnection(), select);

            double c = getFirstAndStartNew(url);
            assertTrue(c < b);

            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Bar (id, value) VALUES (2, " + y + ")");
            SqlScriptRunner.execCommand(getConnection(), "INSERT INTO Foo (id, value, bar_id) VALUES (3, " + x + ", 2)");
            SqlScriptRunner.execCommand(getConnection(), select);

            double d = getFirstAndStartNew(url);
            assertTrue(d < c);
            assertEquals(0d, d, 0.0001);

        } finally {
            starter.stop();
        }
    }


    private void startNewActionInSameTest(String url, int index){

        given().accept(ContentType.ANY)
                .contentType(ContentType.JSON)
                .body("" + index)
                .put(url + NEW_ACTION)
                .then()
                .statusCode(204);
    }

    private void startNewTest(String url){

        given().accept(ContentType.ANY)
                .contentType(ContentType.JSON)
                .body(new SutRunDto(true, true))
                .put(url + RUN_SUT_PATH)
                .then()
                .statusCode(204);

        startNewActionInSameTest(url, 0);
    }

    private Double getFirstAndStartNew(String url) {

        double value = Double.parseDouble(given().accept(ContentType.JSON)
                .get(url + TEST_RESULTS)
                .then()
                .statusCode(200)
                .extract().body().path("data.extraHeuristics[0].toMinimize[0]").toString());

        startNewTest(url);

        return value;
    }

}
