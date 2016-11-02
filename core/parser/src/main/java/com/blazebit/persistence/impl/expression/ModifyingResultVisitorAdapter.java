package com.blazebit.persistence.impl.expression;

import com.blazebit.persistence.impl.expression.modifier.ExpressionModifier;
import com.blazebit.persistence.impl.expression.modifier.ExpressionModifiers;

/**
 * Created
 * by Moritz Becker (moritz.becker@gmx.at)
 * on 22.09.2016.
 */
public abstract class ModifyingResultVisitorAdapter implements Expression.ResultVisitor<Expression> {

    protected ExpressionModifier<Expression> parentModifier;
    protected final ExpressionModifiers expressionModifiers = new ExpressionModifiers();

}
