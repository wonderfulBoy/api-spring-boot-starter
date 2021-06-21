# API Spring Boot Starter Quickstart Guide

This README.md describes how to quickly configure and use the starter.

<p align="center">
  <a>
   <img alt="Framework" src="ECHILS.PNG">
  </a>
</p>

## Development Environment  
JDK     &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;1.8.0_202  
Maven   &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;3.5.4  
Spring Boot &nbsp;&nbsp;&nbsp;&nbsp;2.3.4.RELEASE  

## Functional Description
This starter will solve the developer's annoyance when writing the Swagger API, eliminating the need for developers to add intrusive swagger usage to their code. Instead, just write standard Javadoc comments to automatically generate api documents, which can help developers reduce write documentation time and improve development efficiency.


## Quick Start Example  

##### 1、Add the dependency to the pom.xml.  
````
<dependency>
    <groupId>com.github.echils</groupId>
    <artifactId>api-spring-boot-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
````
##### 2、Add the annotation {@link EnableDocumentDelegate} to the startup class to turn on the api document automation function.
````
package com.github.api.sample;

import com.github.api.EnableDocumentDelegate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The startup class of application
 *
 * @author echils
 */
@SpringBootApplication
@EnableDocumentDelegate
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}

````
##### 3、Start the service, and visit *http://\<ip>:\<port>/index.html* after the service starts normally, you can see the automatically generated swagger api document


## More Usage Samples
Demo:&nbsp;&nbsp;[quick-start-sample](quick-start-sample) 
