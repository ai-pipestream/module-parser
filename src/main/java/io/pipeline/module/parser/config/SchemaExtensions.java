package io.pipeline.module.parser.config;

import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import java.lang.annotation.*;

/**
 * Custom schema extensions for enhanced UI rendering.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SchemaExtensions {
    
    /**
     * Array of extensions to apply to the schema.
     */
    Extension[] value();
    
    /**
     * Convenience annotation for adding suggestions to string array fields.
     */
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface Suggestions {
        /**
         * The class containing a static method that returns suggestions.
         */
        Class<?> provider() default Void.class;
        
        /**
         * The name of the static method that returns List<String> of suggestions.
         */
        String method() default "";
        
        /**
         * Direct list of suggestions (if not using provider).
         */
        String[] values() default {};
    }
}