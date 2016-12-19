/*
 * Copyright 2014 - 2016 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.testsuite;

import static com.googlecode.catchexception.CatchException.caughtException;
import static com.googlecode.catchexception.CatchException.verifyException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;

import com.blazebit.persistence.FullSelectCTECriteriaBuilder;
import com.blazebit.persistence.PagedList;
import com.blazebit.persistence.PaginatedCriteriaBuilder;
import com.blazebit.persistence.testsuite.base.category.*;
import com.blazebit.persistence.testsuite.entity.TestAdvancedCTE1;
import com.blazebit.persistence.testsuite.entity.TestAdvancedCTE2;
import com.blazebit.persistence.testsuite.tx.TxVoidWork;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.testsuite.entity.RecursiveEntity;
import com.blazebit.persistence.testsuite.entity.TestCTE;

/**
 *
 * @author Christian Beikov
 * @since 1.1.0
 */
public class CTETest extends AbstractCoreTest {
    
    @Override
    protected Class<?>[] getEntityClasses() {
        return new Class<?>[] {
            RecursiveEntity.class,
            TestCTE.class,
            TestAdvancedCTE1.class,
            TestAdvancedCTE2.class
        };
    }

    @Before
    public void setUp() {
        transactional(new TxVoidWork() {
            @Override
            public void work(EntityManager em) {
                RecursiveEntity root1 = new RecursiveEntity("root1");
                RecursiveEntity child1_1 = new RecursiveEntity("child1_1", root1);
                RecursiveEntity child1_2 = new RecursiveEntity("child1_2", root1);

                RecursiveEntity child1_1_1 = new RecursiveEntity("child1_1_1", child1_1);
                RecursiveEntity child1_2_1 = new RecursiveEntity("child1_2_1", child1_2);

                em.persist(root1);
                em.persist(child1_1);
                em.persist(child1_2);
                em.persist(child1_1_1);
                em.persist(child1_2_1);
            }
        });
    }

    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testNotFullyBoundCTE() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class, "t");
        FullSelectCTECriteriaBuilder<CriteriaBuilder<TestCTE>> fullSelectCTECriteriaBuilder = cb.with(TestCTE.class)
                .from(RecursiveEntity.class, "e")
                .bind("id").select("e.id");

        verifyException(fullSelectCTECriteriaBuilder, IllegalStateException.class).end();
        // Assert that these columns haven't been bound
        assertTrue(caughtException().getMessage().contains("name"));
        assertTrue(caughtException().getMessage().contains("nesting_level"));
    }

    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testNotDefinedCTE() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class, "t");

        verifyException(cb, IllegalStateException.class).getQueryString();
        // Assert the undefined cte
        assertTrue(caughtException().getMessage().contains(TestCTE.class.getName()));
    }
    
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testCTE() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class, "t").where("t.level").ltExpression("2");
        cb.with(TestCTE.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("0")
            .where("e.parent").isNull()
        .end();
        String expected = ""
                + "WITH " + TestCTE.class.getSimpleName() + "(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\n)\n"
                + "SELECT t FROM " + TestCTE.class.getSimpleName() + " t WHERE t.level < 2";
        
        assertEquals(expected, cb.getQueryString());
        List<TestCTE> resultList = cb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals("root1", resultList.get(0).getName());
    }

    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testCTEAdvanced() {
        CriteriaBuilder<TestAdvancedCTE1> cb = cbf.create(em, TestAdvancedCTE1.class, "t").where("t.level").ltExpression("2");
        cb.with(TestAdvancedCTE1.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("embeddable.name").select("e.name")
            .bind("embeddable.description").select("''")
            .bind("embeddable.recursiveEntity").select("e")
            .bind("level").select("0")
            .bind("parent").select("e.parent")
            .where("e.parent").isNull()
        .end();
        String expected = ""
                + "WITH " + TestAdvancedCTE1.class.getSimpleName() + "(id, embeddable.name, embeddable.description, embeddable.recursiveEntity, level, parent) AS(\n"
                // NOTE: The parent relation select gets transformed to an id select!
                + "SELECT e.id, e.name, '', e.id, 0, e.parent.id FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\n)\n"
                + "SELECT t FROM " + TestAdvancedCTE1.class.getSimpleName() + " t WHERE t.level < 2";

        assertEquals(expected, cb.getQueryString());
        List<TestAdvancedCTE1> resultList = cb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals("root1", resultList.get(0).getEmbeddable().getName());
    }

    // NOTE: Apparently H2 doesn't like limit in CTEs
    @Test
    @Category({ NoH2.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testCTELimit() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class, "t");
        cb.with(TestCTE.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("0")
            .where("e.parent").isNull()
            .orderByAsc("e.id")
            .setMaxResults(1)
        .end();
        String expected = ""
                + "WITH " + TestCTE.class.getSimpleName() + "(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL ORDER BY " + renderNullPrecedence("e.id", "ASC", "LAST") + " LIMIT 1"
                + "\n)\n"
                + "SELECT t FROM " + TestCTE.class.getSimpleName() + " t";
        
        assertEquals(expected, cb.getQueryString());
        List<TestCTE> resultList = cb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals("root1", resultList.get(0).getName());
    }

    // TODO: Oracle requires a cycle clause #295
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class, NoOracle.class })
    public void testRecursiveCTE() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class, "t").where("t.level").ltExpression("2");
        cb.withRecursive(TestCTE.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("0")
            .where("e.parent").isNull()
        .unionAll()
            .from(TestCTE.class, "t")
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("t.level + 1")
            .where("t.id").eqExpression("e.parent.id")
        .end();
        String expected = ""
                + "WITH RECURSIVE " + TestCTE.class.getSimpleName() + "(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM " + TestCTE.class.getSimpleName() + " t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT t FROM " + TestCTE.class.getSimpleName() + " t WHERE t.level < 2";
        
        assertEquals(expected, cb.getQueryString());
        List<TestCTE> resultList = cb.getResultList();
        assertEquals(3, resultList.size());
        assertEquals("root1", resultList.get(0).getName());
    }

    // TODO: Oracle requires a cycle clause #295
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class, NoOracle.class })
    public void testRecursiveCTEAdvanced() {
        CriteriaBuilder<TestAdvancedCTE1> cb = cbf.create(em, TestAdvancedCTE1.class, "t").where("t.level").ltExpression("2");
        cb.withRecursive(TestAdvancedCTE1.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("embeddable.name").select("e.name")
            .bind("embeddable.description").select("''")
            .bind("embeddable.recursiveEntity").select("e")
            .bind("level").select("0")
            .bind("parentId").select("e.parent.id")
            .where("e.parent").isNull()
        .unionAll()
            .from(TestAdvancedCTE1.class, "t")
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("embeddable.name").select("e.name")
            .bind("embeddable.description").select("''")
            .bind("embeddable.recursiveEntity").select("e")
            .bind("level").select("t.level + 1")
            .bind("parent").select("e.parent")
            .where("t.id").eqExpression("e.parent.id")
        .end();
        String expected = ""
                + "WITH RECURSIVE " + TestAdvancedCTE1.class.getSimpleName() + "(id, embeddable.name, embeddable.description, embeddable.recursiveEntity, level, parentId) AS(\n"
                + "SELECT e.id, e.name, '', e.id, 0, e.parent.id FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                // NOTE: The parent relation select gets transformed to an id select!
                + "SELECT e.id, e.name, '', e.id, t.level + 1, e.parent.id FROM " + TestAdvancedCTE1.class.getSimpleName() + " t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT t FROM " + TestAdvancedCTE1.class.getSimpleName() + " t WHERE t.level < 2";

        assertEquals(expected, cb.getQueryString());
        List<TestAdvancedCTE1> resultList = cb.getResultList();
        assertEquals(3, resultList.size());
        assertEquals("root1", resultList.get(0).getEmbeddable().getName());
    }

    // TODO: Oracle requires a cycle clause #295
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class, NoOracle.class })
    public void testRecursiveCTEPagination() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class);
        cb.withRecursive(TestCTE.class)
                .from(RecursiveEntity.class, "e")
                .bind("id").select("e.id")
                .bind("name").select("e.name")
                .bind("level").select("0")
                .where("e.parent").isNull()
        .unionAll()
                .from(TestCTE.class, "t")
                .from(RecursiveEntity.class, "e")
                .bind("id").select("e.id")
                .bind("name").select("e.name")
                .bind("level").select("t.level + 1")
                .where("t.id").eqExpression("e.parent.id")
        .end();
        cb.from(TestCTE.class, "t")
                .where("t.level").ltExpression("2")
                .orderByAsc("t.level")
                .orderByAsc("t.id");

        PaginatedCriteriaBuilder<TestCTE> pcb = cb.page(0, 1);

        String expectedCountQuery = ""
                + "WITH RECURSIVE " + TestCTE.class.getSimpleName() + "(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM " + TestCTE.class.getSimpleName() + " t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT " + countPaginated("t.id", false) + " FROM " + TestCTE.class.getSimpleName() + " t WHERE t.level < 2";

        String expectedObjectQuery = ""
                + "WITH RECURSIVE " + TestCTE.class.getSimpleName() + "(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM " + TestCTE.class.getSimpleName() + " t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT t FROM " + TestCTE.class.getSimpleName() + " t WHERE t.level < 2 ORDER BY " + renderNullPrecedence("t.level", "ASC", "LAST") + ", " + renderNullPrecedence("t.id", "ASC", "LAST");

        assertEquals(expectedCountQuery, pcb.getPageCountQueryString());
        assertEquals(expectedObjectQuery, pcb.getQueryString());

        PagedList<TestCTE> resultList = pcb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals(3, resultList.getTotalSize());
        assertEquals("root1", resultList.get(0).getName());

        pcb = cb.page(1, 1);
        resultList = pcb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals(3, resultList.getTotalSize());
        assertEquals("child1_1", resultList.get(0).getName());

        pcb = cb.page(2, 1);
        resultList = pcb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals(3, resultList.getTotalSize());
        assertEquals("child1_2", resultList.get(0).getName());

        pcb = cb.page(0, 2);
        resultList = pcb.getResultList();
        assertEquals(2, resultList.size());
        assertEquals(3, resultList.getTotalSize());
        assertEquals("root1", resultList.get(0).getName());
        assertEquals("child1_1", resultList.get(1).getName());
    }

    // NOTE: Apparently H2 produces wrong results when a CTE is used with IN predicate
    // TODO: Oracle requires a cycle clause #295
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class, NoH2.class, NoOracle.class })
    public void testRecursiveCTEPaginationIdQuery() {
        CriteriaBuilder<Tuple> cb = cbf.create(em, Tuple.class);
        cb.withRecursive(TestCTE.class)
        .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("0")
            .where("e.parent").isNull()
        .unionAll()
            .from(TestCTE.class, "t")
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("t.level + 1")
            .where("t.id").eqExpression("e.parent.id")
        .end();
        cb.from(RecursiveEntity.class, "r")
            .select("r.name")
            .select("r.children.name")
            .where("r.id").in()
                .from(TestCTE.class, "t")
                .select("t.id")
                .where("t.level").ltExpression("2")
            .end()
            .orderByAsc("r.id");

        PaginatedCriteriaBuilder<Tuple> pcb = cb.page(0, 2);

        String expectedCountQuery = ""
                + "WITH RECURSIVE TestCTE(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM TestCTE t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT " + countPaginated("r.id", false) + " FROM RecursiveEntity r WHERE r.id IN (SELECT t.id FROM TestCTE t WHERE t.level < 2)";

        // TODO: check if we can somehow infer that the order by of children.id is unnecessary and thus be able to omit the join
        String expectedIdQuery = ""
                + "WITH RECURSIVE TestCTE(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM TestCTE t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT r.id FROM RecursiveEntity r WHERE r.id IN (SELECT t.id FROM TestCTE t WHERE t.level < 2) GROUP BY r.id ORDER BY " + renderNullPrecedence("r.id", "ASC", "LAST");

        String expectedObjectQuery = ""
                + "WITH RECURSIVE TestCTE(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM TestCTE t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT r.name, children_1.name FROM RecursiveEntity r LEFT JOIN r.children children_1 WHERE r.id IN :ids ORDER BY " + renderNullPrecedence("r.id", "ASC", "LAST");

        assertEquals(expectedCountQuery, pcb.getPageCountQueryString());
        assertEquals(expectedIdQuery, pcb.getPageIdQueryString());
        assertEquals(expectedObjectQuery, pcb.getQueryString());

        PagedList<Tuple> resultList = pcb.getResultList();
        // Well, it's a tuple and contains collections, so result list contains "more" results
        assertEquals(3, resultList.size());
        assertEquals(3, resultList.getTotalSize());

        // Unfortunately we can't specify order by just for the object query to determinisitcally test this and a comparator is too much, so we do it like that
        if ("child1_1".equals(resultList.get(0).get(1))) {
            assertEquals("root1", resultList.get(0).get(0));
            assertEquals("child1_1", resultList.get(0).get(1));

            assertEquals("root1", resultList.get(1).get(0));
            assertEquals("child1_2", resultList.get(1).get(1));
        } else {
            assertEquals("root1", resultList.get(0).get(0));
            assertEquals("child1_2", resultList.get(0).get(1));

            assertEquals("root1", resultList.get(1).get(0));
            assertEquals("child1_1", resultList.get(1).get(1));
        }

        assertEquals("child1_1", resultList.get(2).get(0));
        assertEquals("child1_1_1", resultList.get(2).get(1));
    }

    // NOTE: Entity joins are supported since Hibernate 5.1, Datanucleus 5 and latest Eclipselink
    // TODO: Oracle requires a cycle clause #295
    @Test
    @Category({ NoHibernate42.class, NoHibernate43.class, NoHibernate50.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class, NoOracle.class })
    public void testRecursiveCTEPaginationIdQueryLeftJoin() {
        CriteriaBuilder<Tuple> cb = cbf.create(em, Tuple.class);
        cb.withRecursive(TestCTE.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("0")
            .where("e.parent").isNull()
        .unionAll()
            .from(TestCTE.class, "t")
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("t.level + 1")
            .where("t.id").eqExpression("e.parent.id")
        .end();
        cb.from(RecursiveEntity.class, "r")
            .select("r.name")
            .select("r.children.name")
            .innerJoinOn(TestCTE.class, "t")
                .on("r.id").eqExpression("t.id")
                .on("t.level").ltExpression("2")
            .end()
            .orderByAsc("r.id");

        PaginatedCriteriaBuilder<Tuple> pcb = cb.page(0, 2);

        String expectedCountQuery = ""
                + "WITH RECURSIVE TestCTE(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM TestCTE t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT " + countPaginated("r.id", true) + " FROM RecursiveEntity r JOIN TestCTE t " + ON_CLAUSE + " r.id = t.id AND t.level < 2";

        String expectedIdQuery = ""
                + "WITH RECURSIVE TestCTE(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM TestCTE t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT r.id FROM RecursiveEntity r JOIN TestCTE t " + ON_CLAUSE + " r.id = t.id AND t.level < 2 GROUP BY r.id ORDER BY " + renderNullPrecedence("r.id", "ASC", "LAST");

        String expectedObjectQuery = ""
                + "WITH RECURSIVE TestCTE(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\nUNION ALL\n"
                + "SELECT e.id, e.name, t.level + 1 FROM TestCTE t, RecursiveEntity e WHERE t.id = e.parent.id"
                + "\n)\n"
                + "SELECT r.name, children_1.name FROM RecursiveEntity r LEFT JOIN r.children children_1 JOIN TestCTE t ON r.id = t.id AND t.level < 2 WHERE r.id IN :ids ORDER BY " + renderNullPrecedence("r.id", "ASC", "LAST");

        assertEquals(expectedCountQuery, pcb.getPageCountQueryString());
        assertEquals(expectedIdQuery, pcb.getPageIdQueryString());
        assertEquals(expectedObjectQuery, pcb.getQueryString());

        PagedList<Tuple> resultList = pcb.getResultList();
        // Well, it's a tuple and contains collections, so result list contains "more" results
        assertEquals(3, resultList.size());
        assertEquals(3, resultList.getTotalSize());
        // Unfortunately we can't specify order by just for the object query to determinisitcally test this and a comparator is too much, so we do it like that
        if ("child1_1".equals(resultList.get(0).get(1))) {
            assertEquals("root1", resultList.get(0).get(0));
            assertEquals("child1_1", resultList.get(0).get(1));

            assertEquals("root1", resultList.get(1).get(0));
            assertEquals("child1_2", resultList.get(1).get(1));
        } else {
            assertEquals("root1", resultList.get(0).get(0));
            assertEquals("child1_2", resultList.get(0).get(1));

            assertEquals("root1", resultList.get(1).get(0));
            assertEquals("child1_1", resultList.get(1).get(1));
        }
    }
    
    // NOTE: Apparently H2 can't handle multiple CTEs
    @Test
    @Category({ NoH2.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testCTEInSubquery() {
        CriteriaBuilder<String> cb = cbf.create(em, String.class)
            .from(RecursiveEntity.class, "r")
            .where("r.id").in()
                .from(TestCTE.class, "a")
                .where("a.level").ltExpression("2")
                .select("a.id")
            .end()
            .select("r.name");
        cb.with(TestCTE.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .bind("level").select("0")
            .where("e.parent").isNull()
        .end();
        String expected = ""
                + "WITH " + TestCTE.class.getSimpleName() + "(id, name, level) AS(\n"
                + "SELECT e.id, e.name, 0 FROM RecursiveEntity e WHERE e.parent IS NULL"
                + "\n)\n"
                + "SELECT r.name FROM RecursiveEntity r WHERE r.id IN (SELECT a.id FROM " + TestCTE.class.getSimpleName() + " a WHERE a.level < 2)";
        
        assertEquals(expected, cb.getQueryString());
        List<String> resultList = cb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals("root1", resultList.get(0));
    }

    // NOTE: Apparently H2 can't handle multiple CTEs
    @Test
    @Category({ NoH2.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testBindEmbeddable() {
        CriteriaBuilder<TestAdvancedCTE2> cb = cbf.create(em, TestAdvancedCTE2.class);
        cb.with(TestAdvancedCTE1.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("embeddable.name").select("e.name")
            .bind("embeddable.description").select("''")
            .bind("embeddable.recursiveEntity").select("e")
            .bind("level").select("0")
            .bind("parent").select("e.parent")
        .end()
        .with(TestAdvancedCTE2.class)
            .from(TestAdvancedCTE1.class)
            .bind("id").select("id")
            .bind("embeddable").select("embeddable")
        .end()
        .orderByAsc("id");
        String expected = ""
                + "WITH " + TestAdvancedCTE1.class.getSimpleName() + "(id, embeddable.name, embeddable.description, embeddable.recursiveEntity, level, parent) AS(\n"
                + "SELECT e.id, e.name, '', e.id, 0, e.parent.id FROM RecursiveEntity e\n"
                + "), " + TestAdvancedCTE2.class.getSimpleName() + "(id, embeddable) AS(\n" +
                "SELECT testadvancedcte1.id, testadvancedcte1.embeddable FROM TestAdvancedCTE1 testadvancedcte1\n" +
                ")\n"
                + "SELECT testadvancedcte2 FROM TestAdvancedCTE2 testadvancedcte2 ORDER BY " + renderNullPrecedence("testadvancedcte2.id", "ASC", "LAST");

        assertEquals(expected, cb.getQueryString());
        List<TestAdvancedCTE2> results = cb.getResultList();
        assertEquals(5, results.size());
        for (TestAdvancedCTE2 result : results) {
            assertEquals("", result.getEmbeddable().getDescription());
        }
        assertEquals("root1", results.get(0).getEmbeddable().getName());
        assertEquals("child1_1", results.get(1).getEmbeddable().getName());
        assertEquals("child1_2", results.get(2).getEmbeddable().getName());
        assertEquals("child1_1_1", results.get(3).getEmbeddable().getName());
        assertEquals("child1_2_1", results.get(4).getEmbeddable().getName());
    }

    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testBindEmbeddableWithNullBindingsForJoinableAttributes() {
        CriteriaBuilder<TestAdvancedCTE1> cb = cbf.create(em, TestAdvancedCTE1.class);
        cb.with(TestAdvancedCTE1.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("embeddable.name").select("e.name")
            .bind("embeddable.description").select("''")
            .bind("embeddable.recursiveEntity").select("NULL")
            .bind("level").select("0")
            .bind("parent").select("NULL")
            .where("e.parent").isNull()
        .end()
        .orderByAsc("id");

        List<TestAdvancedCTE1> results = cb.getResultList();
        assertEquals(5, results.size());
        for (TestAdvancedCTE1 result : results) {
            assertEquals("", result.getEmbeddable().getDescription());
        }
        assertEquals("root1", results.get(0).getEmbeddable().getName());
        assertEquals("child1_1", results.get(1).getEmbeddable().getName());
        assertEquals("child1_2", results.get(2).getEmbeddable().getName());
        assertEquals("child1_1_1", results.get(3).getEmbeddable().getName());
        assertEquals("child1_2_1", results.get(4).getEmbeddable().getName());
    }

    @Test(expected = IllegalStateException.class)
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class, NoMySQL.class })
    public void testErrorOnMissingBinding() {
        CriteriaBuilder<TestCTE> cb = cbf.create(em, TestCTE.class);
        cb.with(TestCTE.class)
            .from(RecursiveEntity.class, "e")
            .bind("id").select("e.id")
            .bind("name").select("e.name")
            .where("e.parent").isNull()
        .end();
    }
}
