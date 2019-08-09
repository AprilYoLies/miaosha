package top.aprilyolies.miaosha.config;

import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.service.MiaoshaUserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
// 方法参数解析器，就本类而言，如果方法参数有 MiaoshaUser 实例，那么就会交由该类进行处理
@Service
public class UserArgumentResolver implements HandlerMethodArgumentResolver {

	@Autowired
    MiaoshaUserService userService;
	// 这个方法就是来判断参数是否支持的
	public boolean supportsParameter(MethodParameter parameter) {
		Class<?> clazz = parameter.getParameterType();
		return clazz==MiaoshaUser.class;
	}
	// 尝试从 request 域和 cookie 中获取 token 信息，然后根据此 token 从缓存中得到 user 信息，如果缓存没过期，就能获取到
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		// 优先获取 request 域中的 token 参数，然后获取 cookie 中的 token 参数
		String paramToken = request.getParameter(MiaoshaUserService.COOKI_NAME_TOKEN);
		String cookieToken = getCookieValue(request, MiaoshaUserService.COOKI_NAME_TOKEN);	// 获取 cookie 参数指定的 cookie
		if(StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {
			return null;
		}
		String token = StringUtils.isEmpty(paramToken)?cookieToken:paramToken;
		return userService.getByToken(response, token);
	}	// 根据 token 从缓存中获取用户信息，如果缓存中的用户信息已经过期，那么将不会获取到用户信息，如果获取到用户信息，将会刷新缓存过期时间
	// 获取 cookie 参数指定的 cookie
	private String getCookieValue(HttpServletRequest request, String cookiName) {
		Cookie[]  cookies = request.getCookies();
		if(cookies == null || cookies.length <= 0){
			return null;
		}
		for(Cookie cookie : cookies) {
			if(cookie.getName().equals(cookiName)) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
