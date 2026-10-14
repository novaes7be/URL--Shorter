package com.contatodireto.shorturl.controller;

import com.contatodireto.shorturl.dto.RankingDTO;
import com.contatodireto.shorturl.dto.UrlDTO;
import com.contatodireto.shorturl.service.UrlService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class URLController {

    private final UrlService urlService;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/shorten")
    public String shortenPost(@RequestBody @Valid UrlDTO dto) {
        return urlService.shorten(dto.url());
    }

    @GetMapping("/{code}")
    public ResponseEntity<Void> codeGetter(@PathVariable String code) {
        return urlService.find(code).map(url -> {
            urlService.registerClick(code);
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound()
                        .build());
    }

    @GetMapping("/stats/{code}")
    public Long clicks(@PathVariable String code) {
        return urlService.getClicks(code);
    }

    @GetMapping("/ranking")
    public List<RankingDTO> rankingList(@RequestParam(defaultValue = "10") @Min(1) int limit) {
        return urlService.getTopLinks(limit);
    }
}
