package com.hsmty.cmr.operator;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorClientTest {

    @Test
    void connectionFailureIsEngineSide() {
        OperatorException result = OperatorClient.classify(new ResourceAccessException("timeout"));
        assertThat(result.getSide()).isEqualTo(OperatorException.Side.ENGINE);
        assertThat(result.getMessage()).contains("不可达或超时");
    }

    @Test
    void clientErrorIsDataSide() {
        HttpClientErrorException error = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "{\"code\":1,\"msg\":\"bad input\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        OperatorException result = OperatorClient.classify(error);
        assertThat(result.getSide()).isEqualTo(OperatorException.Side.DATA);
    }

    @Test
    void serverErrorIsEngineSide() {
        HttpServerErrorException error = HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Server Error", HttpHeaders.EMPTY,
                "engine failure".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        OperatorException result = OperatorClient.classify(error);
        assertThat(result.getSide()).isEqualTo(OperatorException.Side.ENGINE);
    }
}