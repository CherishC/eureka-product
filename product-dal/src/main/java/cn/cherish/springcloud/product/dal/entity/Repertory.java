package cn.cherish.springcloud.product.dal.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "t_product_repertory")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Repertory implements java.io.Serializable {

    private static final long serialVersionUID = -51595887287688114L;

    @Id
    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;
    /**
     * 库存
     */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

}
