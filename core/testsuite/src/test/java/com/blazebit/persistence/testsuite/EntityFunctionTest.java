/*
 * Copyright 2015 Blazebit.
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

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.testsuite.base.category.*;
import com.blazebit.persistence.testsuite.entity.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.persistence.EntityTransaction;
import javax.persistence.Tuple;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 * @author Christian Beikov
 * @since 1.2.0
 */
public class EntityFunctionTest extends AbstractCoreTest {

    @Before
    public void setUp() {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Person p1 = new Person("p1");
            Document d1 = new Document("doc1", 1);

            d1.setOwner(p1);

            em.persist(p1);
            em.persist(d1);

            em.flush();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw new RuntimeException(e);
        }
    }

    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testValuesEntityFunction() {
        CriteriaBuilder<Tuple> cb = cbf.create(em, Tuple.class);
        cb.fromValues(Long.class, "allowedAge", Collections.singleton(1L));
        cb.from(Document.class, "doc");
        cb.where("doc.age").eqExpression("allowedAge.value");
        cb.select("doc.name");
        cb.select("allowedAge.value");

        String expected = ""
        		+ "SELECT doc.name, TREAT_LONG(allowedAge.value) FROM (VALUES (?)) allowedAge, Document doc WHERE doc.age = TREAT_LONG(allowedAge.value)";
        
        assertEquals(expected, cb.getQueryString());
        List<Tuple> resultList = cb.getResultList();
        assertEquals(1, resultList.size());
        assertEquals("doc1", resultList.get(0).get(0));
        assertEquals(1L, resultList.get(0).get(1));
    }

    @Test
    // NOTE: Entity joins are supported since Hibernate 5.1, Datanucleus 5 and latest Eclipselink
    @Category({ NoHibernate42.class, NoHibernate43.class, NoHibernate50.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testValuesEntityFunctionLeftJoin() {
        CriteriaBuilder<Tuple> cb = cbf.create(em, Tuple.class);
        cb.fromValues(Long.class, "allowedAge", Arrays.asList(1L, 2L, 3L));
        cb.leftJoinOn(Document.class, "doc")
            .on("doc.age").eqExpression("allowedAge.value")
        .end();
        cb.select("allowedAge.value");
        cb.select("doc.name");
        cb.orderByAsc("allowedAge.value");

        String expected = ""
                + "SELECT TREAT_LONG(allowedAge.value), doc.name FROM (VALUES (?), (?), (?)) allowedAge LEFT JOIN Document doc ON doc.age = TREAT_LONG(allowedAge.value)" +
                " ORDER BY " + renderNullPrecedence("TREAT_LONG(allowedAge.value)", "ASC", "LAST");

        assertEquals(expected, cb.getQueryString());
        List<Tuple> resultList = cb.getResultList();
        assertEquals(3, resultList.size());

        assertEquals(1L, resultList.get(0).get(0));
        assertEquals("doc1", resultList.get(0).get(1));

        assertEquals(2L, resultList.get(1).get(0));
        assertNull(resultList.get(1).get(1));

        assertEquals(3L, resultList.get(2).get(0));
        assertNull(resultList.get(2).get(1));
    }

    @Test
    // NOTE: Entity joins are supported since Hibernate 5.1, Datanucleus 5 and latest Eclipselink
    @Category({ NoHibernate42.class, NoHibernate43.class, NoHibernate50.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testValuesEntityFunctionWithEntity() {
        CriteriaBuilder<Tuple> cb = cbf.create(em, Tuple.class);
        cb.fromValues(IntIdEntity.class, "intEntity", Arrays.asList(
                new IntIdEntity("doc1"),
                new IntIdEntity("docX")
        ));
        cb.leftJoinOn(Document.class, "doc")
                .on("doc.name").eqExpression("intEntity.name")
        .end();
        cb.select("intEntity.name");
        cb.select("doc.name");
        cb.orderByAsc("intEntity.name");

        String expected = ""
                + "SELECT intEntity.name, doc.name FROM IntIdEntity(VALUES (?,?), (?,?)) intEntity LEFT JOIN Document doc ON doc.name = intEntity.name" +
                " ORDER BY " + renderNullPrecedence("intEntity.name", "ASC", "LAST");

        assertEquals(expected, cb.getQueryString());
        List<Tuple> resultList = cb.getResultList();
        assertEquals(2, resultList.size());

        assertEquals("doc1", resultList.get(0).get(0));
        assertEquals("doc1", resultList.get(0).get(1));

        assertEquals("docX", resultList.get(1).get(0));
        assertNull(resultList.get(1).get(1));
    }
}
