package top.aprilyolies.miaosha.service;

import top.aprilyolies.miaosha.dao.OrderDao;
import top.aprilyolies.miaosha.domain.MiaoshaOrder;
import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.domain.OrderInfo;
import top.aprilyolies.miaosha.redis.OrderKey;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class OrderService {
	
	@Autowired
    OrderDao orderDao;
	
	@Autowired
    RedisService redisService;
	// 获取 OrderKey:moug15674400520_1 对应的 value 信息
	public MiaoshaOrder getMiaoshaOrderByUserIdGoodsId(long userId, long goodsId) {
		//return orderDao.getMiaoshaOrderByUserIdGoodsId(userId, goodsId);
		return redisService.get(OrderKey.getMiaoshaOrderByUidGid, ""+userId+"_"+goodsId, MiaoshaOrder.class);
	}	// 获取 OrderKey:moug15674400520_1 对应的 value 信息
	// 根据 id 查询数据库中的订单信息
	public OrderInfo getOrderById(long orderId) {
		return orderDao.getOrderById(orderId);
	}
	
	// 创建订单信息，包括写入数据库和写入缓存两点
	@Transactional
	public OrderInfo createOrder(MiaoshaUser user, GoodsVo goods) {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setCreateDate(new Date());
		orderInfo.setDeliveryAddrId(0L);
		orderInfo.setGoodsCount(1);
		orderInfo.setGoodsId(goods.getId());
		orderInfo.setGoodsName(goods.getGoodsName());
		orderInfo.setGoodsPrice(goods.getMiaoshaPrice());
		orderInfo.setOrderChannel(1);
		orderInfo.setStatus(0);
		orderInfo.setUserId(user.getId());
		long orderId = orderDao.insert(orderInfo);	// 保存订单信息，修改的是数据库信息
		MiaoshaOrder miaoshaOrder = new MiaoshaOrder();
		miaoshaOrder.setGoodsId(goods.getId());
		miaoshaOrder.setOrderId(orderInfo.getId());
		miaoshaOrder.setUserId(user.getId());
		orderDao.insertMiaoshaOrder(miaoshaOrder);	// 保存秒杀订单信息
		// 同时将秒杀订单存入缓存，格式如 OrderKey:moug15674400520_1 -> {"goodsId":1,"orderId":1,"userId":15674400520}
		redisService.set(OrderKey.getMiaoshaOrderByUidGid, ""+user.getId()+"_"+goods.getId(), miaoshaOrder);
		 
		return orderInfo;
	}

}
