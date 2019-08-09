package top.aprilyolies.miaosha.access;


import com.alibaba.druid.util.StringUtils;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.redis.AccessKey;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.result.CodeMsg;
import top.aprilyolies.miaosha.result.Result;
import top.aprilyolies.miaosha.service.MiaoshaUserService;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;

/**
 * 实现拦截器 在方法之前进行拦截，用来限制某个用户对于某个链接的访问过于频繁
 */

@Component
public class AccessInterceptor extends HandlerInterceptorAdapter{

    private MiaoshaUserService userService;

    private RedisService redisService;

    public AccessInterceptor(MiaoshaUserService userService, RedisService redisService) {
        this.userService = userService;
        this.redisService = redisService;
    }
    // 该处理器就是为了避免用户过于频繁的访问某个连接，将路径和用户进行绑定，当访问超过某个次数后，便直接返回访问频繁信息
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (handler instanceof HandlerMethod){
            MiaoshaUser user=getUser(request,response); // 根据 token 从缓存中获取用户信息，如果缓存中的用户信息已经过期，那么将不会获取到用户信息，如果获取到用户信息，将会刷新缓存过期时间
            UserContext.setUser(user);  // 将 user 信息保存到线程本地变量中
            HandlerMethod hm = (HandlerMethod)handler;
            AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);    // 看被访问的方法上是否有 AccessLimit 注解
            if(accessLimit == null) {   // 这里说明该拦截器只会对指定链接进行拦截
                return true;
            }
            int seconds = accessLimit.seconds();
            int maxCount = accessLimit.maxCount();
            boolean needLogin = accessLimit.needLogin();
            String key = request.getRequestURI();
            if(needLogin) {
                if(user == null) {
                    render(response, CodeMsg.SESSION_ERROR);    // Session不存在或者已经失效
                    return false;
                }
                key += "_" + user.getId();
            }else {
                //do nothing
            }
            AccessKey ak = AccessKey.withExpire(seconds);
            Integer count = redisService.get(ak, key, Integer.class);   // 查询的 key 为 AccessKey:access/miaosha/path_15674400520
            if(count  == null) {
                redisService.set(ak, key, 1);   // 向 redis 中写入 AccessKey:access/miaosha/path_15674400520 -> 1
            }else if(count < maxCount) {
                redisService.incr(ak, key);
            }else {
                render(response, CodeMsg.ACCESS_LIMIT_REACHED); // 这里就是访问频繁处理
                return false;
            }
        }
        return true;
    }
    // 写错误信息
    private void render(HttpServletResponse response, CodeMsg cm)throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        OutputStream out = response.getOutputStream();
        String str  = JSON.toJSONString(Result.error(cm));
        out.write(str.getBytes("UTF-8"));
        out.flush();
        out.close();
    }

    // 根据 token 从缓存中获取用户信息，如果缓存中的用户信息已经过期，那么将不会获取到用户信息，如果获取到用户信息，将会刷新缓存过期时间
    private MiaoshaUser getUser(HttpServletRequest request, HttpServletResponse response) {
        String paramToken = request.getParameter(MiaoshaUserService.COOKI_NAME_TOKEN);  // request 中的 token 信息
        String cookieToken = getCookieValue(request, MiaoshaUserService.COOKI_NAME_TOKEN);  // cookies 中的 token 信息
        if(StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {   // 如果没有 token 信息，直接返回
            return null;
        }
        String token = StringUtils.isEmpty(paramToken)?cookieToken:paramToken;
        return userService.getByToken(response, token); // 根据 token 从缓存中获取用户信息，如果缓存中的用户信息已经过期，那么将不会获取到用户信息，如果获取到用户信息，将会刷新缓存过期时间
    }
    // 从 cookies 中获取参数指定的 cookie
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
