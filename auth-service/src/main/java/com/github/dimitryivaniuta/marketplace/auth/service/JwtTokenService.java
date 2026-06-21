package com.github.dimitryivaniuta.marketplace.auth.service;

import com.github.dimitryivaniuta.marketplace.auth.api.AuthResponses;
import com.github.dimitryivaniuta.marketplace.auth.configuration.JwtKeyProperties;
import com.github.dimitryivaniuta.marketplace.auth.domain.UserAccount;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived RS256 access tokens. */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    /** JWT encoder. */
    private final JwtEncoder jwtEncoder;
    /** Token policy. */
    private final JwtKeyProperties properties;

    /**
     * Issues a token for a verified account.
     *
     * @param user verified user
     * @return bearer-token response
     */
    public AuthResponses.Token issue(UserAccount user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(user.id().toString())
                .audience(java.util.List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("email", user.email())
                .claim("roles", user.roles())
                .id(java.util.UUID.randomUUID().toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponses.Token(token, "Bearer", expiresAt, user.roles());
    }
}
