package top.aprilyolies.miaosha.service;

import top.aprilyolies.miaosha.dao.MiaoshaUserDao;
import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.exception.GlobalException;
import top.aprilyolies.miaosha.redis.MiaoshaUserKey;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.result.CodeMsg;
import top.aprilyolies.miaosha.util.MD5Util;
import top.aprilyolies.miaosha.util.UUIDUtil;
import top.aprilyolies.miaosha.vo.LoginVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

@Service
public class MiaoshaUserService {
	
	
	public static final String COOKI_NAME_TOKEN = "token";
	
	@Autowired
    MiaoshaUserDao miaoshaUserDao;
	
	@Autowired
	RedisService redisService;
	
	public MiaoshaUser getById(long id) {
		//取缓存，看缓存里边是否有用户信息
		MiaoshaUser user = redisService.get(MiaoshaUserKey.getById, ""+id, MiaoshaUser.class);
		if(user != null) {
			return user;
		}
		//取数据库
		user = miaoshaUserDao.getById(id);
		if(user != null) {
			redisService.set(MiaoshaUserKey.getById, ""+id, user);	// 缓存用户信息
		}
		return user;
	}
	// http://blog.csdn.net/tTU1EvLDeLFq5btqiK/article/details/78693323
	public boolean updatePassword(String token, long id, String formPass) {
		//取user
		MiaoshaUser user = getById(id);
		if(user == null) {
			throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
		}
		//更新数据库
		MiaoshaUser toBeUpdate = new MiaoshaUser();
		toBeUpdate.setId(id);
		toBeUpdate.setPassword(MD5Util.formPassToDBPass(formPass, user.getSalt()));
		miaoshaUserDao.update(toBeUpdate);	// 先更新数据库信息
		//处理缓存
		redisService.delete(MiaoshaUserKey.getById, ""+id);	// 再删除缓存信息
		user.setPassword(toBeUpdate.getPassword());
		redisService.set(MiaoshaUserKey.token, token, user);	// 更新缓存信息
		return true;
	}

	// 根据 token 从缓存中获取用户信息，如果缓存中的用户信息已经过期，那么将不会获取到用户信息，如果获取到用户信息，将会刷新缓存过期时间
	public MiaoshaUser getByToken(HttpServletResponse response, String token) {
		if(StringUtils.isEmpty(token)) {
			return null;
		}
		MiaoshaUser user = redisService.get(MiaoshaUserKey.token, token, MiaoshaUser.class);
		//延长有效期
		if(user != null) {
			addCookie(response, token, user);
		}
		return user;
	}
	
	// 获取登录信息，先根据缓存比较，没有的话就从数据库查，并缓存，验证密码，成功后生成 token 信息通过 response 返回
	public String login(HttpServletResponse response, LoginVo loginVo) {
		if(loginVo == null) {
			throw new GlobalException(CodeMsg.SERVER_ERROR);
		}
		String mobile = loginVo.getMobile();
		String formPass = loginVo.getPassword();
		//判断手机号是否存在，优先从缓存中查询，无结果的话就到数据库中，然后将结果缓存
		MiaoshaUser user = getById(Long.parseLong(mobile));
		if(user == null) {
			throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
		}
		//验证密码
		String dbPass = user.getPassword();
		String saltDB = user.getSalt();
		String calcPass = MD5Util.formPassToDBPass(formPass, saltDB);	// 表格密码加盐后再进行 md5 加密
		if(!calcPass.equals(dbPass)) {
			throw new GlobalException(CodeMsg.PASSWORD_ERROR);
		}
		//生成cookie
		String token	 = UUIDUtil.uuid();	// 通过 cookie 进行用户追踪
		addCookie(response, token, user);	// 为响应添加 cookie
		return token;
	}
	// 为响应添加 cookie，同时将 token 信息缓存到 redis 中，token -> user
	private void addCookie(HttpServletResponse response, String token, MiaoshaUser user) {
		redisService.set(MiaoshaUserKey.token, token, user);	// 将 token 信息也保存到 redis 中，token -> user
		Cookie cookie = new Cookie(COOKI_NAME_TOKEN, token);
		cookie.setMaxAge(MiaoshaUserKey.token.expireSeconds());	// 设置 token 的过期时间
		cookie.setPath("/");
		response.addCookie(cookie);
	}

}
