package com.linenexus.monitor.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import com.linenexus.monitor.ingest.TelemetryIngestService;

@Configuration
@ConditionalOnProperty(prefix = "linenexus.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttIngestConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttIngestConfig.class);

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttProperties props) {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {props.getBrokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            options.setUserName(props.getUsername());
            options.setPassword(props.getPassword() == null ? new char[0] : props.getPassword().toCharArray());
        }
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInbound(MqttProperties props, MqttPahoClientFactory factory) {
        String clientId = props.getClientId() + "-" + System.currentTimeMillis() % 100000;
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, factory, props.getTopic());
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(0);
        adapter.setOutputChannel(mqttInputChannel());
        log.info("MQTT subscribe {} @ {}", props.getTopic(), props.getBrokerUrl());
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttInboundHandler(TelemetryIngestService ingest) {
        return message -> {
            Object payload = message.getPayload();
            String text = payload instanceof byte[] bytes
                    ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    : String.valueOf(payload);
            ingest.ingestJson(text);
        };
    }
}
