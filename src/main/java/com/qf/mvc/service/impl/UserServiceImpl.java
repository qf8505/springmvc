package com.qf.mvc.service.impl;

import com.qf.mvc.annotaion.MyService;
import com.qf.mvc.entity.User;
import com.qf.mvc.service.UserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MyService()
public class UserServiceImpl implements UserService {

	private static Map<String, User> users = new HashMap<String, User>();

	static {
		users.put("aa", new User("1", "aaa", "123456"));
		users.put("bb", new User("2", "bbb", "123456"));
		users.put("cc", new User("3", "ccc", "123456"));
		users.put("dd", new User("4", "ddd", "123456"));
		users.put("ee", new User("5", "eee", "123456"));
	}

	@Override
	public String get(String name) {
		User user = users.get(name);
		if (user == null) {
			user = users.get("aa");
		}
		return user.toString();
	}

	@Override
	public List<User> list() {
		List<User> list = new ArrayList<User>();
		for (Map.Entry<String, User> entry : users.entrySet()) {
			list.add(entry.getValue());
		}
		return list;
	}
}
