package cn.cherish.springcloud.service.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductReq implements java.io.Serializable {

    private static final long serialVersionUID = 5553283847616393048L;

    private Long id;
    /**
     * 名称
     */
    private String name;
    /**
     * 单价
     */
    private Integer price;
    /**
     * 库存
     */
    private Integer quantity;

}
