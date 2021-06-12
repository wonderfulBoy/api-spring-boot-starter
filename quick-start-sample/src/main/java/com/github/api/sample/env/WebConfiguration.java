package com.github.api.sample.env;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * WEB应用配置
 *
 * @author echils
 * @since 2021-06-12 20:41:56
 */
@Configuration
public class WebConfiguration extends WebMvcConfigurationSupport {


    /**
     * 注意：当你使用WebMvcConfigurationSupport{@link WebMvcConfigurationSupport}
     * 来对你的应用进行全局配置时需要手动加载静态资源.
     * 推荐使用{@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer}
     * 替代{@link WebMvcConfigurationSupport}.
     *
     * @param registry {@link ResourceHandlerRegistry}
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/");
        super.addResourceHandlers(registry);
    }


}
