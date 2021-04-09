package com.github.api.core.refer;

import lombok.Data;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Set;

/**
 * ApiListingReference
 *
 * @author echils
 * @since 2021-03-03 21:49:47
 */
@Data
public class ApiListingReference {

    /**
     * Request path
     */
    private String path;

    /**
     * Http request method
     */
    private RequestMethod requestMethod;

    /**
     * Method name
     */
    private String methodName;

    /**
     * Api description
     */
    private String description;

    /**
     * produces support
     */
    private Set<MediaType> produces;

    /**
     * consumes support
     */
    private Set<MediaType> consumes;

    /**
     * Whether to deprecate
     */
    private boolean deprecated;

    /**
     * Response model
     */
    private ModelReference responseModel;

    /**
     * Param models
     */
    private List<ParameterReference> parameterReferences;

    /**
     * Response messages
     */
    private List<ResponseMessage> responseMessages;


}
