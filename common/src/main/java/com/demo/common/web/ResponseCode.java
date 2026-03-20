package com.demo.common.web;

/**
 * Compile-time constant HTTP response codes for use in OpenAPI {@code @ApiResponse} annotations.
 *
 * <p><b>Why not {@code HttpStatus.OK.value()}?</b><br>
 * Java annotation attributes must be <em>compile-time constant expressions</em> (JLS §15.29).
 * A method call — even on an enum — is never a constant expression, so the following does
 * <strong>not</strong> compile:
 * <pre>
 *   // COMPILE ERROR: element value must be a constant expression
 *   {@literal @}ApiResponse(responseCode = String.valueOf(HttpStatus.OK.value()))
 * </pre>
 * {@code SpringDoc} defines {@code @ApiResponse.responseCode} as {@code String}, not as
 * {@code HttpStatus}, so passing an enum constant directly is also not possible.
 *
 * <p><b>Why this class?</b><br>
 * {@code public static final String} fields initialised with string literals <em>are</em>
 * compile-time constants and satisfy the annotation attribute requirement. This class
 * provides named constants (e.g. {@link #OK}, {@link #CREATED}) that avoid magic-number
 * strings scattered across controllers while remaining usable inside annotations.
 */
public final class ResponseCode {

    public static final String OK = "200";
    public static final String CREATED = "201";
    public static final String NO_CONTENT = "204";
    public static final String BAD_REQUEST = "400";
    public static final String NOT_FOUND = "404";
    public static final String CONFLICT = "409";
    public static final String INTERNAL_SERVER_ERROR = "500";

    private ResponseCode() {}
}
