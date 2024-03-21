/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.geospatial;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.trino.metadata.ResolvedFunction;
import io.trino.metadata.TestingFunctionResolution;
import io.trino.sql.ir.ComparisonExpression;
import io.trino.sql.ir.Constant;
import io.trino.sql.ir.FunctionCall;
import io.trino.sql.ir.LogicalExpression;
import io.trino.sql.ir.NotExpression;
import io.trino.sql.ir.SymbolReference;
import io.trino.sql.planner.Symbol;
import io.trino.sql.planner.iterative.rule.ExtractSpatialJoins.ExtractSpatialInnerJoin;
import io.trino.sql.planner.iterative.rule.test.RuleBuilder;
import io.trino.sql.planner.iterative.rule.test.RuleTester;
import org.junit.jupiter.api.Test;

import static io.trino.plugin.geospatial.GeometryType.GEOMETRY;
import static io.trino.plugin.geospatial.SphericalGeographyType.SPHERICAL_GEOGRAPHY;
import static io.trino.spi.type.DoubleType.DOUBLE;
import static io.trino.spi.type.IntegerType.INTEGER;
import static io.trino.spi.type.VarcharType.VARCHAR;
import static io.trino.sql.analyzer.TypeSignatureProvider.fromTypes;
import static io.trino.sql.ir.ComparisonExpression.Operator.GREATER_THAN;
import static io.trino.sql.ir.ComparisonExpression.Operator.LESS_THAN;
import static io.trino.sql.ir.ComparisonExpression.Operator.NOT_EQUAL;
import static io.trino.sql.ir.LogicalExpression.Operator.AND;
import static io.trino.sql.planner.assertions.PlanMatchPattern.expression;
import static io.trino.sql.planner.assertions.PlanMatchPattern.project;
import static io.trino.sql.planner.assertions.PlanMatchPattern.spatialJoin;
import static io.trino.sql.planner.assertions.PlanMatchPattern.values;
import static io.trino.sql.planner.plan.JoinType.INNER;

public class TestExtractSpatialInnerJoin
        extends AbstractTestExtractSpatial
{
    private static final TestingFunctionResolution FUNCTIONS = new TestingFunctionResolution(new GeoPlugin());
    private static final ResolvedFunction ST_CONTAINS = FUNCTIONS.resolveFunction("st_contains", fromTypes(GEOMETRY, GEOMETRY));
    private static final ResolvedFunction ST_POINT = FUNCTIONS.resolveFunction("st_point", fromTypes(DOUBLE, DOUBLE));
    private static final ResolvedFunction ST_GEOMETRY_FROM_TEXT = FUNCTIONS.resolveFunction("st_geometryfromtext", fromTypes(VARCHAR));

    @Test
    public void testDoesNotFire()
    {
        // scalar expression
        assertRuleApplication()
                .on(p ->
                        p.filter(
                                containsCall(geometryFromTextCall("POLYGON ..."), p.symbol("b").toSymbolReference()),
                                p.join(INNER,
                                        p.values(),
                                        p.values(p.symbol("b")))))
                .doesNotFire();

        // OR operand
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    Symbol point = p.symbol("point", GEOMETRY);
                    Symbol name1 = p.symbol("name_1");
                    Symbol name2 = p.symbol("name_2");
                    return p.filter(
                            LogicalExpression.or(
                                    containsCall(geometryFromTextCall(wkt), point.toSymbolReference()),
                                    new ComparisonExpression(NOT_EQUAL, name1.toSymbolReference(), name2.toSymbolReference())),
                            p.join(INNER, p.values(wkt, name1), p.values(point, name2)));
                })
                .doesNotFire();

        // NOT operator
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    Symbol point = p.symbol("point", GEOMETRY);
                    Symbol name1 = p.symbol("name_1");
                    Symbol name2 = p.symbol("name_2");
                    return p.filter(
                            new NotExpression(containsCall(geometryFromTextCall(wkt), point.toSymbolReference())),
                            p.join(INNER,
                                    p.values(wkt, name1),
                                    p.values(point, name2)));
                })
                .doesNotFire();

        // ST_Distance(...) > r
        assertRuleApplication()
                .on(p ->
                {
                    Symbol a = p.symbol("a", GEOMETRY);
                    Symbol b = p.symbol("b", GEOMETRY);
                    return p.filter(
                            new ComparisonExpression(GREATER_THAN, distanceCall(a.toSymbolReference(), b.toSymbolReference()), new Constant(INTEGER, 5L)),
                            p.join(INNER,
                                    p.values(a),
                                    p.values(b)));
                })
                .doesNotFire();

        // SphericalGeography operand
        assertRuleApplication()
                .on(p ->
                {
                    Symbol a = p.symbol("a", SPHERICAL_GEOGRAPHY);
                    Symbol b = p.symbol("b", SPHERICAL_GEOGRAPHY);
                    return p.filter(
                            new ComparisonExpression(LESS_THAN, sphericalDistanceCall(a.toSymbolReference(), b.toSymbolReference()), new Constant(INTEGER, 5L)),
                            p.join(INNER,
                                    p.values(a),
                                    p.values(b)));
                })
                .doesNotFire();

        // to_spherical_geography() operand
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    Symbol point = p.symbol("point", SPHERICAL_GEOGRAPHY);
                    return p.filter(
                            new ComparisonExpression(LESS_THAN, sphericalDistanceCall(toSphericalGeographyCall(wkt), point.toSymbolReference()), new Constant(INTEGER, 5L)),
                            p.join(INNER,
                                    p.values(wkt),
                                    p.values(point)));
                })
                .doesNotFire();
    }

    @Test
    public void testConvertToSpatialJoin()
    {
        // symbols
        assertRuleApplication()
                .on(p ->
                {
                    Symbol a = p.symbol("a");
                    Symbol b = p.symbol("b");
                    return p.filter(
                            containsCall(a.toSymbolReference(), b.toSymbolReference()),
                            p.join(INNER,
                                    p.values(a),
                                    p.values(b)));
                })
                .matches(
                        spatialJoin(
                                new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "a"), new SymbolReference(GEOMETRY, "b"))),
                                values(ImmutableMap.of("a", 0)),
                                values(ImmutableMap.of("b", 0))));

        // AND
        assertRuleApplication()
                .on(p ->
                {
                    Symbol a = p.symbol("a", GEOMETRY);
                    Symbol b = p.symbol("b", GEOMETRY);
                    Symbol name1 = p.symbol("name_1");
                    Symbol name2 = p.symbol("name_2");
                    return p.filter(
                            LogicalExpression.and(
                                    new ComparisonExpression(NOT_EQUAL, name1.toSymbolReference(), name2.toSymbolReference()),
                                    containsCall(a.toSymbolReference(), b.toSymbolReference())),
                            p.join(INNER,
                                    p.values(a, name1),
                                    p.values(b, name2)));
                })
                .matches(
                        spatialJoin(
                                new LogicalExpression(AND, ImmutableList.of(new ComparisonExpression(NOT_EQUAL, new SymbolReference(VARCHAR, "name_1"), new SymbolReference(VARCHAR, "name_2")), new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "a"), new SymbolReference(GEOMETRY, "b"))))),
                                values(ImmutableMap.of("a", 0, "name_1", 1)),
                                values(ImmutableMap.of("b", 0, "name_2", 1))));

        // AND
        assertRuleApplication()
                .on(p ->
                {
                    Symbol a1 = p.symbol("a1");
                    Symbol a2 = p.symbol("a2");
                    Symbol b1 = p.symbol("b1");
                    Symbol b2 = p.symbol("b2");
                    return p.filter(
                            LogicalExpression.and(
                                    containsCall(a1.toSymbolReference(), b1.toSymbolReference()),
                                    containsCall(a2.toSymbolReference(), b2.toSymbolReference())),
                            p.join(INNER,
                                    p.values(a1, a2),
                                    p.values(b1, b2)));
                })
                .matches(
                        spatialJoin(
                                new LogicalExpression(AND, ImmutableList.of(new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "a1"), new SymbolReference(GEOMETRY, "b1"))), new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "a2"), new SymbolReference(GEOMETRY, "b2"))))),
                                values(ImmutableMap.of("a1", 0, "a2", 1)),
                                values(ImmutableMap.of("b1", 0, "b2", 1))));
    }

    @Test
    public void testPushDownFirstArgument()
    {
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    Symbol point = p.symbol("point", GEOMETRY);
                    return p.filter(
                            containsCall(geometryFromTextCall(wkt), point.toSymbolReference()),
                            p.join(INNER,
                                    p.values(wkt),
                                    p.values(point)));
                })
                .matches(
                        spatialJoin(
                                new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "st_geometryfromtext"), new SymbolReference(GEOMETRY, "point"))),
                                project(ImmutableMap.of("st_geometryfromtext", expression(new FunctionCall(ST_GEOMETRY_FROM_TEXT, ImmutableList.of(new SymbolReference(VARCHAR, "wkt"))))),
                                        values(ImmutableMap.of("wkt", 0))),
                                values(ImmutableMap.of("point", 0))));

        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    return p.filter(
                            containsCall(geometryFromTextCall(wkt), toPointCall(new Constant(INTEGER, 0L), new Constant(INTEGER, 0L))),
                            p.join(INNER,
                                    p.values(wkt),
                                    p.values()));
                })
                .doesNotFire();
    }

    @Test
    public void testPushDownSecondArgument()
    {
        assertRuleApplication()
                .on(p ->
                {
                    Symbol polygon = p.symbol("polygon", GEOMETRY);
                    Symbol lat = p.symbol("lat");
                    Symbol lng = p.symbol("lng");
                    return p.filter(
                            containsCall(polygon.toSymbolReference(), toPointCall(lng.toSymbolReference(), lat.toSymbolReference())),
                            p.join(INNER,
                                    p.values(polygon),
                                    p.values(lat, lng)));
                })
                .matches(
                        spatialJoin(
                                new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "polygon"), new SymbolReference(GEOMETRY, "st_point"))),
                                values(ImmutableMap.of("polygon", 0)),
                                project(ImmutableMap.of("st_point", expression(new FunctionCall(ST_POINT, ImmutableList.of(new SymbolReference(DOUBLE, "lng"), new SymbolReference(DOUBLE, "lat"))))),
                                        values(ImmutableMap.of("lat", 0, "lng", 1)))));

        assertRuleApplication()
                .on(p ->
                {
                    Symbol lat = p.symbol("lat");
                    Symbol lng = p.symbol("lng");
                    return p.filter(
                            containsCall(geometryFromTextCall("POLYGON ..."), toPointCall(lng.toSymbolReference(), lat.toSymbolReference())),
                            p.join(INNER,
                                    p.values(),
                                    p.values(lat, lng)));
                })
                .doesNotFire();
    }

    @Test
    public void testPushDownBothArguments()
    {
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    Symbol lat = p.symbol("lat");
                    Symbol lng = p.symbol("lng");
                    return p.filter(
                            containsCall(geometryFromTextCall(wkt), toPointCall(lng.toSymbolReference(), lat.toSymbolReference())),
                            p.join(INNER,
                                    p.values(wkt),
                                    p.values(lat, lng)));
                })
                .matches(
                        spatialJoin(
                                new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "st_geometryfromtext"), new SymbolReference(GEOMETRY, "st_point"))),
                                project(ImmutableMap.of("st_geometryfromtext", expression(new FunctionCall(ST_GEOMETRY_FROM_TEXT, ImmutableList.of(new SymbolReference(VARCHAR, "wkt"))))),
                                        values(ImmutableMap.of("wkt", 0))),
                                project(ImmutableMap.of("st_point", expression(new FunctionCall(ST_POINT, ImmutableList.of(new SymbolReference(DOUBLE, "lng"), new SymbolReference(DOUBLE, "lat"))))),
                                        values(ImmutableMap.of("lat", 0, "lng", 1)))));
    }

    @Test
    public void testPushDownOppositeOrder()
    {
        assertRuleApplication()
                .on(p ->
                {
                    Symbol lat = p.symbol("lat");
                    Symbol lng = p.symbol("lng");
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    return p.filter(
                            containsCall(geometryFromTextCall(wkt), toPointCall(lng.toSymbolReference(), lat.toSymbolReference())),
                            p.join(INNER,
                                    p.values(lat, lng),
                                    p.values(wkt)));
                })
                .matches(
                        spatialJoin(new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "st_geometryfromtext"), new SymbolReference(GEOMETRY, "st_point"))),
                                project(ImmutableMap.of("st_point", expression(new FunctionCall(ST_POINT, ImmutableList.of(new SymbolReference(DOUBLE, "lng"), new SymbolReference(DOUBLE, "lat"))))),
                                        values(ImmutableMap.of("lat", 0, "lng", 1))),
                                project(ImmutableMap.of("st_geometryfromtext", expression(new FunctionCall(ST_GEOMETRY_FROM_TEXT, ImmutableList.of(new SymbolReference(VARCHAR, "wkt"))))),
                                        values(ImmutableMap.of("wkt", 0)))));
    }

    @Test
    public void testPushDownAnd()
    {
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt = p.symbol("wkt", VARCHAR);
                    Symbol lat = p.symbol("lat");
                    Symbol lng = p.symbol("lng");
                    Symbol name1 = p.symbol("name_1");
                    Symbol name2 = p.symbol("name_2");
                    return p.filter(
                            LogicalExpression.and(
                                    new ComparisonExpression(NOT_EQUAL, name1.toSymbolReference(), name2.toSymbolReference()),
                                    containsCall(geometryFromTextCall(wkt), toPointCall(lng.toSymbolReference(), lat.toSymbolReference()))),
                            p.join(INNER,
                                    p.values(wkt, name1),
                                    p.values(lat, lng, name2)));
                })
                .matches(
                        spatialJoin(
                                new LogicalExpression(AND, ImmutableList.of(new ComparisonExpression(NOT_EQUAL, new SymbolReference(VARCHAR, "name_1"), new SymbolReference(VARCHAR, "name_2")), new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "st_geometryfromtext"), new SymbolReference(GEOMETRY, "st_point"))))),
                                project(ImmutableMap.of("st_geometryfromtext", expression(new FunctionCall(ST_GEOMETRY_FROM_TEXT, ImmutableList.of(new SymbolReference(VARCHAR, "wkt"))))),
                                        values(ImmutableMap.of("wkt", 0, "name_1", 1))),
                                project(ImmutableMap.of("st_point", expression(new FunctionCall(ST_POINT, ImmutableList.of(new SymbolReference(DOUBLE, "lng"), new SymbolReference(DOUBLE, "lat"))))),
                                        values(ImmutableMap.of("lat", 0, "lng", 1, "name_2", 2)))));

        // Multiple spatial functions - only the first one is being processed
        assertRuleApplication()
                .on(p ->
                {
                    Symbol wkt1 = p.symbol("wkt1", VARCHAR);
                    Symbol wkt2 = p.symbol("wkt2", VARCHAR);
                    Symbol geometry1 = p.symbol("geometry1");
                    Symbol geometry2 = p.symbol("geometry2");
                    return p.filter(
                            LogicalExpression.and(
                                    containsCall(geometryFromTextCall(wkt1), geometry1.toSymbolReference()),
                                    containsCall(geometryFromTextCall(wkt2), geometry2.toSymbolReference())),
                            p.join(INNER,
                                    p.values(wkt1, wkt2),
                                    p.values(geometry1, geometry2)));
                })
                .matches(
                        spatialJoin(
                                new LogicalExpression(AND, ImmutableList.of(new FunctionCall(ST_CONTAINS, ImmutableList.of(new SymbolReference(GEOMETRY, "st_geometryfromtext"), new SymbolReference(GEOMETRY, "geometry1"))), new FunctionCall(ST_CONTAINS, ImmutableList.of(new FunctionCall(ST_GEOMETRY_FROM_TEXT, ImmutableList.of(new SymbolReference(GEOMETRY, "wkt2"))), new SymbolReference(GEOMETRY, "geometry2"))))),
                                project(ImmutableMap.of("st_geometryfromtext", expression(new FunctionCall(ST_GEOMETRY_FROM_TEXT, ImmutableList.of(new SymbolReference(GEOMETRY, "wkt1"))))),
                                        values(ImmutableMap.of("wkt1", 0, "wkt2", 1))),
                                values(ImmutableMap.of("geometry1", 0, "geometry2", 1))));
    }

    private RuleBuilder assertRuleApplication()
    {
        RuleTester tester = tester();
        return tester.assertThat(new ExtractSpatialInnerJoin(tester.getPlannerContext(), tester.getSplitManager(), tester.getPageSourceManager()));
    }
}
