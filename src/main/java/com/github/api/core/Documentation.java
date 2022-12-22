package com.github.api.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.models.Swagger;
import io.swagger.util.Yaml;

import java.text.SimpleDateFormat;

import static com.github.api.ApiDocumentContext.DEFAULT_DATE_FORMAT;

/**
 * Documentation
 *
 * @author echils
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
            return Yaml.pretty().writeValueAsString(swagger);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not write YAML", e);
        }
    }

    /**
     * Convert to JSON
     */
    public Json toJson() {

        if (swagger == null) {
            return new Json(null);
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setDateFormat(new SimpleDateFormat(DEFAULT_DATE_FORMAT));
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            return new Json(objectMapper.writeValueAsString(swagger));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not write JSON", e);
        }
    }

    /**
     * Wrapper the api content
     */
    public static class Json{

        private final String value;

        public Json(String value) {
            this.value = value;
        }

        @JsonValue
        @JsonRawValue
        public String value() {
            return value;
        }
    }

}

