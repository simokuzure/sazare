package com.jt.learning.service;

import java.util.List;

public interface AiEmbeddingClient {

    List<Float> embed(String content);

    String modelName();
}
