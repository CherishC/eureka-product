package cn.cherish.springcloud.product.web.controller;

import cn.cherish.springcloud.product.service.common.TimeUtil;
import cn.cherish.springcloud.product.service.impl.ProductService;
import cn.cherish.springcloud.service.dto.ProductDTO;
import cn.cherish.springcloud.service.req.ProductReq;
import me.cherish.web.MResponse;
import me.cherish.web.controller.ABaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/product")
public class ProductController extends ABaseController {

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public MResponse show(@PathVariable Long id) {
        ProductDTO productDTO = productService.findOne(id);
        productDTO.setQuantity(productService.findQuantity(productDTO.getId()));
        return buildResponse(Boolean.TRUE, "", productDTO);
    }

    @GetMapping("/list")
    public MResponse getList() {
        return buildResponse(Boolean.TRUE, "", productService.findAllDTO());
    }

    @PostMapping("/save")
    public MResponse save(@RequestBody ProductReq productReq) {
        log.info("【保存新商品】 {}", productReq);
        ProductDTO save = productService.save(productReq);
        log.info("【保存新商品】 ID {}", save.getId());
        return buildResponse(Boolean.TRUE, "保存成功", null);
    }

    @DeleteMapping(value="/{id}")
    public MResponse delete(@PathVariable Long id){
        productService.delete(id);
        return buildResponse(Boolean.TRUE, "删除成功", null);
    }

    /**
     * 刷新商品库存在redis中的缓存
     */
    @GetMapping("/quantity/flush")
    public void flushQuantity(){
        productService.quantityIntoDb();
        productService.quantityIntoRedis();
    }

    @GetMapping("/quantity/{id}")
    public MResponse findQuantity(@PathVariable Long id) {
        return buildResponse(Boolean.TRUE, "商品的库存量", productService.findQuantity(id));
    }

    @PostMapping("/order")
    public MResponse order(Long userId, Long productId, Integer num) {
        log.info("【商品下单】 userId:{} productId:{} num:{} ", userId, productId, num);
        ProductDTO productDTO = productService.findOne(productId);

        {
            log.debug("【TimeUtil】 now: {} threadID: {}", System.currentTimeMillis(), Thread.currentThread().getId());
            TimeUtil.threadLocalTime.set(System.currentTimeMillis());
        }

        boolean result = productService.orderProduct2(productDTO, userId, num);
        log.info("【商品下单】 是否成功：{}", result);
        if (result) {
            return buildResponse(Boolean.TRUE, "成功下单", null);
        }
        return buildResponse(Boolean.FALSE, "下单失败", null);
    }


}
