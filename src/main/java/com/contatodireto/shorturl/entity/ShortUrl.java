package com.contatodireto.shorturl.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(unique = true, nullable = false)
    String code;

    @Column(nullable = false)
    String originalUrl;

    LocalDateTime createdAt;

    @PrePersist
    public void created() {
        createdAt = LocalDateTime.now();
    }
}
