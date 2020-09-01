package com.qf.mvc.servlet;

import com.qf.mvc.annotaion.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {
	private Properties contextConfig = new Properties();
	private List<String> classNames = new ArrayList<String>();
	private Map<String, Object> ioc = new HashMap<String, Object>();
	private List<Handler> handlerMapping = new ArrayList<Handler>();

	private static final long serialVersionUID = -4943120355864715254L;


	@Override
	public void init(ServletConfig config) throws ServletException {
		//load config
		doLoadConfig(config.getInitParameter("contextConfigLocation"));
		//scan relative class
		doScanner(contextConfig.getProperty("scanPackage"));
		//init ioc container put relative class to it
		doInstance();
		//inject dependence
		doAutoWired();
		//init handlerMapping
		initHandlerMapping();
	}

	/**
	 * 解析请求路径和controller方法映射
	 */
	private void initHandlerMapping() {
		if (ioc.isEmpty()) return;
		for (Map.Entry<String, Object> entry : ioc.entrySet()) {
			Class<?> clazz = entry.getValue().getClass();
			if (!clazz.isAnnotationPresent(MyController.class)) {
				continue;
			}
			String baseUrl = "";
			if (clazz.isAnnotationPresent(MyRequestMapping.class)) {
				MyRequestMapping requestMapping = clazz.getAnnotation(MyRequestMapping.class);
				baseUrl = requestMapping.value();
			}
			Method[] methods = clazz.getMethods();
			for (Method method : methods) {
				if (!method.isAnnotationPresent(MyRequestMapping.class)) {
					continue;
				}
				MyRequestMapping requestMapping = method.getAnnotation(MyRequestMapping.class);
				String url = (baseUrl + requestMapping.value()).replaceAll("/+", "/");
				Pattern pattern = Pattern.compile(url);
				handlerMapping.add(new Handler(pattern, entry.getValue(), method));
				System.out.println("mapped:" + url + "=>" + method);
			}
		}
	}

	/**
	 * 对属性进行注入
	 */
	private void doAutoWired() {
		if (ioc.isEmpty()) return;
		for (Map.Entry<String, Object> entry : ioc.entrySet()) {
			//依赖注入->给加了MyAutowired注解的字段赋值
			Field[] fields = entry.getValue().getClass().getDeclaredFields();
			for (Field field : fields) {
				if (!field.isAnnotationPresent(MyAutowired.class)) {
					continue;
				}
				MyAutowired autowired = field.getAnnotation(MyAutowired.class);
				String beanName = autowired.value();
				if ("".equals(beanName)) {
					beanName = field.getType().getName();
				}
				// 禁用安全检查的开关，可以访问私有属性，并且提高访问速度
				field.setAccessible(true);
				try {
					// Field属性设置新值value
					field.set(entry.getValue(), ioc.get(beanName));
				} catch (IllegalAccessException e) {
					e.printStackTrace();
					continue;
				}
			}
		}
	}

	/**
	 * 创建对象保存到对象map集合中
	 */
	private void doInstance() {
		if (classNames.isEmpty()) return;
		try {
			for (String className : classNames) {
				Class<?> clazz = Class.forName(className);
				if (clazz.isAnnotationPresent(MyController.class)) {
					String beanName = lowerFirstCase(clazz.getSimpleName());
					ioc.put(beanName, clazz.newInstance());
				} else if (clazz.isAnnotationPresent(MyService.class)) {

					MyService service = clazz.getAnnotation(MyService.class);
					String beanName = service.value();
					if ("".equals(beanName)) {
						beanName = lowerFirstCase(clazz.getSimpleName());
					}
					Object instance = clazz.newInstance();
					ioc.put(beanName, instance);
					// 为所有接口均记录对应的实现类，因为任何接口均能配置依赖
					Class<?>[] interfaces = clazz.getInterfaces();
					for (Class<?> i : interfaces) {
						ioc.put(i.getName(), instance);
					}
				} else {
					continue;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 递归扫描包路径，把类信息保存到集合中
	 * @param packageName
	 */
	private void doScanner(String packageName) {
		URL resource =
				this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.", "/"));
		File classDir = new File(resource.getFile());
		for (File classFile : classDir.listFiles()) {
			if (classFile.isDirectory()) {
				doScanner(packageName + "." + classFile.getName());
			} else {
				String className = (packageName + "." + classFile.getName()).replace(".class", "");
				classNames.add(className);
			}
		}
	}

	/**
	 * 读取web.xml中执行的配置文件信息
	 * @param location
	 */
	private void doLoadConfig(String location) {
		//DefaultResourceLoader中getResource会替换classpath:
		if(location.startsWith("classpath:")){
			location = location.substring("classpath:".length());
		}
		InputStream input = this.getClass().getClassLoader().getResourceAsStream(location);
		try {
			contextConfig.load(input);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		this.doPost(req, res);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res)
			throws ServletException, IOException {
		try {
			doDispatcher(req, res);
		}catch (Exception e){
			res.getWriter().write("500 Exception Details:\r\n"+Arrays.toString(e.getStackTrace())
					.replaceAll("\\[|\\]","").replaceAll(",\\s","\r\n"));
		}
	}

	public void doDispatcher(HttpServletRequest req, HttpServletResponse res) {
		try {
			Handler handler = getHandler(req);
			if (handler == null) {
				res.getWriter().write("404 not found.");
				return;
			}
			Class<?>[] paramTypes = handler.method.getParameterTypes();
			Object[] paramValues = new Object[paramTypes.length];
			Map<String, String[]> params = req.getParameterMap();
			// 为占位的方法赋值参数
			for (Map.Entry<String, String[]> param : params.entrySet()) {
				String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]", "");
				if (!handler.paramIndexMapping.containsKey(param.getKey())) {
					continue;
				}
				int index = handler.paramIndexMapping.get(param.getKey());
				paramValues[index] = convert(paramTypes[index], value);
			}
			int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
			paramValues[reqIndex] = req;
			int resIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
			paramValues[resIndex] = res;
			// 调用url对应的类+方法
			handler.method.invoke(handler.controller, paramValues);
		} catch (Exception e) {
			e.printStackTrace();
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");

	}

	private Object convert(Class<?> type, String value) {
		if (Integer.class == type) {
			return Integer.valueOf(value);
		}
		return value;
	}

	private String lowerFirstCase(String str) {
		char[] chars = str.toCharArray();
		chars[0] += 32;
		return String.valueOf(chars);
	}

	private Handler getHandler(HttpServletRequest req) {
		if (handlerMapping.isEmpty()) {
			return null;
		}
		String url = req.getRequestURI();
		String contextPath = req.getContextPath();
		url = url.replace(contextPath, "").replaceAll("/+", "/");
		for (Handler handler : handlerMapping) {
			// 正则方式匹配url，为了方便restful方式传参匹配
			Matcher matcher = handler.pattern.matcher(url);
			if (!matcher.matches()) {
				continue;
			}
			return handler;
		}
		return null;
	}

	private class Handler {
		protected Object controller;
		protected Method method;
		protected Pattern pattern;
		protected Map<String, Integer> paramIndexMapping;

		protected Handler(Pattern pattern, Object controller, Method method) {
			this.pattern = pattern;
			this.controller = controller;
			this.method = method;
			paramIndexMapping = new HashMap<String, Integer>();
			putParamIndexMapping(method);
		}

		/**
		 * 对参数进行占位
		 * @param method
		 */
		private void putParamIndexMapping(Method method) {
			Annotation[][] pa = method.getParameterAnnotations();
			for (int i = 0; i < pa.length; i++) {
				for (Annotation a : pa[i]) {
					if (a instanceof MyRequestParam) {
						String paramName = ((MyRequestParam) a).value();
						if (!"".equals(paramName)) {
							paramIndexMapping.put(paramName, i);
						}
					}
				}
			}
			Class<?>[] paramTypes = method.getParameterTypes();
			for (int i = 0; i < paramTypes.length; i++) {
				Class<?> type = paramTypes[i];
				if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
					paramIndexMapping.put(type.getName(), i);
				}
			}
		}
	}
}