package com.github.api.core.refer;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * RequestMappingContext
 *
 * @author echils
 * @since 2021-03-01 22:55:44
 */
@Data
public class RequestMappingContext {

    /**
     * Api tag name
     */
    private String tagName;

    /**
     * Api tag description
     */
    private String description;

    /**
     * Api reference
     */
    private List<ApiListingReference> apiListingReferences = new ArrayList<>();

}
