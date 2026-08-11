package com.jt.learning.service.ai;

import java.util.List;

public interface AiEmbeddingClient {

    List<Float> embed(String content);

    String modelName();
}
