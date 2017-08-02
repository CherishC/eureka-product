package cn.cherish.springcloud.product.dal.repository;


import cn.cherish.springcloud.product.dal.entity.Product;
import me.cherish.dal.repository.IBaseDAO;

public interface ProductDAO extends IBaseDAO<Product, Long> {

    Product findByName(String name);

}
