package com.larv.pharmacy.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    List<RevokedToken> findByExpiresAtBefore(Instant instant);

    void deleteByExpiresAtBefore(Instant instant);
}
