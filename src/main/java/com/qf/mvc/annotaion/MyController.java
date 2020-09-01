package com.qf.mvc.annotaion;

import java.lang.annotation.*;
 
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyController {
	String value() default "";
}