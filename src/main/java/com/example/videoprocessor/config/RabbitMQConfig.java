package com.example.videoprocessor.config; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String VIDEO_PROCESSING_QUEUE = "video.processing.queue";
    public static final String VIDEO_PROCESSING_DLQ = "video.processing.dlq";
    public static final String VIDEO_PROCESSING_EXCHANGE = "video.processing.exchange";
    public static final String VIDEO_PROCESSING_ROUTING_KEY = "video.processing";
    public static final String VIDEO_PROCESSING_FAILED_ROUTING_KEY = "video.processing.failed";

    private final ConnectionFactory connectionFactory;

    public RabbitMQConfig(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin() {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(5);
        
        factory.setPrefetchCount(1);
        
        factory.setDefaultRequeueRejected(false);
        
        logger.info("RabbitListener ContainerFactory configured with manual ack");
        return factory;
    }

    @Bean
    public Queue videoProcessingQueue() {
        return QueueBuilder.durable(VIDEO_PROCESSING_QUEUE)
                .withArgument("x-dead-letter-exchange", VIDEO_PROCESSING_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", VIDEO_PROCESSING_FAILED_ROUTING_KEY)
                .withArgument("x-message-ttl", 3600000) // 1 hour TTL
                .build();
    }

    @Bean
    public Queue videoProcessingDLQ() {
        return QueueBuilder.durable(VIDEO_PROCESSING_DLQ).build();
    }

    @Bean
    public DirectExchange videoProcessingExchange() {
        return new DirectExchange(VIDEO_PROCESSING_EXCHANGE, true, false);
    }

    @Bean
    public Binding videoProcessingBinding() {
        return BindingBuilder.bind(videoProcessingQueue())
                .to(videoProcessingExchange())
                .with(VIDEO_PROCESSING_ROUTING_KEY);
    }

    @Bean
    public Binding videoProcessingDLQBinding() {
        return BindingBuilder.bind(videoProcessingDLQ())
                .to(videoProcessingExchange())
                .with(VIDEO_PROCESSING_FAILED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate() {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setMandatory(true);
        return template;
    }

    @Bean
    public ApplicationRunner initializeQueues(RabbitAdmin rabbitAdmin) {
        return args -> {
            try {
                logger.info("Initializing RabbitMQ queues and exchanges...");
                
                rabbitAdmin.declareExchange(videoProcessingExchange());
                logger.info("Declared exchange: {}", VIDEO_PROCESSING_EXCHANGE);
                
                rabbitAdmin.declareQueue(videoProcessingQueue());
                logger.info("Declared queue: {}", VIDEO_PROCESSING_QUEUE);
                
                rabbitAdmin.declareQueue(videoProcessingDLQ());
                logger.info("Declared DLQ: {}", VIDEO_PROCESSING_DLQ);
                
                rabbitAdmin.declareBinding(videoProcessingBinding());
                rabbitAdmin.declareBinding(videoProcessingDLQBinding());
                logger.info("Declared bindings for video processing");
                
                logger.info("RabbitMQ initialization completed successfully!");
                
            } catch (Exception e) {
                logger.error("Failed to initialize RabbitMQ queues and exchanges", e);
                throw e;
            }
        };
    }
}