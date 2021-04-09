package com.github.api.core.refer;

import lombok.Data;

/**
 * ParameterReference
 *
 * @author echils
 * @since 2021-03-03 22:04:48
 */
@Data
public class ParameterReference {

    /**
     * param name
     */
    private String name;

    /**
     * param description
     */
    private String description;

    /**
     * param default value
     */
    private String defaultValue;

    /**
     * is it required
     */
    private boolean required;

    /**
     * Model reference
     */
    private ModelReference modelRef;

    /**
     * The location of the parameter request
     */
    private String paramLocation;

}
