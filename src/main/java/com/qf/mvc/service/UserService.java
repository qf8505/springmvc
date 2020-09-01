package com.qf.mvc.service;

import com.qf.mvc.entity.User;

import java.util.List;

public interface UserService {
	String get(String name);

	List<User> list();
}