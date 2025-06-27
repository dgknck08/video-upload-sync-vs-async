package com.example.videoprocessor;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;


@SpringBootApplication
@EnableRabbit 
@EnableJpaRepositories
public class VideoproccesorApplication {
	private static final Logger logger = LoggerFactory.getLogger(VideoproccesorApplication.class);
	
	public static void main(String[] args) {
		SpringApplication.run(VideoproccesorApplication.class, args);
	}
	@EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        logger.info("=== APPLICATION CONTEXT REFRESHED ===");
        logger.info("RabbitMQ listeners should be starting now...");
    }

}
