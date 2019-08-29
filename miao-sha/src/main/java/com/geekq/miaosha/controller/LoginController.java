package com.geekq.miaosha.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.geekq.miaosha.common.resultbean.ResultGeekQ;
import com.geekq.miaosha.redis.redismanager.RedisLua;
import com.geekq.miaosha.service.MiaoShaUserService;
import com.geekq.miaosha.vo.LoginVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import static com.geekq.miaosha.common.Constanst.COUNTLOGIN;

@Controller
@RequestMapping("/login")
public class LoginController {

    private static Logger logger = LoggerFactory.getLogger(LoginController.class);

    @Autowired
    private MiaoShaUserService userService;
    @Reference
    @RequestMapping("/to_login")
    public String tologin(LoginVo loginVo, Model model) {   // 前往登录页面，返回过程会统计站点访问次数
        logger.info(loginVo.toString());
        //未完成
          RedisLua.vistorCount(COUNTLOGIN);
        String count = RedisLua.getVistorCount(COUNTLOGIN).toString();
        logger.info("访问网站的次数为:{}",count);
        model.addAttribute("count",count);
        return "login";
    }
    // 优先查看缓存中是否有用户信息，如果有的话，就获取，否则查询数据库，并更新缓存，比对密码，然后生成用户的 token 并缓存，同时将该 token 通过 cookie 返回给用户
    @RequestMapping("/do_login")
    @ResponseBody
    public ResultGeekQ<Boolean> dologin(HttpServletResponse response, @Valid LoginVo loginVo) {
        ResultGeekQ<Boolean> result = ResultGeekQ.build();
        logger.info(loginVo.toString());
        userService.login(response, loginVo);   // 优先查看缓存中是否有用户信息，如果有的话，就获取，否则查询数据库，并更新缓存，比对密码，然后生成用户的 token 并缓存，同时将该 token 通过 cookie 返回给用户
        return result;
    }


    @RequestMapping("/create_token")
    @ResponseBody
    public String createToken(HttpServletResponse response, @Valid LoginVo loginVo) {
        logger.info(loginVo.toString());
        String token = userService.createToken(response, loginVo);
        return token;
    }
}
