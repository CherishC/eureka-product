package cn.cherish.springcloud.product.service.component.rocketmq;

import lombok.Data;

/**
 * @author Cherish
 * @version 1.0
 * @date 2017/6/20 14:57
 */
@Data
public class OrderCreateMsg {

    private Long userId;
    private Long productId;
    private Integer num;
    private Integer fee;

}
