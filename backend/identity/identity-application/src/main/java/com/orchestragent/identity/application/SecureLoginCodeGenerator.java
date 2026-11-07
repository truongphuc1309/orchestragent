package com.orchestragent.identity.application;

import com.orchestragent.identity.application.port.LoginCodeGenerator;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
class SecureLoginCodeGenerator implements LoginCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        return String.format("%06d", random.nextInt(1_000_000));
    }
}
