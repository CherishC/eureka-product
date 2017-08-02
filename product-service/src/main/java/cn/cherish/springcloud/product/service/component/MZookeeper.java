package cn.cherish.springcloud.product.service.component;


import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author Cherish
 * @version 1.0
 * @date 2017/6/18 12:02
 */
@Slf4j
@Component
public class MZookeeper {

    @Value("${zookeeper.connectString}")
    private String connectString = "119.23.30.142:2181,39.108.67.111:2181,39.108.151.46:2181";
    private CuratorFramework client;

    public MZookeeper(){
        init();
    }

    private void init(){
        String namespace = "springcloud";// 本项目的命名空间
        client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(30000)
                .connectionTimeoutMs(30000)
                .canBeReadOnly(false)
                .retryPolicy(new ExponentialBackoffRetry(100, Integer.MAX_VALUE))
                .namespace(namespace)
                .defaultData(null)
                .build();
        client.start();
    }

    public CuratorFramework client(){
        return client;
    }

    public InterProcessMutex worldLockToCreateOrder(Long productId) {
        return new InterProcessMutex(client(), "/lock/order/_productId_" + productId);
    }

//    public static void main(String[] args) throws Exception {
//        MZookeeper zookeeper = new MZookeeper();
//        InterProcessMutex lock = zookeeper.worldLockToCreateOrder();
//        try {
//            if (lock.acquire(10 * 1000, TimeUnit.SECONDS)) {
//                System.out.println(Thread.currentThread().getName() + " hold lock");
//                Thread.sleep(5000L);
//                System.out.println(Thread.currentThread().getName() + " release lock");
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                lock.release();
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//        CloseableUtils.closeQuietly(zookeeper.client());
//    }
}
