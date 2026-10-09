package com.campusflow.hardware;

import com.campusflow.common.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static com.campusflow.hardware.HardwarePayload.*;

@Component
@ConditionalOnProperty(name="campusflow.checkin.mqtt.enabled",havingValue="true")
public class HardwareMqttReceiver {
    private static final Logger log=LoggerFactory.getLogger(HardwareMqttReceiver.class);
    private final HardwareService service;
    private final ObjectMapper json;
    private final MqttClient client;
    private final MqttConnectOptions options=new MqttConnectOptions();
    private final ScheduledExecutorService reconnect=Executors.newSingleThreadScheduledExecutor(work->{var thread=new Thread(work,"checkin-mqtt-reconnect");thread.setDaemon(true);return thread;});
    private volatile boolean subscribed;
    public HardwareMqttReceiver(HardwareService service,ObjectMapper json,
        @Value("${campusflow.checkin.mqtt.uri:tcp://localhost:1883}") String uri,
        @Value("${campusflow.checkin.mqtt.client-id:campusflow-checkin-receiver}") String clientId,
        @Value("${campusflow.checkin.mqtt.username:}") String username,
        @Value("${campusflow.checkin.mqtt.password:}") String password) throws MqttException {
        this.service=service;this.json=json;
        if(clientId.isBlank()) throw new IllegalArgumentException("MQTT接收端必须配置固定client-id");
        client=new MqttClient(uri,clientId,new MqttDefaultFilePersistence("data/checkin-mqtt"));
        client.setTimeToWait(3000);client.setManualAcks(true);
        // 固定 client-id 和持久会话让 broker 保留离线期间的 QoS1 事件。
        options.setCleanSession(false);options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setConnectionTimeout(3);options.setKeepAliveInterval(30);
        if(!username.isBlank()) options.setUserName(username);
        if(!password.isEmpty()) options.setPassword(password.toCharArray());
        client.setCallback(new MqttCallback() {
            public void connectionLost(Throwable failure) { subscribed=false;log.warn("签到MQTT连接断开，将自动重连"); }
            public void deliveryComplete(IMqttDeliveryToken token) {}
            public void messageArrived(String topic,MqttMessage message) throws Exception {
                String payload=new String(message.getPayload(),StandardCharsets.UTF_8);
                try { receive(topic,payload,message.isRetained()); }
                catch(BusinessException invalid) { service.reject(topic,payload,invalid.code);log.warn("设备消息已隔离，原因：{}",invalid.code); }
                catch(JsonProcessingException invalid) { service.reject(topic,payload,"INVALID_JSON");log.warn("设备消息已隔离，原因：INVALID_JSON"); }
                // 接收事务返回后才确认。数据库故障继续抛出，断连后由 broker 重投。
                if(message.getQos()>0) client.messageArrivedComplete(message.getId(),message.getQos());
            }
        });
    }
    @PostConstruct void start() {
        // 专用线程负责初次连接和重连，broker 未启动不会阻止网页和模拟调度。
        reconnect.scheduleWithFixedDelay(()->{
            try {
                if(!client.isConnected()) { subscribed=false;client.connect(options); }
                if(!subscribed) { client.subscribe(new String[]{"checkin/+/events","checkin/+/telemetry"},new int[]{1,1});subscribed=true;log.info("签到MQTT订阅已就绪"); }
            } catch(MqttException failure) { log.warn("签到MQTT连接或订阅失败（{}），5秒后重试",failure.getReasonCode()); }
        },0,5,TimeUnit.SECONDS);
    }
    void receive(String topic,String payload,boolean retained) throws JsonProcessingException {
        String[] parts=topic.split("/",-1);
        if(payload.getBytes(StandardCharsets.UTF_8).length>8192 || retained || parts.length!=3 || !"checkin".equals(parts[0]))
            throw new BusinessException(400,"INVALID_MQTT_MESSAGE","消息大小、保留标记或主题不符合协议");
        var reader=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        if("events".equals(parts[2])) {
            Event input=reader.forType(Event.class).readValue(payload);
            if(input==null || !parts[1].equals(input.deviceId())) throw new BusinessException(400,"DEVICE_TOPIC_MISMATCH","主题设备与消息设备不一致");
            service.event(input);
        } else if("telemetry".equals(parts[2])) {
            Telemetry input=reader.forType(Telemetry.class).readValue(payload);
            if(input==null || !parts[1].equals(input.deviceId())) throw new BusinessException(400,"DEVICE_TOPIC_MISMATCH","主题设备与消息设备不一致");
            service.telemetry(input);
        } else throw new BusinessException(400,"INVALID_TOPIC","未知设备消息主题");
    }
    @PreDestroy void stop() throws MqttException {
        reconnect.shutdownNow();
        if(client.isConnected()) client.disconnectForcibly(1000,1000);
        client.close(true);
    }
}
