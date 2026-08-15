package com.jt.learning.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String provider = "google";
    private Duration requestTimeout = Duration.ofSeconds(180);
    private Providers providers = new Providers();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers;
    }

    public static class Providers {

        private Google google = new Google();

        public Google getGoogle() {
            return google;
        }

        public void setGoogle(Google google) {
            this.google = google;
        }
    }

    public static class Google {

        private String model = "gemini-3.6-flash";
        private String embeddingModel = "gemini-embedding-001";
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";
        private double articleTemperature = 1.1d;
        private double articleTopP = 0.98d;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public double getArticleTemperature() {
            return articleTemperature;
        }

        public void setArticleTemperature(double articleTemperature) {
            this.articleTemperature = articleTemperature;
        }

        public double getArticleTopP() {
            return articleTopP;
        }

        public void setArticleTopP(double articleTopP) {
            this.articleTopP = articleTopP;
        }
    }
}
