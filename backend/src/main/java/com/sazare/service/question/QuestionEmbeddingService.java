package com.sazare.service.question;

import com.sazare.dto.QuestionEmbeddingMatch;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionEmbeddingCandidate;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.QuestionEmbeddingMapper;
import com.sazare.service.ai.AiEmbeddingClient;
import com.sazare.vo.QuestionEmbeddingBackfillVO;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class QuestionEmbeddingService {

    public static final double SIMILARITY_THRESHOLD = 0.80d;
    private static final String ARTICLE_QUESTION_TYPE = "TRANSLATION_ZH_TO_JA_ARTICLE";
    private static final int DIMENSION = 768;

    private final QuestionEmbeddingMapper questionEmbeddingMapper;
    private final AiEmbeddingClient aiEmbeddingClient;

    public QuestionEmbeddingService(
            QuestionEmbeddingMapper questionEmbeddingMapper,
            AiEmbeddingClient aiEmbeddingClient
    ) {
        this.questionEmbeddingMapper = questionEmbeddingMapper;
        this.aiEmbeddingClient = aiEmbeddingClient;
    }

    public List<Float> embedQuestion(String sourceText, String contextText) {
        List<Float> embedding = aiEmbeddingClient.embed(buildContent(sourceText, contextText));
        validateEmbedding(embedding);
        return embedding;
    }

    public List<Float> embedArticleBody(String sourceText) {
        List<Float> embedding = aiEmbeddingClient.embed(buildArticleContent(sourceText));
        validateEmbedding(embedding);
        return embedding;
    }

    public List<QuestionEmbeddingMatch> findSimilarQuestions(List<Float> embedding) {
        return findSimilarQuestions(embedding, null);
    }

    public List<QuestionEmbeddingMatch> findSimilarQuestions(List<Float> embedding, String questionType) {
        validateEmbedding(embedding);
        return questionEmbeddingMapper.selectSimilarQuestionEmbeddings(
                toVectorLiteral(embedding),
                SIMILARITY_THRESHOLD,
                questionType
        );
    }

    public boolean isSimilar(List<Float> left, List<Float> right) {
        validateEmbedding(left);
        validateEmbedding(right);
        double dotProduct = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < DIMENSION; i++) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dotProduct += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return false;
        }
        return dotProduct / Math.sqrt(leftNorm * rightNorm) >= SIMILARITY_THRESHOLD;
    }

    public void saveEmbedding(Question question, List<Float> embedding) {
        validateEmbedding(embedding);
        questionEmbeddingMapper.upsertQuestionEmbedding(
                question.getId(),
                toVectorLiteral(embedding),
                contentHash(question.getQuestionType(), question.getSourceText(), question.getContextText()),
                aiEmbeddingClient.modelName(),
                LocalDateTime.now()
        );
    }

    public void synchronizeEmbedding(Question question) {
        List<Float> embedding = isArticle(question.getQuestionType())
                ? embedArticleBody(question.getSourceText())
                : embedQuestion(question.getSourceText(), question.getContextText());
        saveEmbedding(question, embedding);
    }

    public QuestionEmbeddingBackfillVO backfill(int batchSize) {
        List<QuestionEmbeddingCandidate> staleCandidates = questionEmbeddingMapper.selectRegularQuestionEmbeddingCandidates().stream()
                .filter(this::isStale)
                .toList();
        List<QuestionEmbeddingCandidate> batch = staleCandidates.stream().limit(batchSize).toList();
        for (QuestionEmbeddingCandidate candidate : batch) {
            Question question = new Question();
            question.setId(candidate.getQuestionId());
            question.setQuestionType(candidate.getQuestionType());
            question.setSourceText(candidate.getSourceText());
            question.setContextText(candidate.getContextText());
            synchronizeEmbedding(question);
        }
        return new QuestionEmbeddingBackfillVO(batch.size(), staleCandidates.size() - batch.size());
    }

    public String contentHash(String sourceText, String contextText) {
        return hash(buildContent(sourceText, contextText));
    }

    public String contentHash(String questionType, String sourceText, String contextText) {
        String content = isArticle(questionType)
                ? buildArticleContent(sourceText)
                : buildContent(sourceText, contextText);
        return hash(content);
    }

    private String hash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private boolean isStale(QuestionEmbeddingCandidate candidate) {
        return candidate.getContentHash() == null
                || !candidate.getContentHash().equals(contentHash(
                        candidate.getQuestionType(),
                        candidate.getSourceText(),
                        candidate.getContextText()
                ))
                || !aiEmbeddingClient.modelName().equals(candidate.getModelName());
    }

    private String buildContent(String sourceText, String contextText) {
        return "题目原文：" + normalizeText(sourceText) + "\n语境：" + normalizeText(contextText);
    }

    private String buildArticleContent(String sourceText) {
        return "文章正文：" + normalizeText(sourceText);
    }

    private boolean isArticle(String questionType) {
        return ARTICLE_QUESTION_TYPE.equals(questionType);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private void validateEmbedding(List<Float> embedding) {
        if (embedding == null || embedding.size() != DIMENSION
                || embedding.stream().anyMatch(value -> value == null || !Float.isFinite(value))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "嵌入向量必须包含 768 个有效数值");
        }
    }

    private String toVectorLiteral(List<Float> embedding) {
        List<String> values = new ArrayList<>(embedding.size());
        for (Float value : embedding) {
            values.add(Float.toString(value));
        }
        return "[" + String.join(",", values) + "]";
    }
}
