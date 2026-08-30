package com.sazare.service.ai.client;

import com.sazare.service.ai.AiEmbeddingClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class MockAiEmbeddingClient implements AiEmbeddingClient {

    private static final int DIMENSION = 768;
    private static final String MODEL_NAME = "mock-embedding-v1";

    @Override
    public List<Float> embed(String content) {
        byte[] digest = digest(content);
        List<Float> embedding = new ArrayList<>(DIMENSION);
        for (int i = 0; i < DIMENSION; i++) {
            int value = digest[i % digest.length] & 0xff;
            embedding.add((value - 127.5f) / 127.5f);
        }
        return embedding;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    private byte[] digest(String content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
