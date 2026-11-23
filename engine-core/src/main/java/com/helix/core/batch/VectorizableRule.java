package com.helix.core.batch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation applied to compiled rules or rule definitions to indicate
 * whether the rule expression is eligible for SIMD vectorised batch evaluation.
 * Allows O(1) classification lookups without repeated AST traversal.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Inherited
public @interface VectorizableRule {

    /**
     * Whether the rule is vectorizable via the SIMD batch evaluator.
     *
     * @return true if vectorizable, false otherwise
     */
    boolean value() default true;

    /**
     * Optional diagnostic reason or classification note.
     *
     * @return diagnostic reason
     */
    String reason() default "";
}
