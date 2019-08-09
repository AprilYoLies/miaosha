package top.aprilyolies.miaosha.rabbitmq;

import top.aprilyolies.miaosha.domain.MiaoshaOrder;
import top.aprilyolies.miaosha.domain.MiaoshaUser;
import top.aprilyolies.miaosha.redis.RedisService;
import top.aprilyolies.miaosha.service.GoodsService;
import top.aprilyolies.miaosha.service.MiaoshaService;
import top.aprilyolies.miaosha.service.MiaoshaUserService;
import top.aprilyolies.miaosha.service.OrderService;
import top.aprilyolies.miaosha.vo.GoodsVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author dcg
 * Created by user on 2018/4/15.
 */
@Service
public class MQReceiver {
    @Autowired
    MiaoshaUserService userService;

    @Autowired
    RedisService redisService;

    @Autowired
    GoodsService goodsService;

    @Autowired
    MiaoshaService miaoshaService;

    @Autowired
    OrderService orderService;
    // 修改数据库中的库存信息，创建订单信息，包括写入数据库和写入缓存两点
    private static Logger log=LoggerFactory.getLogger(MQReceiver.class);
    @RabbitListener(queues = MQConfig.MIAOSHA_QUEUE)    // 该注解会启动 rabbitmq 客户端，然后能够自动的消费 mq 中的消息
    public void receive(String message){
        log.info("receive message:"+message );
        MiaoshaMessage mm = RedisService.stringToBean(message, MiaoshaMessage.class);   // 将消息转换为 MiaoshaMessage 实例
        MiaoshaUser user = mm.getUser();
        long goodsid = mm.getGoodsid();
        // 根据商品 id 获取商品信息
        GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsid);//10个商品，req1 req2
    	int stock = goods.getStockCount();
    	if(stock <= 0) {
    		return ;
    	}
        // 判断是否已经秒杀到了，确保用户不会重复秒杀
    	MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsid);
    	if(order != null) {
    		return ;
    	}
    	// 减库存 下订单 写入秒杀订单，修改数据库中的库存信息，创建订单信息，包括写入数据库和写入缓存两点
        miaoshaService.miaosha(user, goods);
    }

//    @RabbitListener(queues=MQConfig.TOPIC_QUEUE1)
//    public void receiveTopic1(String message) {
//        log.info(" topic  queue1 message:"+message);
//    }
//
//    @RabbitListener(queues=MQConfig.TOPIC_QUEUE2)
//    public void receiveTopic2(String message) {
//        log.info(" topic  queue2 message:"+message);
//    }
}
