package com.farhan.urlshortener.repository;

import com.farhan.urlshortener.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<Url, Long> {

    Optional<Url> findByShortCode(String shortCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select url from Url url where url.shortCode = :shortCode")
    Optional<Url> findByShortCodeForUpdate(@Param("shortCode") String shortCode);

    @Modifying
    @Query("delete from Url url where url.expiresAt <= :now "
            + "or (url.expiresAt is null and url.createdAt <= :legacyCutoff)")
    long deleteExpiredUrls(@Param("now") java.time.LocalDateTime now,
                           @Param("legacyCutoff") java.time.LocalDateTime legacyCutoff);
}
