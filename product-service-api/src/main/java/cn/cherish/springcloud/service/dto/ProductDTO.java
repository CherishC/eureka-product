package cn.cherish.springcloud.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO implements java.io.Serializable {

    private static final long serialVersionUID = -5324510060282682856L;

    private Long id;
    /**
     * 名称
     */
    private String name;
    /**
     * 单价
     */
    private Integer price;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    private Date createdTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    private Date modifiedTime;
    /**
     * 库存
     */
    private Integer quantity;

}
