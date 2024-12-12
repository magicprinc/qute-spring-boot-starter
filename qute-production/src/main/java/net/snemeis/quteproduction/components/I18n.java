package net.snemeis.quteproduction.components;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class I18n {

    @Autowired
    MessageSource messageSource;

    public String t(String key, Object... args) {
        return messageSource.getMessage(key, args, "", Locale.ENGLISH);
    }
}
