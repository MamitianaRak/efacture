package com.mamitiana.efacture.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    static final String ROLES_CLAIM = "roles";
    static final String NAME_CLAIM = "name";
    static final String ISSUER = "efacture";

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, @Value("${app.jwt.ttl:8h}") Duration ttl, Clock clock) {
        this.encoder = encoder;
        this.ttl = ttl;
        this.clock = clock;
    }

    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(NAME_CLAIM, user.getDisplayName())
                .claim(ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
