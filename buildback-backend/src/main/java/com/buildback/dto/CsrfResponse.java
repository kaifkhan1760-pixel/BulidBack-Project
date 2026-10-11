package com.buildback.dto;

/** Tells React which header to send and the token value to put in it. */
public record CsrfResponse(String headerName, String token) {
}
