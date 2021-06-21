package com.github.api;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * EnableDocumentDelegate
 * Decorate the startup class with this annotation to enable automatic hosting of API documents
 *
 * @author echils
 */
@Target(value = ElementType.TYPE)
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Import(ApiDocumentConfiguration.class)
public @interface EnableDocumentDelegate {

}
