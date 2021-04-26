package com.github.api.core.refer;

import com.fasterxml.classmate.ResolvedType;
import lombok.Data;

import java.util.Map;

/**
 * ModelReference
 *
 * @author echils
 * @since 2021-03-03 22:00:10
 */
@Data
public class ModelReference {

    /**
     * Model name
     */
    private String name;

    /**
     * Model type
     */
    private ResolvedType type;

    /**
     * Is it a map
     */
    private boolean isMap = false;

    /**
     * Model properties
     */
    private Map<String, ModelReference> properties;

    /**
     * Model description
     */
    private String description;

    /**
     * Model data example
     */
    private Object example;

}
