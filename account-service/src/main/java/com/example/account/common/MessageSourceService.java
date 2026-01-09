package com.example.account.common;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service to access messages from properties file.
 * Provides centralized message management for the application.
 */
@Service
public class MessageSourceService {

    private final MessageSource messageSource;

    public MessageSourceService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Get message by key.
     *
     * @param key the message key
     * @return the resolved message
     */
    public String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    /**
     * Get message by key with arguments.
     *
     * @param key the message key
     * @param args the arguments to substitute
     * @return the resolved message
     */
    public String getMessage(String key, Object... args) {
        // Varargs automatically convert to Object[] when passed to Spring MessageSource
        // Spring MessageSource.getMessage(String code, Object[] args, Locale locale)
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Get message by key with default message.
     *
     * @param key the message key
     * @param defaultMessage the default message if key not found
     * @return the resolved message or default message
     */
    public String getMessage(String key, String defaultMessage) {
        return messageSource.getMessage(key, null, defaultMessage, LocaleContextHolder.getLocale());
    }

    /**
     * Get message by key with arguments and default message.
     *
     * @param key the message key
     * @param args the arguments to substitute
     * @param defaultMessage the default message if key not found
     * @return the resolved message or default message
     */
    public String getMessage(String key, Object[] args, String defaultMessage) {
        return messageSource.getMessage(key, args, defaultMessage, LocaleContextHolder.getLocale());
    }
}

