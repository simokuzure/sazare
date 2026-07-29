package com.jt.learning.service.impl;

import java.net.URI;
import java.util.Map;

public interface AiProviderHttpClient {

    AiProviderHttpResponse postJson(URI uri, Map<String, String> headers, String body);
}
