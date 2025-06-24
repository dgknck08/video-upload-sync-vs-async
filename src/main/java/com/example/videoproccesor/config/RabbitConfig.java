package com.example.videoproccesor.config;



import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@EnableRabbit
public class RabbitConfig {
    
    // Queue names
    public static final String THUMBNAIL_QUEUE = "thumbnail.queue";
    public static final String TRANSCODING_QUEUE = "transcoding.queue";
    public static final String METADATA_QUEUE = "metadata.queue";
    
    // Exchange
    public static final String VIDEO_EXCHANGE = "video.exchange";
    
    // Routing keys
    public static final String THUMBNAIL_ROUTING_KEY = "video.thumbnail";
    public static final String TRANSCODING_ROUTING_KEY = "video.transcoding";
    public static final String METADATA_ROUTING_KEY = "video.metadata";
    
    @Bean
    public TopicExchange videoExchange() {
        return new TopicExchange(VIDEO_EXCHANGE);
    }
    
    @Bean
    public Queue thumbnailQueue() {
        return QueueBuilder.durable(THUMBNAIL_QUEUE).build();
    }
    
    @Bean
    public Queue transcodingQueue() {
        return QueueBuilder.durable(TRANSCODING_QUEUE).build();
    }
    
    @Bean
    public Queue metadataQueue() {
        return QueueBuilder.durable(METADATA_QUEUE).build();
    }
    
    @Bean
    public Binding thumbnailBinding() {
        return BindingBuilder
            .bind(thumbnailQueue())
            .to(videoExchange())
            .with(THUMBNAIL_ROUTING_KEY);
    }
    
    @Bean
    public Binding transcodingBinding() {
        return BindingBuilder
            .bind(transcodingQueue())
            .to(videoExchange())
            .with(TRANSCODING_ROUTING_KEY);
    }
    
    @Bean
    public Binding metadataBinding() {
        return BindingBuilder
            .bind(metadataQueue())
            .to(videoExchange())
            .with(METADATA_ROUTING_KEY);
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}