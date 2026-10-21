package com.contatodireto.shorturl.service;

import com.contatodireto.shorturl.dto.RankingDTO;
import com.contatodireto.shorturl.entity.ShortUrl;
import com.contatodireto.shorturl.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Service
@RequiredArgsConstructor
public class UrlService {

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final String ALFABETO = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final ShortUrlRepository shortRepo;

    @Value("${shorturl.ttl-seconds}")
    private long ttl;

    public String shorten(String url ) {
        String code;

        do {
            code = generateCode();
        } while (shortRepo.existsByCode(code));

        ShortUrl shortUrl = new ShortUrl();
        shortUrl.setOriginalUrl(url);
        shortUrl.setCode(code);

        shortRepo.save(shortUrl);
        redisTemplate.opsForValue().set("url:" + code, url, Duration.ofSeconds(ttl));

        return code;
    }

    public Optional<String> find(String code) {
        String value = redisTemplate.opsForValue().get("url:" + code);
        if (value != null) {
            return Optional.of(value);
        }
        Optional<ShortUrl> shortUrl = shortRepo.findByCode(code);
        if (shortUrl.isPresent()) {
            String originalUrl = shortUrl.get().getOriginalUrl();
            redisTemplate.opsForValue().set("url:" + code, originalUrl, Duration.ofSeconds(ttl));
            return Optional.of(originalUrl);
        }
        return Optional.empty();

    }

    private String generateCode() {
        int indice = 0 ;
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 7 ; i++) {
            indice = secureRandom.nextInt(ALFABETO.length());
            code.append(ALFABETO.charAt(indice));
        }
        return code.toString();
    }

    public void registerClick(String code) {
        redisTemplate.opsForValue().increment("clicks:" +  code);
        Long expiration = redisTemplate.getExpire("url:" + code);
        if (expiration != null && expiration > 0) {
            redisTemplate.expire("clicks:" + code, Duration.ofSeconds(expiration));
        }

        redisTemplate.opsForZSet().incrementScore("ranking", code, 1);

    }

    public Long getClicks (String code) {
       return Optional.ofNullable(redisTemplate.opsForValue().get("clicks:" + code)).map(Long::parseLong).orElse(0L);
    }

    public List<RankingDTO> getTopLinks(int n) {

        Set<ZSetOperations.TypedTuple<String>> tupleSet = redisTemplate.opsForZSet().reverseRangeWithScores("ranking", 0, n-1);

        if (tupleSet == null) {
            return List.of();
        } else {

               return tupleSet
                        .stream()
                        .filter(tuple -> Boolean.TRUE.equals(redisTemplate.hasKey("url:" + tuple.getValue())))
                        .map(typedDuble ->
                            new RankingDTO(typedDuble.getValue(), typedDuble.getScore().longValue())
                        )
                        .toList();
        }
    }
}
