package com.example.account.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * Configuration for MessageSource to load messages from properties file.
 */
@Configuration
public class MessageSourceConfig {

    @Bean
    @Primary
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setCacheSeconds(3600); // Cache for 1 hour
        messageSource.setUseCodeAsDefaultMessage(false);
        messageSource.setAlwaysUseMessageFormat(true);
        // Ensure MessageFormat is used for placeholder replacement
        messageSource.setFallbackToSystemLocale(true);
        return messageSource;
    }
}

