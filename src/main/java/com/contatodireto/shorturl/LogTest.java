package com.contatodireto.shorturl;

import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


@Component
public class LogTest implements CommandLineRunner {

    final StringRedisTemplate stringRedisTemplate;

    public LogTest (StringRedisTemplate stringRedisTemplate, RedisTemplate<Object, Object> redisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public void run(String... args) throws Exception {
        stringRedisTemplate.opsForValue().set("chave", "valor");
        System.out.println(stringRedisTemplate.opsForValue().get("chave"));
    }
}
