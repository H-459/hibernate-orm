/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.graphs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Subgraph;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;

import org.hibernate.Hibernate;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;

import org.hibernate.testing.TestForIssue;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Christian Bauer
 * @author Brett Meyer
 */
public class EntityGraphTest extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] { Foo.class, Bar.class, Baz.class,
				Company.class, Employee.class, Manager.class, Location.class };
	}

	@Test
	@TestForIssue(jiraKey = "HHH-8857")
	public void loadMultipleAssociations() {
		EntityManager em = getOrCreateEntityManager();
		em.getTransaction().begin();

		Bar bar = new Bar();
		em.persist( bar );

		Baz baz = new Baz();
		em.persist( baz );

		Foo foo = new Foo();
		foo.bar = bar;
		foo.baz = baz;
		em.persist( foo );

		em.getTransaction().commit();
		em.clear();

		em.getTransaction().begin();

		EntityGraph<Foo> fooGraph = em.createEntityGraph( Foo.class );
		fooGraph.addAttributeNodes( "bar", "baz" );

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put( "javax.persistence.loadgraph", fooGraph );

		Foo result = em.find( Foo.class, foo.id, properties );

		assertTrue( Hibernate.isInitialized( result ) );
		assertTrue( Hibernate.isInitialized( result.bar ) );
		assertTrue( Hibernate.isInitialized( result.baz ) );

		em.getTransaction().commit();
		em.close();
	}

    @Test
   	public void loadCollection() {
   		EntityManager em = getOrCreateEntityManager();
   		em.getTransaction().begin();

   		Bar bar = new Bar();
   		em.persist( bar );

   		Foo foo = new Foo();
   		foo.bar = bar;
        bar.foos.add(foo);
   		em.persist( foo );

   		em.getTransaction().commit();
   		em.clear();

   		em.getTransaction().begin();

   		EntityGraph<Bar> barGraph = em.createEntityGraph( Bar.class );
   		barGraph.addAttributeNodes("foos");

   		Map<String, Object> properties = new HashMap<String, Object>();
   		properties.put( "javax.persistence.loadgraph", barGraph);

   		Bar result = em.find( Bar.class, bar.id, properties );

   		assertTrue( Hibernate.isInitialized( result ) );
   		assertTrue( Hibernate.isInitialized( result.foos ) );

   		em.getTransaction().commit();
   		em.close();
   	}

    @Test
   	public void loadInverseCollection() {
   		EntityManager em = getOrCreateEntityManager();
   		em.getTransaction().begin();

   		Bar bar = new Bar();
   		em.persist( bar );
   		Baz baz = new Baz();
   		em.persist( baz );

   		Foo foo = new Foo();
   		foo.bar = bar;
   		foo.baz = baz;
        bar.foos.add(foo);
        baz.foos.add(foo);
   		em.persist( foo );

   		em.getTransaction().commit();
   		em.clear();

   		em.getTransaction().begin();

   		EntityGraph<Foo> fooGraph = em.createEntityGraph( Foo.class );
   		fooGraph.addAttributeNodes("bar");
   		fooGraph.addAttributeNodes("baz");
        Subgraph<Bar> barGraph = fooGraph.addSubgraph("bar", Bar.class);
        barGraph.addAttributeNodes("foos");

   		Map<String, Object> properties = new HashMap<String, Object>();
   		properties.put( "javax.persistence.loadgraph", fooGraph );

   		Foo result = em.find( Foo.class, foo.id, properties );

   		assertTrue( Hibernate.isInitialized( result ) );
   		assertTrue( Hibernate.isInitialized( result.bar ) );
        assertTrue( Hibernate.isInitialized( result.bar.foos) );
   		assertTrue( Hibernate.isInitialized( result.baz ) );
   		// sanity check -- ensure the only bi-directional fetch was the one identified by the graph
        assertFalse( Hibernate.isInitialized( result.baz.foos) );

   		em.getTransaction().commit();
   		em.close();
   	}

    /**
	 * JPA 2.1 spec: "Add a node to the graph that corresponds to a managed type with inheritance. This allows for
	 * multiple subclass subgraphs to be defined for this node of the entity graph. Subclass subgraphs will
	 * automatically include the specified attributes of superclass subgraphs."
	 */
	@Test
	@TestForIssue(jiraKey = "HHH-8640")
	public void inheritanceTest() {
		EntityManager em = getOrCreateEntityManager();
		em.getTransaction().begin();

		Manager manager = new Manager();
		em.persist( manager );
		Employee employee = new Employee();
		employee.friends.add( manager );
		employee.managers.add( manager );
		em.persist( employee );
		Company company = new Company();
		company.employees.add( employee );
		company.employees.add( manager );
		em.persist( company );

		em.getTransaction().commit();
		em.clear();

		em.getTransaction().begin();

		EntityGraph<Company> entityGraph = em.createEntityGraph( Company.class );
		Subgraph<Employee> subgraph = entityGraph.addSubgraph( "employees" );
		subgraph.addAttributeNodes( "managers" );
		subgraph.addAttributeNodes( "friends" );
		Subgraph<Manager> subSubgraph = subgraph.addSubgraph( "managers", Manager.class );
		subSubgraph.addAttributeNodes( "managers" );
		subSubgraph.addAttributeNodes( "friends" );

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put( "javax.persistence.loadgraph", entityGraph );

		Company result = em.find( Company.class, company.id, properties );

		assertTrue( Hibernate.isInitialized( result ) );
		assertTrue( Hibernate.isInitialized( result.employees ) );
		assertEquals( result.employees.size(), 2 );
		for (Employee resultEmployee : result.employees) {
			assertTrue( Hibernate.isInitialized( resultEmployee.managers ) );
			assertTrue( Hibernate.isInitialized( resultEmployee.friends ) );
		}

		em.getTransaction().commit();
		em.close();
	}

    @Test
    @TestForIssue(jiraKey = "HHH-9080")
    public void attributeNodeInheritanceTest() {
        EntityManager em = getOrCreateEntityManager();
        em.getTransaction().begin();

        Manager manager = new Manager();
        em.persist( manager );
        Employee employee = new Employee();
        manager.friends.add( employee);
        em.persist( employee );
        Manager anotherManager = new Manager();
        manager.managers.add(anotherManager);
        em.persist( anotherManager );
        em.getTransaction().commit();
        em.clear();

        em.getTransaction().begin();

        EntityGraph<Manager> entityGraph = em.createEntityGraph( Manager.class );
        entityGraph.addAttributeNodes( "friends" );
        entityGraph.addAttributeNodes( "managers" );

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put( "javax.persistence.loadgraph", entityGraph );

        Manager result = em.find( Manager.class, manager.id, properties );

        assertTrue( Hibernate.isInitialized( result ) );
        assertTrue( Hibernate.isInitialized( result.friends ) );
        assertEquals( result.friends.size(), 1 );
        assertTrue( Hibernate.isInitialized( result.managers) );
        assertEquals( result.managers.size(), 1 );

        em.getTransaction().commit();
        em.close();
    }

    @Test
    @TestForIssue(jiraKey = "HHH-9735")
    public void loadIsMemeberQueriedCollection() {

        EntityManager em = getOrCreateEntityManager();
        em.getTransaction().begin();

        Bar bar = new Bar();
        em.persist( bar );

        Foo foo = new Foo();
        foo.bar = bar;
        bar.foos.add(foo);
        em.persist( foo );

        em.getTransaction().commit();
        em.clear();

        em.getTransaction().begin();
        foo = em.find(Foo.class, foo.id);

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Bar> cq = cb.createQuery(Bar.class);
        Root<Bar> from = cq.from(Bar.class);

        Expression<Set<Foo>> foos = from.get("foos");

        cq.where(cb.isMember(foo, foos));

        TypedQuery<Bar> query = em.createQuery(cq);

        EntityGraph<Bar> barGraph = em.createEntityGraph( Bar.class );
        barGraph.addAttributeNodes("foos");
        query.setHint("javax.persistence.loadgraph", barGraph);

        Bar result = query.getSingleResult();

        assertTrue( Hibernate.isInitialized( result ) );
        assertTrue( Hibernate.isInitialized( result.foos ) );

        em.getTransaction().commit();
        em.close();
    }

    @Entity
	@Table(name = "foo")
    public static class Foo {

		@Id
		@GeneratedValue
		public Integer id;

		@ManyToOne(fetch = FetchType.LAZY)
		public Bar bar;

		@ManyToOne(fetch = FetchType.LAZY)
		public Baz baz;
	}

	@Entity
	@Table(name = "bar")
	public static class Bar {

		@Id
		@GeneratedValue
		public Integer id;

        @OneToMany(mappedBy = "bar")
        public Set<Foo> foos = new HashSet<Foo>();
	}

	@Entity
	@Table(name = "baz")
    public static class Baz {

		@Id
		@GeneratedValue
        public Integer id;

        @OneToMany(mappedBy = "baz")
        public Set<Foo> foos = new HashSet<Foo>();

	}

}