package com.qf.mvc.annotaion;

import java.lang.annotation.*;
 
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyService {
	String value() default "";
}