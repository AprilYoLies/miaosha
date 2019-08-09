package top.aprilyolies.miaosha.service;

import top.aprilyolies.miaosha.domain.MiaoshaOrder;
import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.domain.OrderInfo;
import top.aprilyolies.miaosha.redis.MiaoshaKey;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.util.MD5Util;
import top.aprilyolies.miaosha.util.UUIDUtil;
import top.aprilyolies.miaosha.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Random;

@Service
public class MiaoshaService {
	
	@Autowired
	GoodsService goodsService;
	
	@Autowired
	OrderService orderService;

	@Autowired
	RedisService redisService;


	@Transactional
	public OrderInfo miaosha(MiaoshaUser user, GoodsVo goods) {
		//减库存 下订单 写入秒杀订单
		boolean success = goodsService.reduceStock(goods);
		if (success){
			//order_info maiosha_order
			return orderService.createOrder(user, goods);
		}
		else {
			setGoodsOver(goods.getId());
			return null;
		}
	}
	// 获取自己的秒杀结果，即订单信息，缓存在 redis 中，即 OrderKey:moug15674400520_1 对应的 value 信息
	public long getMiaoshaResult(Long userId, long goodsId) {
		MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(userId, goodsId);
		if (order!=null){//秒杀成功
			return order.getOrderId();
		}else {
			boolean isOver=getGoodsOver(goodsId);
			if (isOver){
				return -1;
			}else {
				return 0;
			}
		}
	}

	private void setGoodsOver(Long goodsId) {
		redisService.set(MiaoshaKey.isGoodsOver, ""+goodsId, true);
	}

	private boolean getGoodsOver(long goodsId) {
		return redisService.exists(MiaoshaKey.isGoodsOver, ""+goodsId);
	}

	/**
	 * 检查验证码是否正确，从缓存中获取验证的结果，然后和用户输入的结果进行比对
	 * @param user
	 * @param goodsId
	 * @param verifyCode
	 * @return
	 */
	public boolean checkVerifyCode(MiaoshaUser user, long goodsId, int verifyCode) {
		if (user==null||goodsId<0){
			return false;
		}	// 获取缓存中的验证码结果值
		Integer codeOld=redisService.get(MiaoshaKey.getMiaoshaVerifyCode,user.getId()+","+goodsId,Integer.class);
		if (codeOld==null||codeOld-verifyCode!=0){
			return false;
		}
		return true;
	}

	/**
	 * 秒杀开始前获取path 用于请求秒杀接口对比验证，秒杀路劲的缓存格式如 MiaoshaKey:mp15674400520_1 -> 69f65225b8d3531906f13f60bae4df7d
	 * @param user
	 * @param goodsId
	 * @return
	 */
	public String createMiaoShaPath(MiaoshaUser user, long goodsId) {
		if (user==null||goodsId<0){
			return null;
		}
		String str= MD5Util.md5(UUIDUtil.uuid()+"123456");
		redisService.set(MiaoshaKey.getMiaoshaPath, ""+user.getId() + "_"+ goodsId, str);	// 这里是缓存用户的秒杀路径
		return str;
	}

	/**
	 * 生产验证码，将验证码的结果存放到了缓存中，格式为 MiaoshaKey:vc15674400520,1（商品 id） -> 1
	 * @param user
	 * @param goodsId
	 * @return
	 */
	public BufferedImage createVerifyCode(MiaoshaUser user, long goodsId) {
		if(user==null||goodsId<0){
			return null;
		}
		int width = 80;
		int height = 32;
		//create the image
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics g = image.getGraphics();
		// set the background color
		g.setColor(new Color(0xDCDCDC));
		g.fillRect(0, 0, width, height);
		// draw the border
		g.setColor(Color.black);
		g.drawRect(0, 0, width - 1, height - 1);
		// create a random instance to generate the codes
		Random rdm = new Random();
		// make some confusion
		for (int i = 0; i < 50; i++) {
			int x = rdm.nextInt(width);
			int y = rdm.nextInt(height);
			g.drawOval(x, y, 0, 0);
		}
		// generate a random code
		String verifyCode = generateVerifyCode(rdm);	// 创建验证字符串，+ - * 随机组合
		g.setColor(new Color(0, 100, 0));
		g.setFont(new Font("Candara", Font.BOLD, 24));
		g.drawString(verifyCode, 8, 24);
		g.dispose();
		//把验证码存到redis中
		int rnd = calc(verifyCode);	// 表达式计算
		redisService.set(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId, rnd);	// MiaoshaKey:vc15674400520,1 -> 1
		//输出图片
		return image;
	}
	// 表达式计算
	private int calc(String exp) {
		try {
			ScriptEngineManager manager = new ScriptEngineManager();
			ScriptEngine engine = manager.getEngineByName("JavaScript");
			return (Integer)engine.eval(exp);
		}catch(Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	private static char[] ops = new char[] {'+', '-', '*'};
	/**
	 * + - *，创建验证字符串，+ - * 随机组合
	 * */
	private String generateVerifyCode(Random rdm) {
		int num1 = rdm.nextInt(10);
		int num2 = rdm.nextInt(10);
		int num3 = rdm.nextInt(10);
		char op1 = ops[rdm.nextInt(3)];
		char op2 = ops[rdm.nextInt(3)];
		String exp = ""+ num1 + op1 + num2 + op2 + num3;
		return exp;
	}
}
