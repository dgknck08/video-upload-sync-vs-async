package com.example.videoprocesor.config;

import org.springframework.amqp.core.*;


import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
@Configuration
@EnableRabbit
public class RabbitMQConfig {

    // Queue isimleri
    public static final String VIDEO_PROCESSING_QUEUE = "video.processing.queue";
    public static final String VIDEO_PROCESSING_DLQ = "video.processing.dlq";
    public static final String VIDEO_PROCESSING_EXCHANGE = "video.processing.exchange";
    public static final String VIDEO_PROCESSING_ROUTING_KEY = "video.processing";
    

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
    // Ana processing queue
    @Bean
    public Queue videoProcessingQueue() {
        return QueueBuilder.durable(VIDEO_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", VIDEO_PROCESSING_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "video.processing.failed")
                .withArgument("x-message-ttl", 3600000) // 1 saat TTL
                .build();
    }

    // Dead Letter Queue (başarısız mesajlar için)
    @Bean
    public Queue videoProcessingDLQ() {
        return QueueBuilder.durable(VIDEO_PROCESSING_DLQ).build();
    }

    // Exchange
    @Bean
    public DirectExchange videoProcessingExchange() {
        return new DirectExchange(VIDEO_PROCESSING_EXCHANGE);
    }

    // Bindings
    @Bean
    public Binding videoProcessingBinding() {
        return BindingBuilder
                .bind(videoProcessingQueue())
                .to(videoProcessingExchange())
                .with(VIDEO_PROCESSING_ROUTING_KEY);
    }

    @Bean
    public Binding videoProcessingDLQBinding() {
        return BindingBuilder
                .bind(videoProcessingDLQ())
                .to(videoProcessingExchange())
                .with("video.processing.failed");
    }

    // Message converter (JSON)
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // RabbitTemplate
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // Listener container factory
    @Bean
    public RabbitListenerContainerFactory<?> rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(1); 
        return factory;
    }
}
