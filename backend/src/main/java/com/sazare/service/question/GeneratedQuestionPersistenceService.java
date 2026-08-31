package com.sazare.service.question;

import com.sazare.dto.AiArticleBlueprintDTO;
import com.sazare.dto.AiGeneratedQuestionDTO;
import com.sazare.dto.AiQuestionAnswerDTO;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.Tag;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.ArticleGenerationMetadataMapper;
import com.sazare.mapper.QuestionAnswerMapper;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import com.sazare.service.ai.validation.AiQuestionGenerationResponseValidator.ValidatedArticle;
import com.sazare.service.ai.validation.AiQuestionGenerationResponseValidator.ValidatedQuestion;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GeneratedQuestionPersistenceService {

    private static final String SOURCE_TYPE_AI = "AI";
    private static final String ANSWER_TYPE_STANDARD = "STANDARD";

    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionTagMapper questionTagMapper;
    private final QuestionEmbeddingService questionEmbeddingService;
    private final ArticleGenerationMetadataMapper articleGenerationMetadataMapper;
    private final ObjectMapper objectMapper;

    public GeneratedQuestionPersistenceService(
            QuestionMapper questionMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionTagMapper questionTagMapper,
            QuestionEmbeddingService questionEmbeddingService,
            ArticleGenerationMetadataMapper articleGenerationMetadataMapper,
            ObjectMapper objectMapper
    ) {
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionTagMapper = questionTagMapper;
        this.questionEmbeddingService = questionEmbeddingService;
        this.articleGenerationMetadataMapper = articleGenerationMetadataMapper;
        this.objectMapper = objectMapper;
    }

    public SavedQuestion saveShort(ValidatedQuestion validatedQuestion, List<Float> embedding) {
        AiGeneratedQuestionDTO generatedQuestion = validatedQuestion.question();
        LocalDateTime now = LocalDateTime.now();

        Question question = new Question();
        question.setQuestionType(generatedQuestion.questionType());
        question.setSourceText(generatedQuestion.sourceText().trim());
        question.setContextText(generatedQuestion.contextText().trim());
        question.setLevel(generatedQuestion.level());
        question.setDifficulty(generatedQuestion.difficulty());
        question.setGrammarPoint(generatedQuestion.grammarPoint().trim());
        question.setSpoken(generatedQuestion.spoken());
        question.setBusiness(generatedQuestion.business());
        question.setExam(generatedQuestion.exam());
        initializeGeneratedQuestion(question, now);
        questionMapper.insertQuestion(question);
        questionEmbeddingService.saveEmbedding(question, embedding);

        List<QuestionAnswer> answers = saveAnswers(question.getId(), generatedQuestion.answers(), now);
        for (Tag tag : validatedQuestion.tags()) {
            questionTagMapper.insertQuestionTag(question.getId(), tag.getId());
        }
        return new SavedQuestion(question, validatedQuestion.tags(), answers);
    }

    public SavedQuestion saveArticle(ValidatedArticle article, Tag genreTag, List<Float> embedding) {
        LocalDateTime now = LocalDateTime.now();
        Question question = new Question();
        question.setQuestionType(article.article().questionType());
        question.setSourceText(article.sourceText());
        question.setContextText(article.article().contextText().trim());
        question.setLevel(article.article().level());
        question.setDifficulty(article.article().difficulty());
        question.setGrammarPoint(article.article().grammarPoint().trim());
        question.setSpoken(article.article().spoken());
        question.setBusiness(article.article().business());
        question.setExam(article.article().exam());
        initializeGeneratedQuestion(question, now);
        questionMapper.insertQuestion(question);
        questionEmbeddingService.saveEmbedding(question, embedding);

        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(article.referenceText());
        answer.setAnswerType(ANSWER_TYPE_STANDARD);
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        questionAnswerMapper.insertQuestionAnswer(answer);
        questionTagMapper.insertQuestionTag(question.getId(), genreTag.getId());
        articleGenerationMetadataMapper.insertArticleGenerationMetadata(
                question.getId(),
                UUID.fromString(article.blueprint().seed()),
                serializeArticleBlueprint(article.blueprint()),
                now
        );
        return new SavedQuestion(question, List.of(genreTag), List.of());
    }

    private void initializeGeneratedQuestion(Question question, LocalDateTime now) {
        question.setSourceType(SOURCE_TYPE_AI);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
    }

    private List<QuestionAnswer> saveAnswers(
            Long questionId,
            List<AiQuestionAnswerDTO> answerDTOs,
            LocalDateTime now
    ) {
        List<QuestionAnswer> answers = new ArrayList<>();
        for (AiQuestionAnswerDTO answerDTO : answerDTOs) {
            QuestionAnswer answer = new QuestionAnswer();
            answer.setQuestionId(questionId);
            answer.setAnswerText(answerDTO.answerText().trim());
            answer.setAnswerType(answerDTO.answerType());
            answer.setPrimaryAnswer(answerDTO.primaryAnswer());
            answer.setSortOrder(answerDTO.sortOrder());
            answer.setDeleted(false);
            answer.setCreatedAt(now);
            answer.setUpdatedAt(now);
            questionAnswerMapper.insertQuestionAnswer(answer);
            answers.add(answer);
        }
        return List.copyOf(answers);
    }

    private String serializeArticleBlueprint(AiArticleBlueprintDTO blueprint) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "coreConcept", blueprint.coreConcept(),
                    "roles", blueprint.roles()
            ));
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章蓝图序列化失败");
        }
    }

    public record SavedQuestion(Question question, List<Tag> tags, List<QuestionAnswer> answers) {
    }
}
