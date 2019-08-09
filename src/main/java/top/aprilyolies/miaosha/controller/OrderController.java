package top.aprilyolies.miaosha.controller;

import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.domain.OrderInfo;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.result.CodeMsg;
import top.aprilyolies.miaosha.result.Result;
import top.aprilyolies.miaosha.service.GoodsService;
import top.aprilyolies.miaosha.service.MiaoshaUserService;
import top.aprilyolies.miaosha.service.OrderService;
import top.aprilyolies.miaosha.vo.GoodsVo;
import top.aprilyolies.miaosha.vo.OrderDetailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/order")
public class OrderController {

	@Autowired
    MiaoshaUserService userService;
	
	@Autowired
    RedisService redisService;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	GoodsService goodsService;
	// 根据订单 id 查询订单信息，然后根据其中的商品 id 查询商品信息，直接返回订单和商品信息
    @RequestMapping("/detail")
    @ResponseBody
    public Result<OrderDetailVo> info(Model model, MiaoshaUser user,
									  @RequestParam("orderId") long orderId) {
    	if(user == null) {
    		return Result.error(CodeMsg.SESSION_ERROR);
    	}
    	OrderInfo order = orderService.getOrderById(orderId);
    	if(order == null) {
    		return Result.error(CodeMsg.ORDER_NOT_EXIST);
    	}
    	long goodsId = order.getGoodsId();
    	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);	// 根据商品 id 获取商品详情
    	OrderDetailVo vo = new OrderDetailVo();
    	vo.setOrder(order);
    	vo.setGoods(goods);
    	return Result.success(vo);
    }
    
}
