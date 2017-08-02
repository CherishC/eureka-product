package cn.cherish.springcloud.product.service.component.rocketmq;

import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.client.producer.DefaultMQProducer;
import com.alibaba.rocketmq.client.producer.SendResult;
import com.alibaba.rocketmq.common.message.Message;
import com.alibaba.rocketmq.shade.com.alibaba.fastjson.JSON;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author Cherish
 * @version 1.0
 * @date 2017/6/20 11:54
 */
@Slf4j
@Component
public class RocketMQ {

    private static final String NamesrvAddr = "39.108.151.46:9876";
    private static final String PRODUCER_GROUP = "order_group";
    private static final String TOPIC = "springcloud_order";
    private static final String TAGS = "TAG_CREATE_ORDER";

    public RocketMQ() {
        initProducer();
    }

    private DefaultMQProducer producer;
    private void initProducer(){
        //Instantiate with a producer PRODUCER_GROUP name.
        producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NamesrvAddr);
        producer.setInstanceName("order_producer1");
        producer.setVipChannelEnabled(false);
        //Launch the instance.
        try {
            producer.start();
            log.info("【RocketMQ启动】 Producer Started");
        } catch (MQClientException e) {
            log.error("【RocketMQ启动】{}", Throwables.getStackTraceAsString(e));
        }
    }

    public boolean sendMsgToCreateOrder(OrderCreateMsg orderCreateMsg) {
        String body = JSON.toJSONString(orderCreateMsg);
        Message msg = new Message(
                TOPIC,
                TAGS,
                body.getBytes()
        );
        msg.setKeys(UUID.randomUUID().toString());
        //发送消息，只要不抛异常就是成功
        try {
            log.info("【发送生成订单消息】 消息内容：{}  {}", body, msg);
            SendResult sendResult = producer.send(msg);
            log.info("【发送生成订单消息】 发送结果： {}", sendResult);
        } catch (Exception e){
            log.error("【发送生成订单消息】 {}", Throwables.getStackTraceAsString(e));
            return false;
        }

        return true;
    }


}
