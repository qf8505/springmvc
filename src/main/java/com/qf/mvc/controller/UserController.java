package com.qf.mvc.controller;

import com.qf.mvc.annotaion.MyAutowired;
import com.qf.mvc.annotaion.MyController;
import com.qf.mvc.annotaion.MyRequestMapping;
import com.qf.mvc.annotaion.MyRequestParam;
import com.qf.mvc.entity.User;
import com.qf.mvc.service.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@MyController
@MyRequestMapping("/user")
public class UserController {

	@MyAutowired
	private UserService userService;

	@MyRequestMapping("/index")
	public String index(HttpServletRequest request, HttpServletResponse response,
						@MyRequestParam("name") String name) throws IOException {
		String res = userService.get(name);
		System.out.println(name + "=>" + res);
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write(res);
		return "index";
	}

	@MyRequestMapping("/list")
	public String list(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		List<User> users = userService.list();
		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write(users.toString());
		return "list";
	}
}