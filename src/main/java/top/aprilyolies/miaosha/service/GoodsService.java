package top.aprilyolies.miaosha.service;

import top.aprilyolies.miaosha.dao.GoodsDao;
import top.aprilyolies.miaosha.domain.MiaoshaGoods;
import top.aprilyolies.miaosha.vo.GoodsVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GoodsService {
	
	@Autowired
    GoodsDao goodsDao;
	
	public List<GoodsVo> listGoodsVo(){
		return goodsDao.listGoodsVo();
	}
	// 根据商品 id 获取商品详情
	public GoodsVo getGoodsVoByGoodsId(long goodsId) {
		return goodsDao.getGoodsVoByGoodsId(goodsId);
	}
	// 将秒杀商品的库存数量减一
	public boolean reduceStock(GoodsVo goods) {
		MiaoshaGoods g = new MiaoshaGoods();
		g.setGoodsId(goods.getId());
		int ret=goodsDao.reduceStock(g);	// 将秒杀商品的库存数量减一
		return ret>0;
	}
	
	
	
}
