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

package com.blazebit.persistence;

import static org.junit.Assert.assertEquals;

import javax.persistence.EntityTransaction;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.blazebit.persistence.entity.DeletePersonCTE;
import com.blazebit.persistence.entity.Document;
import com.blazebit.persistence.entity.IntIdEntity;
import com.blazebit.persistence.entity.Person;
import com.blazebit.persistence.entity.PersonCTE;
import com.blazebit.persistence.entity.Version;
import com.blazebit.persistence.testsuite.base.category.NoDB2;
import com.blazebit.persistence.testsuite.base.category.NoDatanucleus;
import com.blazebit.persistence.testsuite.base.category.NoEclipselink;
import com.blazebit.persistence.testsuite.base.category.NoFirebird;
import com.blazebit.persistence.testsuite.base.category.NoH2;
import com.blazebit.persistence.testsuite.base.category.NoHibernate42;
import com.blazebit.persistence.testsuite.base.category.NoMySQL;
import com.blazebit.persistence.testsuite.base.category.NoOpenJPA;
import com.blazebit.persistence.testsuite.base.category.NoOracle;
import com.blazebit.persistence.testsuite.base.category.NoSQLite;
import com.blazebit.persistence.tx.TxVoidWork;

/**
 *
 * @author Christian Beikov
 * @since 1.1.0
 */
public class InsertTest extends AbstractCoreTest {
    
    private Person p1;
    private Person p2;

	@Override
    protected Class<?>[] getEntityClasses() {
        return new Class<?>[] {
            Document.class,
            Version.class,
            IntIdEntity.class,
            Person.class, 
            PersonCTE.class,
            DeletePersonCTE.class
        };
    }

    @Before
	public void setUp() {
		EntityTransaction tx = em.getTransaction();
		try {
			tx.begin();
			p1 = new Person("P1");
			em.persist(p1);
			em.flush();

			p2 = new Person("P2");
			em.persist(p2);
			em.flush();
			tx.commit();
		} catch (Exception e) {
			tx.rollback();
			throw new RuntimeException(e);
		}
	}
	
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testSimple() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.from(Person.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner").select("p");
        cb.orderByAsc("p.id");
        String expected = "INSERT INTO Document(age, idx, name, owner)\n"
                + "SELECT p.age, 1, CONCAT(p.name,'s document'), p FROM Person p ORDER BY " + renderNullPrecedence("p.id", "ASC", "LAST");

        assertEquals(expected, cb.getQueryString());

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                int updateCount = cb.executeUpdate();
                assertEquals(2, updateCount);
            }
        });
    }
    
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testSimpleWithLimit() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.from(Person.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner").select("p");
        cb.orderByAsc("p.id");
        cb.setMaxResults(1);
        String expected = "INSERT INTO Document(age, idx, name, owner)\n"
                + "SELECT p.age, 1, CONCAT(p.name,'s document'), p FROM Person p ORDER BY " + renderNullPrecedence("p.id", "ASC", "LAST");

        assertEquals(expected, cb.getQueryString());

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                int updateCount = cb.executeUpdate();
                assertEquals(1, updateCount);
            }
        });
    }

	// NOTE: hibernate 4.2 does not support using parameters in the select clause
	@Test
	@Category({ NoHibernate42.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
	public void testSimpleWithParameters() {
		final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
		cb.from(Person.class, "p");
		cb.bind("name").select("CONCAT(p.name,'s document')");
		cb.bind("age", 1L);
		cb.bind("idx", 1);
		cb.bind("owner").select("p");
        cb.orderByAsc("p.id");
		String expected = "INSERT INTO Document(age, idx, name, owner)\n"
				+ "SELECT :age, :idx, CONCAT(p.name,'s document'), p FROM Person p ORDER BY " + renderNullPrecedence("p.id", "ASC", "LAST");

		assertEquals(expected, cb.getQueryString());

        transactional(new TxVoidWork() {
            @Override
            public void work() {
    			int updateCount = cb.executeUpdate();
    			assertEquals(2, updateCount);
            }
        });
	}

	/* Returning */
    
	// NOTE: H2 does not support returning all generated keys
    @Test
    @Category({ NoH2.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testReturningAll() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.from(Person.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner").select("p");
        cb.orderByAsc("p.id");

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(2, result.getUpdateCount());
                assertEquals(2, result.getResultList().size());
                assertEquals(byOwner(p1).getId(), result.getResultList().get(0));
                assertEquals(byOwner(p2).getId(), result.getResultList().get(1));
            }
        });
    }
    
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testReturningLast() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.from(Person.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner").select("p");
        cb.orderByAsc("p.id");

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(2, result.getUpdateCount());
                assertEquals(byOwner(p2).getId(), result.getLastResult());
            }
        });
    }
    
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testReturningLastWithParameter() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.from(Person.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner").select("p");
        cb.where("name").eq("P2");
        cb.orderByAsc("p.id");

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(1, result.getUpdateCount());
                assertEquals(byOwner(p2).getId(), result.getLastResult());
            }
        });
    }
    
    @Test
    @Category({ NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testReturningLastWithLimit() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.from(Person.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner").select("p");
        cb.orderByAsc("p.id");
        cb.setMaxResults(1);

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(1, result.getUpdateCount());
                assertEquals(byOwner(p1).getId(), result.getLastResult());
            }
        });
    }
    
    // NOTE: H2 only supports with clause in select statement
    @Test
    @Category({ NoH2.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testReturningLastWithCte() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.with(PersonCTE.class)
            .from(Person.class, "p")
            .bind("id").select("p.id")
            .bind("name").select("CONCAT(p.name,'s document')")
            .bind("age").select("p.age")
            .bind("idx").select("1")
            .bind("owner").select("p")
        .end();
        cb.from(PersonCTE.class, "p");
        cb.bind("name").select("p.name");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("p.idx");
        cb.bind("owner").select("p.owner");
        cb.where("p.name").eq("P2s document");
        cb.orderByAsc("p.id");

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(1, result.getUpdateCount());
                assertEquals(byOwner(p2).getId(), result.getLastResult());
            }
        });
    }

    // NOTE: H2 only supports with clause in select statement
    @Test
    @Category({ NoH2.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testReturningLastWithCteAndLimit() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.with(PersonCTE.class)
            .from(Person.class, "p")
            .bind("id").select("p.id")
            .bind("name").select("CONCAT(p.name,'s document')")
            .bind("age").select("p.age")
            .bind("idx").select("1")
            .bind("owner").select("p")
        .end();
        cb.from(PersonCTE.class, "p");
        cb.bind("name").select("p.name");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("p.idx");
        cb.bind("owner").select("p.owner");
        cb.orderByAsc("p.id");
        cb.setMaxResults(1);

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(1, result.getUpdateCount());
                assertEquals(byOwner(p1).getId(), result.getLastResult());
            }
        });
    }

    // NOTE: Currently only PostgreSQL supports returning from within a CTE
    // NOTE: hibernate 4.2 does not support using parameters in the select clause
    @Test
    @Category({ NoH2.class, NoDB2.class, NoOracle.class, NoSQLite.class, NoFirebird.class, NoMySQL.class, NoHibernate42.class, NoDatanucleus.class, NoEclipselink.class, NoOpenJPA.class })
    public void testDeleteReturningWithCteAndLimitInto() {
        final InsertCriteriaBuilder<Document> cb = cbf.insert(em, Document.class);
        cb.withReturning(DeletePersonCTE.class)
            .delete(Person.class, "p")
            .where("p.name").eq(p1.getName())
            .returning("id", "id")
            .returning("name", "name")
            .returning("age", "age")
            .returning("owner", "p")
        .end();
        cb.from(DeletePersonCTE.class, "p");
        cb.bind("name").select("CONCAT(p.name,'s document')");
        cb.bind("age").select("p.age");
        cb.bind("idx").select("1");
        cb.bind("owner", p2);
        cb.bind("nonJoinable").select("CONCAT('PersonId=',p.owner.id)");
        cb.orderByAsc("p.id");
        cb.setMaxResults(1);

        transactional(new TxVoidWork() {
            @Override
            public void work() {
                ReturningResult<Long> result = cb.executeWithReturning("id", Long.class);
                assertEquals(1, result.getUpdateCount());
                assertEquals("PersonId=" + p1.getId(), em.find(Document.class, result.getLastResult()).getNonJoinable());
            }
        });
    }
    
    private Document byOwner(Person p) {
        return cbf.create(em, Document.class).where("owner").eq(p).getSingleResult();
    }
}
