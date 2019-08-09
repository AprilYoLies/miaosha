package top.aprilyolies.miaosha.controller;

import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.result.Result;
import top.aprilyolies.miaosha.service.MiaoshaUserService;
import top.aprilyolies.miaosha.vo.LoginVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@Controller
@RequestMapping("/login")
public class LoginController {

	private static Logger log = LoggerFactory.getLogger(LoginController.class);
	
	@Autowired
    MiaoshaUserService userService;
	
	@Autowired
	RedisService redisService;
	// 跳转用户登录界面
    @RequestMapping("/to_login")
    public String toLogin() {
        return "login";
    }
    // 用户登录处理
    @RequestMapping("/do_login")
    @ResponseBody
    public Result<String> doLogin(HttpServletResponse response, @Valid LoginVo loginVo) {
    	log.info(loginVo.toString());
        // 获取登录信息，先根据缓存比较，没有的话就从数据库查，并缓存，验证密码，成功后生成 token 信息通过 response 返回
    	String token = userService.login(response, loginVo);
    	return Result.success(token);
    }
}
