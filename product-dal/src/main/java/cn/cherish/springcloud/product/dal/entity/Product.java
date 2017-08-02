package cn.cherish.springcloud.product.dal.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "t_product")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product implements java.io.Serializable {

    private static final long serialVersionUID = -3192194652675748951L;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "id", unique = true, nullable = false)
    private Long id;
    /**
     * 名称
     */
    @Column(name = "name", nullable = false)
    private String name;
    /**
     * 单价
     */
    @Column(name = "price", nullable = false)
    private Integer price;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_time", nullable = false, length = 19)
    private Date createdTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "modified_time", length = 19)
    private Date modifiedTime;

    /**
     * 库存
     */
    @Transient
    private Integer quantity;

}
