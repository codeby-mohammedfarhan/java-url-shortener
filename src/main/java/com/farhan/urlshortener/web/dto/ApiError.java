package com.farhan.urlshortener.web.dto;

public record ApiError(int status, String error, String message) {
}
