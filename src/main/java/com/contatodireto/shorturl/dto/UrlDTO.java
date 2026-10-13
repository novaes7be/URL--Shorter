package com.contatodireto.shorturl.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record UrlDTO(@NotBlank String url) { }
