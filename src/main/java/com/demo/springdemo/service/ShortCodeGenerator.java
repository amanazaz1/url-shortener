package com.demo.springdemo.service;

import com.demo.springdemo.config.AppProperties;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Random (unguessable) base62 codes. Uniqueness is enforced by the DB constraint, not by this class. */
@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public ShortCodeGenerator(AppProperties props) {
        this.length = props.codeLength();
    }

    public String next() {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
