package com.github.api.core.refer;

import io.swagger.models.Swagger;
import lombok.Data;

/**
 * Documentation
 *
 * @author echils
 * @since 2021-02-28 22:30:53
 */
@Data
public class Documentation {

    /**
     * The model of swagger
     */
    private Swagger swagger;

    /**
     * The json of swagger {@link io.swagger.models.Swagger}
     */
    private String value;


    /**
     * @return
     */
    public String toYaml() {
        return null;
    }

}

