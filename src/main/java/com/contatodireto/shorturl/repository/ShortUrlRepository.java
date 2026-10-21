package com.contatodireto.shorturl.repository;

import com.contatodireto.shorturl.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.parser.Entity;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends JpaRepository {

     boolean existsByCode(String code);
     Optional<ShortUrl> findByCode(String code);

}
