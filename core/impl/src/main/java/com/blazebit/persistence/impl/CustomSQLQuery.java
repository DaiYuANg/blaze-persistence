package com.blazebit.persistence.impl;

import com.blazebit.persistence.CommonQueryBuilder;
import com.blazebit.persistence.impl.query.QuerySpecification;
import com.blazebit.persistence.spi.ExtendedQuerySupport;

import javax.persistence.FlushModeType;
import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import java.util.List;
import java.util.Map;

public class CustomSQLQuery extends AbstractCustomQuery<Object> {

    private final Query delegate;

    public CustomSQLQuery(QuerySpecification querySpecification, Query delegate, CommonQueryBuilder<?> cqb, ExtendedQuerySupport extendedQuerySupport, Map<String, String> valuesParameters, Map<String, ValuesParameterBinder> valuesBinders) {
        super(querySpecification, cqb, extendedQuerySupport, valuesParameters, valuesBinders);
        this.delegate = delegate;
    }

    public Map<String, String> getAddedCtes() {
        return querySpecification.getAddedCtes();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getResultList() {
        throw new IllegalArgumentException("Can not call getResultList on a modification query!");
    }

    @Override
    public Object getSingleResult() {
        throw new IllegalArgumentException("Can not call getSingleResult on a modification query!");
    }

    @Override
    public int executeUpdate() {
        return querySpecification.createModificationPlan(firstResult, maxResults).executeUpdate();
    }

    @Override
    public Query setHint(String hintName, Object value) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public Map<String, Object> getHints() {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented!");
    }

    @Override
    public Query setFlushMode(FlushModeType flushMode) {
        delegate.setFlushMode(flushMode);
        return this;
    }

    @Override
    public FlushModeType getFlushMode() {
        return delegate.getFlushMode();
    }

    @Override
    public Query setLockMode(LockModeType lockMode) {
        delegate.setLockMode(lockMode);
        return this;
    }

    @Override
    public LockModeType getLockMode() {
        return delegate.getLockMode();
    }

    @Override
    public <T> T unwrap(Class<T> cls) {
        if (querySpecification.getParticipatingQueries().size() > 1) {
            throw new PersistenceException("Unsupported unwrap: " + cls.getName());
        }
        return delegate.unwrap(cls);
    }
}
