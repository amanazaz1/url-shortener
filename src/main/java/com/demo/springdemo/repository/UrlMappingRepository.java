package com.demo.springdemo.repository;

import com.demo.springdemo.entity.UrlMapping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByShortCode(String shortCode);

    /** Atomic in the database: no lost updates under concurrent clicks (unlike read-modify-write). */
    @Transactional
    @Modifying
    @Query("update UrlMapping u set u.clickCount = u.clickCount + 1 where u.shortCode = :shortCode")
    int incrementClickCount(@Param("shortCode") String shortCode);
}
