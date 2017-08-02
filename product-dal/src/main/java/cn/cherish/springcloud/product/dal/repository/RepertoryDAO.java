package cn.cherish.springcloud.product.dal.repository;


import cn.cherish.springcloud.product.dal.entity.Repertory;
import me.cherish.dal.repository.IBaseDAO;

public interface RepertoryDAO extends IBaseDAO<Repertory, Long> {

    Repertory findByProductId(Long productId);

}
