package com.blazebit.persistence.impl.function.count;

import com.blazebit.persistence.spi.FunctionRenderContext;

/**
 *
 * @author Moritz Becker
 * @since 1.2.0
 */
public class MySQLCountTupleFunction extends AbstractCountFunction {

    private static final String DISTINCT = "distinct ";

    @Override
    public void render(FunctionRenderContext context) {
        Count count = getCount(context);

        context.addChunk("count(");

        if (count.isDistinct()) {
            context.addChunk(DISTINCT);
        }

        int argumentStartIndex = count.getArgumentStartIndex();

        if (count.getCountArgumentSize() > 1) {
            if (count.isDistinct()) {
                context.addArgument(argumentStartIndex);
                for (int i = argumentStartIndex + 1; i < context.getArgumentsSize(); i++) {
                    context.addChunk(", ");
                    context.addArgument(i);
                }
            } else {
                context.addChunk("case when ");
                context.addArgument(argumentStartIndex);
                context.addChunk(" is null");
                for (int i = argumentStartIndex + 1; i < context.getArgumentsSize(); i++) {
                    context.addChunk(" or ");
                    context.addArgument(i);
                    context.addChunk(" is null");
                }
                context.addChunk(" then null else 1 end");
            }
        } else {
            context.addArgument(argumentStartIndex);
        }

        context.addChunk(")");
    }
}
