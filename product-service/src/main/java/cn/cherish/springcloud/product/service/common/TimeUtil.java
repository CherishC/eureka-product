package cn.cherish.springcloud.product.service.common;

/**
 * @author Cherish
 * @version 1.0
 * @date 2017/6/21 8:13
 */
public class TimeUtil {

    public static final ThreadLocal<Long> threadLocalTime = new ThreadLocal<>();

}
