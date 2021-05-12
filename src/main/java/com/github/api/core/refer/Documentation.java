package com.github.api.core.refer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.models.Swagger;

/**
 * Documentation
 *
 * @author echils
 * @since 2021-02-28 22:30:53
 */
public class Documentation {

    /**
     * The model of swagger
     */
    private Swagger swagger;


    public Documentation(Swagger swagger) {
        this.swagger = swagger;
    }

    /**
     * Convert to YAML
     */
    public String toYaml() {

        if (swagger == null) {
            return "";
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            return objectMapper.writer(new DefaultPrettyPrinter()).writeValueAsString(swagger);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not write YAML", e);
        }
    }

    /**
     * Convert to JSON
     */
    public String toJson() {

        if (swagger == null) {
            return "";
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            return objectMapper.writeValueAsString(swagger);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not write JSON", e);
        }
    }

}

