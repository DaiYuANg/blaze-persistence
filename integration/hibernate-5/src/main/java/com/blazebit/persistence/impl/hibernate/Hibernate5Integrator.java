package com.blazebit.persistence.impl.hibernate;

import com.blazebit.apt.service.ServiceProvider;
import com.blazebit.persistence.CTE;
import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.persister.spi.PersisterClassResolver;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

import java.util.logging.Logger;

@ServiceProvider(Integrator.class)
public class Hibernate5Integrator implements Integrator {

    private static final Logger LOG = Logger.getLogger(Hibernate5Integrator.class.getName());
    
    @Override
    public void integrate(Metadata metadata, SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
        for (PersistentClass clazz : metadata.getEntityBindings()) {
            Class<?> entityClass = clazz.getMappedClass();
            
            if (entityClass.isAnnotationPresent(CTE.class)) {
                clazz.getTable().setSubselect("select * from " + clazz.getJpaEntityName());
                // TODO: check that no collections are mapped
            }
        }

        serviceRegistry.locateServiceBinding(PersisterClassResolver.class).setService(new CustomPersisterClassResolver());
        serviceRegistry.locateServiceBinding(Database.class).setService(new SimpleDatabase(metadata.getDatabase().getDefaultNamespace().getTables().iterator()));
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory, SessionFactoryServiceRegistry serviceRegistry) {
    }

}
