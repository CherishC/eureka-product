package cn.cherish.springcloud.product.service.impl;

import cn.cherish.springcloud.product.dal.entity.Product;
import cn.cherish.springcloud.product.dal.entity.Repertory;
import cn.cherish.springcloud.product.dal.repository.ProductDAO;
import cn.cherish.springcloud.product.dal.repository.RepertoryDAO;
import cn.cherish.springcloud.product.service.common.TimeUtil;
import cn.cherish.springcloud.product.service.common.exception.ServiceException;
import cn.cherish.springcloud.product.service.component.MZookeeper;
import cn.cherish.springcloud.product.service.component.rocketmq.OrderCreateMsg;
import cn.cherish.springcloud.product.service.component.rocketmq.RocketMQ;
import cn.cherish.springcloud.service.dto.ProductDTO;
import cn.cherish.springcloud.service.req.ProductReq;
import com.google.common.base.Throwables;
import me.cherish.dal.repository.IBaseDAO;
import me.cherish.service.ABaseService;
import me.cherish.util.ObjectConvertUtil;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@CacheConfig(cacheNames = "products")
public class ProductService extends ABaseService<Product, Long> {

    private static final String QUANTITY_KEY_FOR_REDIS = "products:quantity_";

    private final ProductDAO productDAO;
    private final RepertoryDAO repertoryDAO;

    @Resource(name = "redisTemplate")
    private ValueOperations<String, Integer> opsForQuantity;
    @Autowired
    private RocketMQ rocketMQ;
    @Autowired
    private MZookeeper zookeeper;

    @Autowired
    public ProductService(ProductDAO productDAO, RepertoryDAO repertoryDAO) {
        this.productDAO = productDAO;
        this.repertoryDAO = repertoryDAO;
    }

    @Override
    protected IBaseDAO<Product, Long> getEntityDAO() {
        return productDAO;
    }

    @Cacheable(key = "'id_'+#p0.id")
    @Transactional
    public ProductDTO update(ProductReq productReq) {

        Product product = findById(productReq.getId());
        if (product == null) return null;

        ObjectConvertUtil.objectCopy(product, productReq);
        product.setModifiedTime(new Date());

        Repertory repertory = repertoryDAO.findByProductId(product.getId());
        repertory.setQuantity(productReq.getQuantity());
        repertoryDAO.save(repertory);

        Product update = super.update(product);

        opsForQuantity.set(QUANTITY_KEY_FOR_REDIS + repertory.getProductId(), repertory.getQuantity());
        return getProductDTO(update);
    }

    @CachePut(key = "'id_'+#result.id")
    @Transactional
    public ProductDTO save(ProductReq productReq) {
        Product product = new Product();
        ObjectConvertUtil.objectCopy(product, productReq);
        product.setCreatedTime(new Date());
        product.setModifiedTime(new Date());

        Product save = super.save(product);

        Repertory repertory = new Repertory(save.getId(), productReq.getQuantity());
        repertoryDAO.save(repertory);

        opsForQuantity.set(QUANTITY_KEY_FOR_REDIS + repertory.getProductId(), repertory.getQuantity());

        return getProductDTO(save);
    }

    @CacheEvict(key = "'id_'+#p0")
    @Transactional
    public void delete(Long productId) {
        opsForQuantity.set(QUANTITY_KEY_FOR_REDIS + productId, 0);
        repertoryDAO.delete(productId);
        productDAO.delete(productId);
    }

    @Cacheable(key = "'id_'+#p0")
    public ProductDTO findOne(Long productId) {
        Product product = productDAO.findOne(productId);
        return getProductDTO(product);
    }

    public List<ProductDTO> findAllDTO() {
        List<Product> products = productDAO.findAll();
        return products.stream()
                .map(this::getProductDTO)
                .collect(Collectors.toList());
    }

    /**
     * 寻找出商品的库存量
     * @param productId 商品ID
     * @return Integer 库存量
     */
    public Integer findQuantity(Long productId) {
        String key = QUANTITY_KEY_FOR_REDIS + productId;
        Integer quantity = opsForQuantity.get(key);
        if (quantity == null) {
            Repertory repertory = repertoryDAO.findOne(productId);
            if (repertory == null) {
                quantity = 0;
            }else {
                quantity = repertory.getQuantity();
            }
            opsForQuantity.set(key, quantity);
        }
        return quantity;
    }

    /**
     * 提交下单数据(无效)
     * @param userId    用户ID
     * @param productDTO 商品, 其实是为了充分利用缓存！！！！
     * @param num       购买数量
     * @return boolean 是否成功下单
     */
    public boolean orderProduct(ProductDTO productDTO, Long userId, Integer num) {
        long now;
        {
            now = System.currentTimeMillis();
            log.debug("【TimeUtil】进入orderProduct方法 now:{} consume:{} threadID:{}", now,
                    now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(now);
        }

        Long productId = productDTO.getId();
        log.info("【生成订单】 userId:{} productId: {} num: {}", userId, productId, num);
        Integer quantity = findQuantity(productId);
        if (quantity <= 0) {
            log.info("【生成订单】 该商品已卖完 productId:{}", productId);
            throw new ServiceException("222", "该商品已卖完");
        }
        if (quantity - num < 0) {
            log.info("【生成订单】 该商品库存不足 productId:{}", productId);
            throw new ServiceException("223", "该商品库存不足");
        }

        {
            now = System.currentTimeMillis();
            log.debug("【TimeUtil】开始竞争全局锁 now:{} consume:{} threadID:{}", now,
                    now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(now);
        }

        // 传入 商品ID 获取全局锁
        InterProcessMutex worldLock = zookeeper.worldLockToCreateOrder(productId);

        long lockTime = System.currentTimeMillis();
        try {
            log.debug("【worldLock】 开始竞争全局锁:{} ", lockTime);
            // 等待 2s 去竞争锁
            boolean acquire = worldLock.acquire(2, TimeUnit.SECONDS);
            if (!acquire) {
                log.info("【worldLock】 2s 竞争锁失败，取消下单");
                throw new ServiceException("224", "服务器繁忙");
            }

            log.debug("【worldLock】 获取竞争全局锁:{} 消耗时间: {}", System.currentTimeMillis(), System.currentTimeMillis() - now);
            {
                now = System.currentTimeMillis();
                log.debug("【TimeUtil】获取竞争全局锁 now:{} consume:{} threadID:{}", now,
                        now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
                TimeUtil.threadLocalTime.set(now);
            }

            // 再次检查库存
            quantity = findQuantity(productId);
            if (quantity <= 0) {
                log.info("【生成订单】 该商品已卖完 productId:{}", productId);
                throw new ServiceException("222", "该商品已卖完");
            }
            if (quantity - num < 0) {
                log.info("【生成订单】 该商品库存不足 productId:{}", productId);
                throw new ServiceException("223", "该商品库存不足");
            }

            {
                now = System.currentTimeMillis();
                log.debug("【TimeUtil】 检查完库存 now:{} consume:{} threadID:{}", now,
                        now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
                TimeUtil.threadLocalTime.set(now);
            }

            // 实现减库存
            Integer newQuantity = quantity - num;
            String key = QUANTITY_KEY_FOR_REDIS + productId;
            opsForQuantity.set(key, newQuantity);
            log.info("【实现减库存】 quantity:{} newQuantity:{}", quantity, newQuantity);
            {
                now = System.currentTimeMillis();
                log.debug("【TimeUtil】 减完库存 now:{} consume:{} threadID:{}", now,
                        now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
                TimeUtil.threadLocalTime.set(now);
            }
        } catch (ServiceException e) {
            log.info("【ServiceException】异常 {}", Throwables.getStackTraceAsString(e));
            throw new ServiceException(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("【获取全局锁】异常 {}", Throwables.getStackTraceAsString(e));
            throw new ServiceException("225", "服务器繁忙");
        } finally {
            if (worldLock.isAcquiredInThisProcess()) {
                try {
                    worldLock.release();
                    log.debug("【worldLock】 全局锁减库存时间:{} ", System.currentTimeMillis() - lockTime);
                } catch (Exception e) {
                    log.error("【释放全局锁】 {}", Throwables.getStackTraceAsString(e));
                }
            }
        }
        // 减库存成功 END

        // 发送订单消息
        try {
            log.info("【生成订单】 提交订单给消息中间件 ");
            OrderCreateMsg orderCreateMsg = new OrderCreateMsg();
            orderCreateMsg.setProductId(productId);
            orderCreateMsg.setUserId(userId);
            orderCreateMsg.setNum(num);
            orderCreateMsg.setFee(productDTO.getPrice() * num);
            boolean sendMsgToCreateOrder = rocketMQ.sendMsgToCreateOrder(orderCreateMsg);
            {
                now = System.currentTimeMillis();
                log.debug("【TimeUtil】 发送完订单消息 now:{} consume:{} threadID:{}", now,
                        now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
                TimeUtil.threadLocalTime.set(now);
            }
            if (!sendMsgToCreateOrder) {
                log.info("【发送订单消息】 发送订单消息中间件失败 ");
                throw new Exception("发送订单消息中间件失败");
            }
        } catch (Exception e){
            log.info("【发送订单消息异常】 {}", Throwables.getStackTraceAsString(e));
            // 发送订单消息不成功
            try {
                // 去竞争 全局锁
                worldLock.acquire();
                // 实现加回库存
                quantity = findQuantity(productId);
                Integer backQuantity = quantity + num;
                String key = QUANTITY_KEY_FOR_REDIS + productId;
                opsForQuantity.set(key, backQuantity);
                log.info("【实现回加库存】 quantity:{} backQuantity:{}", quantity, backQuantity);

            } catch (Exception e1) {
                log.error("【回加库存全局锁异常】 {}", Throwables.getStackTraceAsString(e1));
                throw new ServiceException("225", "服务器繁忙");
            } finally {
                if (worldLock.isAcquiredInThisProcess()) {
                    try {
                        worldLock.release();
                        log.debug("【worldLock】 回加库存锁时间:{} ", System.currentTimeMillis() - lockTime);
                    } catch (Exception e2) {
                        log.error("【释放全局锁】 {}", Throwables.getStackTraceAsString(e2));
                    }
                }
            }
            return false;
        }
        return true;
    }

    /**
     * 使用redis incrby 操作整型库存量
     */
    public boolean orderProduct2(ProductDTO productDTO, Long userId, Integer num) {
        long now;
        if (log.isDebugEnabled()) {
            now = System.currentTimeMillis();
            log.debug("【TimeUtil】进入orderProduct方法 now:{} consume:{} threadID:{}", now,
                    now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(now);
        }

        Long productId = productDTO.getId();
        log.info("【生成订单】 userId:{} productId: {} num: {}", userId, productId, num);
        Integer quantity = findQuantity(productId);
        if (quantity <= 0) {
            log.info("【生成订单】 该商品已卖完 productId:{}", productId);
            throw new ServiceException("222", "该商品已卖完");
        }
        if (quantity - num < 0) {
            log.info("【生成订单】 该商品库存不足 productId:{}", productId);
            throw new ServiceException("223", "该商品库存不足");
        }

        if (log.isDebugEnabled()) {
            now = System.currentTimeMillis();
            log.debug("【TimeUtil】准备减库存 now:{} consume:{} threadID:{}", now,
                    now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(now);
        }

        // 实现减库存
        String key = QUANTITY_KEY_FOR_REDIS + productId;
        Long newQuantity = opsForQuantity.increment(key, -num);
        if (newQuantity < 0) {
            opsForQuantity.increment(key, num); // 并发减库存导致此次库存量不足，回加
            log.info("【生成订单】 该商品库存不足 productId:{}", productId);
            throw new ServiceException("223", "该商品库存不足");
        }
        if (log.isDebugEnabled()) {
            now = System.currentTimeMillis();
            log.debug("【TimeUtil】减库存耗时 now:{} consume:{} threadID:{}", now,
                    now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(now);
        }

        // 发送订单消息
        log.info("【生成订单】 提交订单给消息中间件 ");
        OrderCreateMsg orderCreateMsg = new OrderCreateMsg();
        orderCreateMsg.setProductId(productId);
        orderCreateMsg.setUserId(userId);
        orderCreateMsg.setNum(num);
        orderCreateMsg.setFee(productDTO.getPrice() * num);
        boolean sendMsgToCreateOrder = rocketMQ.sendMsgToCreateOrder(orderCreateMsg);

        if (log.isDebugEnabled()) {
            now = System.currentTimeMillis();
            log.debug("【TimeUtil】 发送订单消息耗时 now:{} consume:{} threadID:{}", now,
                    now - TimeUtil.threadLocalTime.get(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(now);
        }
        if (!sendMsgToCreateOrder) {
            log.warn("【发送订单消息】 发送订单消息中间件失败 ");
            opsForQuantity.increment(key, num);// 实现加回库存
            log.warn("【实现回加库存】 quantity:{}", quantity);
            throw new ServiceException("发送订单消息中间件失败");
        }
        return true;
    }


    /**
     * 把redis中缓存的商品库存量写入数据库
     */
    @Transactional
    public synchronized void quantityIntoDb(){
        log.info("把redis中缓存的商品库存量写入数据库");
        repertoryDAO.findAll().forEach(repertory -> {
            String key = QUANTITY_KEY_FOR_REDIS + repertory.getProductId();
            Integer quantity = opsForQuantity.get(key);
            if (quantity != null && quantity >= 0 &&
                    quantity.intValue() != repertory.getQuantity()) {
                repertory.setQuantity(quantity);
                repertoryDAO.save(repertory);
            }
        });
    }
    /**
     * 把数据库中缓存的商品库存量写入redis
     */
    public synchronized void quantityIntoRedis(){
        log.info("把数据库中缓存的商品库存量写入redis");
        final Map<String, Integer> quantityMap = new HashMap<>();
        repertoryDAO.findAll().forEach(repertory -> {
            quantityMap.put(QUANTITY_KEY_FOR_REDIS + repertory.getProductId(), repertory.getQuantity());
        });
        // 多字段更新
        opsForQuantity.multiSet(quantityMap);
    }

    private ProductDTO getProductDTO(Product product) {
        if (product == null) {
            return null;
        }
        ProductDTO productDTO = new ProductDTO();
        ObjectConvertUtil.objectCopy(productDTO, product);
        productDTO.setQuantity(findQuantity(product.getId()));
        return productDTO;
    }

}
