package com.github.api;

import com.github.api.core.Documentation;
import com.github.api.utils.CommonParseUtils;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import static com.github.api.ApiDocumentContext.DEFAULT_API_QUERY_REQUEST;

/**
 * This class will provide the ability to view the project documentation
 *
 * @author echils
 */
@RestController
public class ApiDocumentFileProvider {

    /**
     * logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ApiDocumentFileProvider.class);


    @Autowired
    private ApiDocumentProperties apiDocumentProperties;

    /**
     * Show the api document
     */
    @GetMapping(value = {DEFAULT_API_QUERY_REQUEST}, produces = {"application/json", "application/hal+json"})
    public ResponseEntity<Documentation.Json> showDocument() {
        if (apiDocumentProperties.getProfile() == ApiDocumentProperties.Profile.LOCAL) {
            if (ApiDocumentContext.documentation != null) {
                return new ResponseEntity<>(ApiDocumentContext.documentation.toJson(), HttpStatus.OK);
            }
        } else {
            try {
                Resource[] resources;
                if (CommonParseUtils.inJar(ApiDocumentFileProvider.class)) {
                    resources = (new PathMatchingResourcePatternResolver())
                            .getResources("classpath:" + ApiDocumentContext.DEFAULT_MATCHING_PATH);
                } else {
                    resources = (new PathMatchingResourcePatternResolver())
                            .getResources("classpath:" + ApiDocumentContext.DEFAULT_LOCAL_MATCHING_PATH);
                }
                if (resources.length > 0) {
                    return new ResponseEntity<>(new Documentation.Json(new Gson().toJson(new Yaml()
                            .load(resources[0].getInputStream()))), HttpStatus.OK);
                }
            } catch (Exception e) {
                logger.error("Load local api file failed:{}", e.getMessage());
            }
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
