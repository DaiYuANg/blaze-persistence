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
package com.blazebit.persistence.impl;

import com.blazebit.persistence.ReturningUpdateCriteriaBuilder;

/**
 *
 * @param <T> The query result type
 * @author Christian Beikov
 * @since 1.1.0
 */
public class ReturningUpdateCriteriaBuilderImpl<T, Y> extends BaseUpdateCriteriaBuilderImpl<T, ReturningUpdateCriteriaBuilder<T, Y>, Y> implements ReturningUpdateCriteriaBuilder<T, Y> {

    public ReturningUpdateCriteriaBuilderImpl(MainQuery mainQuery, Class<T> clazz, String alias, String cteName, Class<?> cteClass, Y result, CTEBuilderListener listener) {
        super(mainQuery, false, clazz, alias, cteName, cteClass, result, listener);
    }

    @Override
    protected void buildExternalQueryString(StringBuilder sbSelectFrom) {
        super.buildExternalQueryString(sbSelectFrom);
        applyJpaReturning(sbSelectFrom);
    }

}
