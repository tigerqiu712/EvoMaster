package com.foo.rest.examples.spring.namedresource;

import com.foo.rest.examples.spring.SwaggerConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import static springfox.documentation.builders.PathSelectors.regex;

/**
 * Created by arcand on 01.03.17.
 */
@EnableSwagger2
@SpringBootApplication(exclude = SecurityAutoConfiguration.class)
public class NamedResourceApplication  extends SwaggerConfiguration {

    public static void main(String[] args){
        SpringApplication.run(NamedResourceApplication.class, args);
    }

}
