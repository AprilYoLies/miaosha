package top.aprilyolies.miaosha.controller;

import top.aprilyolies.miaosha.access.AccessLimit;
import top.aprilyolies.miaosha.domain.MiaoshaOrder;
import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.rabbitmq.MQSender;
import top.aprilyolies.miaosha.rabbitmq.MiaoshaMessage;
import top.aprilyolies.miaosha.redis.GoodsKey;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.result.CodeMsg;
import top.aprilyolies.miaosha.result.Result;
import top.aprilyolies.miaosha.service.GoodsService;
import top.aprilyolies.miaosha.service.MiaoshaService;
import top.aprilyolies.miaosha.service.MiaoshaUserService;
import top.aprilyolies.miaosha.service.OrderService;
import top.aprilyolies.miaosha.vo.GoodsVo;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;

@Controller
@RequestMapping("/miaosha")
public class MiaoshaController implements InitializingBean{

	@Autowired
	MiaoshaUserService userService;
	
	@Autowired
    RedisService redisService;
	
	@Autowired
	GoodsService goodsService;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	MiaoshaService miaoshaService;

	@Autowired
	MQSender mqSender;
	// 记录在 localOverMap 中的秒杀商品状态信息
	private HashMap<Long, Boolean> localOverMap =  new HashMap<Long, Boolean>();
	//系统初始化 预加载数量，将全部的秒杀商品的库存记录在 redis 中，localOverMap 记录了商品的状态信息
	@Override
	public void afterPropertiesSet() throws Exception {
		List<GoodsVo> goodsVoList=goodsService.listGoodsVo();
		if (goodsVoList==null){
			return;
		}	// 将全部的秒杀商品的库存记录在 redis 中，localOverMap 记录了商品的状态信息
		for(GoodsVo goods:goodsVoList){
			redisService.set(GoodsKey.getMiaoshaGoodsStock,""+goods.getId(),goods.getStockCount());
			localOverMap.put(goods.getId(), false);
		}
	}

	/**
	 * QPS:1306
	 * 5000 * 10
	 * */
	/**
	 *  GET POST有什么区别？localOverMap 记录了商品的秒杀状态，减少 redis 的访问，修改库存，检查库存是否足够，对于同样的用户，只能参与秒杀一次，最后将秒杀的结果放到 mq 中
	 * */
    @RequestMapping(value="/do_miaosha", method=RequestMethod.POST)
    @ResponseBody
    public Result<Integer> miaosha(Model model, MiaoshaUser user,
                                     @RequestParam("goodsId")long goodsId) {
    	model.addAttribute("user", user);
    	if(user == null) {
    		return Result.error(CodeMsg.SESSION_ERROR);
    	}
		//内存标记，减少redis访问
		boolean over = localOverMap.get(goodsId);	// 记录在 localOverMap 中的秒杀商品状态信息
		if(over) {
			return Result.error(CodeMsg.MIAO_SHA_OVER);
		}
		//判断是否已经秒杀到了，就是看是否已经秒杀过了
		MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
		if(order != null) {
			return Result.error(CodeMsg.REPEATE_MIAOSHA);
		}
		//预减库存
		Long stock = redisService.decr(GoodsKey.getMiaoshaGoodsStock, "" + goodsId);
		if (stock<0){	// 如果库存数量不够了，更新商品秒杀状态
			localOverMap.put(goodsId, true);
			return Result.error(CodeMsg.MIAO_SHA_OVER);
		}
    	//入队
		MiaoshaMessage mm=new MiaoshaMessage();
		mm.setUser(user);
		mm.setGoodsid(goodsId);
		mqSender.sendMiaoshaMessage(mm);	// {"goodsid":1,"user":{"id":15674400520,"lastLoginDate":1565250497000,"loginCount":0,"nickname":"90後","password":"99f786f83a478faa391a527df047ad3e","registerDate":1565250493000,"salt":"abcdef"}}
		return Result.success(0);//排队中
//    	//判断库存
//    	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);//10个商品，req1 req2
//    	int stock = goods.getStockCount();
//    	if(stock <= 0) {
//    		return Result.error(CodeMsg.MIAO_SHA_OVER);
//    	}
//    	//判断是否已经秒杀到了
//    	MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
//    	if(order != null) {
//    		return Result.error(CodeMsg.REPEATE_MIAOSHA);
//    	}
//    	//减库存 下订单 写入秒杀订单
//    	OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
//        return Result.success(orderInfo);
    }

	/**
	 * orderId：成功
	 * -1：秒杀失败
	 * 0： 排队中	// 获取自己的秒杀结果，即订单信息，缓存在 redis 中，即 OrderKey:moug15674400520_1 对应的 value 信息，最后返回订单 id 信息
	 * */
	@RequestMapping(value="/result", method=RequestMethod.GET)
	@ResponseBody
	public Result<Long> miaoshaResult(Model model,MiaoshaUser user,
									  @RequestParam("goodsId")long goodsId) {
		model.addAttribute("user", user);
		if(user == null) {
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		long result  =miaoshaService.getMiaoshaResult(user.getId(), goodsId);
		return Result.success(result);
	}

	/**
	 * 获取path 隐藏秒杀接口
	 * 自定义注解 限制在规定时间内 请求次数
	 * seconds 代表秒数
	 * maxCount 代表最大请求数
	 * needLogin 是否需要登录
	 */

	@AccessLimit(seconds=5, maxCount=5, needLogin=true)
	@RequestMapping(value = "/path",method = RequestMethod.GET)
	@ResponseBody
	public Result<String> getMiaoShaPath(HttpServletRequest request,MiaoshaUser user,@RequestParam("goodsId") long goodsId,
										 @RequestParam(value="verifyCode", defaultValue="0") int verifyCode){
		if (user==null){
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		//查看验证码是否正确，从缓存中获取验证的结果，然后和用户输入的结果进行比对
		boolean check=miaoshaService.checkVerifyCode(user,goodsId,verifyCode);
		if (!check){
			return Result.error(CodeMsg.REQUEST_ILLEGAL);
		}
		// 秒杀开始前获取path 用于请求秒杀接口对比验证，秒杀路劲的缓存格式如 MiaoshaKey:mp15674400520_1 -> 69f65225b8d3531906f13f60bae4df7d
		String path=miaoshaService.createMiaoShaPath(user,goodsId);
		return Result.success(path);
	}

	/**
	 * 获取验证码，将验证码的结果存放到了缓存中，格式为 MiaoshaKey:vc15674400520,1（商品 id） -> 1，然后将验证码返回
	 */
	@RequestMapping(value = "/verifyCode",method = RequestMethod.GET)
	@ResponseBody
	public Result<String> getMiaoshaVerifyCode(HttpServletRequest request, MiaoshaUser user, @RequestParam("goodsId") long goodsId,
											   HttpServletResponse response){
		if (user==null){
			return Result.error(CodeMsg.SESSION_ERROR);
		}
		try {	// 生产验证码，将验证码的结果存放到了缓存中，格式为 MiaoshaKey:vc15674400520,1（商品 id） -> 1
			BufferedImage image  = miaoshaService.createVerifyCode(user, goodsId);
			OutputStream out = response.getOutputStream();
			ImageIO.write(image, "JPEG", out);
			out.flush();
			out.close();
			return null;
		}catch (Exception e){
			e.printStackTrace();
			return Result.error(CodeMsg.MIAOSHA_FAIL);
		}
	}

}
