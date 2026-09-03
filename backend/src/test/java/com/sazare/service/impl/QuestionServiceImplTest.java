package com.sazare.service.impl;

import com.sazare.dto.AiAnswerScoringRequest;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiErrorTypeOptionDTO;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.QuestionAnswerRequest;
import com.sazare.dto.QuestionCreateRequest;
import com.sazare.dto.QuestionEnabledRequest;
import com.sazare.dto.QuestionEmbeddingMatch;
import com.sazare.dto.QuestionQueryRequest;
import com.sazare.dto.QuestionTagRow;
import com.sazare.dto.QuestionUpdateRequest;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.Tag;
import com.sazare.entity.User;
import com.sazare.entity.UserAnswer;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.QuestionAnswerMapper;
import com.sazare.mapper.ArticleGenerationMetadataMapper;
import com.sazare.mapper.ErrorTypeMapper;
import com.sazare.mapper.QuestionMapper;
import com.sazare.mapper.QuestionTagMapper;
import com.sazare.mapper.ReviewCycleQuestionMapper;
import com.sazare.mapper.TagMapper;
import com.sazare.mapper.UserAnswerMapper;
import com.sazare.mapper.UserMapper;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.ai.AiAnswerScoringClient;
import com.sazare.service.ai.AiQuestionClient;
import com.sazare.service.ai.AiQuestionPrompt;
import com.sazare.service.ai.prompt.AiAnswerScoringPromptBuilder;
import com.sazare.service.ai.prompt.AiQuestionPromptBuilder;
import com.sazare.service.ai.validation.AiErrorAnalysisValidator;
import com.sazare.service.ai.validation.AiAnswerScoringResponseValidator;
import com.sazare.service.ai.validation.AiQuestionGenerationResponseValidator;
import com.sazare.service.question.GeneratedQuestionPersistenceService;
import com.sazare.service.question.QuestionEmbeddingService;
import com.sazare.vo.AnswerReviewVO;
import com.sazare.vo.PageVO;
import com.sazare.vo.QuestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceImplTest {

    private TagMapper tagMapper;
    private QuestionMapper questionMapper;
    private QuestionAnswerMapper questionAnswerMapper;
    private QuestionTagMapper questionTagMapper;
    private ReviewCycleQuestionMapper reviewCycleQuestionMapper;
    private UserMapper userMapper;
    private UserAnswerMapper userAnswerMapper;
    private ErrorTypeMapper errorTypeMapper;
    private DictionaryCacheService dictionaryCacheService;
    private AiQuestionClient aiQuestionClient;
    private AiAnswerScoringClient aiAnswerScoringClient;
    private QuestionEmbeddingService questionEmbeddingService;
    private ArticleGenerationMetadataMapper articleGenerationMetadataMapper;
    private PlatformTransactionManager transactionManager;
    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        questionMapper = mock(QuestionMapper.class);
        questionAnswerMapper = mock(QuestionAnswerMapper.class);
        questionTagMapper = mock(QuestionTagMapper.class);
        reviewCycleQuestionMapper = mock(ReviewCycleQuestionMapper.class);
        userMapper = mock(UserMapper.class);
        userAnswerMapper = mock(UserAnswerMapper.class);
        errorTypeMapper = mock(ErrorTypeMapper.class);
        dictionaryCacheService = mock(DictionaryCacheService.class);
        aiQuestionClient = mock(AiQuestionClient.class);
        aiAnswerScoringClient = mock(AiAnswerScoringClient.class);
        questionEmbeddingService = mock(QuestionEmbeddingService.class);
        articleGenerationMetadataMapper = mock(ArticleGenerationMetadataMapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        ObjectMapper objectMapper = new ObjectMapper();
        when(dictionaryCacheService.getEnabledLeafErrorTypes()).thenReturn(List.of(errorTypeOption()));
        questionService = new QuestionServiceImpl(
                tagMapper,
                questionMapper,
                questionAnswerMapper,
                questionTagMapper,
                reviewCycleQuestionMapper,
                userMapper,
                userAnswerMapper,
                errorTypeMapper,
                dictionaryCacheService,
                new AiQuestionPromptBuilder(objectMapper),
                aiQuestionClient,
                new AiAnswerScoringPromptBuilder(objectMapper),
                aiAnswerScoringClient,
                new AiAnswerScoringResponseValidator(objectMapper, new AiErrorAnalysisValidator()),
                new AiQuestionGenerationResponseValidator(objectMapper),
                questionEmbeddingService,
                new GeneratedQuestionPersistenceService(
                        questionMapper,
                        questionAnswerMapper,
                        questionTagMapper,
                        questionEmbeddingService,
                        articleGenerationMetadataMapper,
                        objectMapper
                ),
                new TransactionTemplate(transactionManager),
                objectMapper
        );
    }

    @Test
    void generateQuestionsByAiShouldSaveValidatedQuestionWithoutEmbeddingExcludedSources() {
        Tag sceneTag = tag(1L, "SCENE", "DAILY_LIFE_WEATHER", "天气");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_PROPOSE_PLAN", "提出计划");
        when(tagMapper.selectEnabledTagsByCodes(eq("SCENE"), anyList())).thenReturn(List.of(sceneTag));
        when(tagMapper.selectEnabledTagsByCodes(eq("FUNCTION"), anyList())).thenReturn(List.of(functionTag));
        when(aiQuestionClient.generateQuestions(any(), any(), anyList(), anyList())).thenReturn(validAiJson());
        when(questionEmbeddingService.embedQuestion(any(), any())).thenReturn(vector());
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(100L);
            return 1;
        });
        AtomicLong answerId = new AtomicLong(200L);
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(answerId.getAndIncrement());
            return 1;
        });

        List<QuestionVO> questions = questionService.generateQuestionsByAi(requestWithExcludedSourceText());

        assertThat(questions).hasSize(1);
        QuestionVO question = questions.getFirst();
        assertThat(question.id()).isEqualTo(100L);
        assertThat(question.sourceType()).isEqualTo("AI");
        assertThat(question.tags()).extracting("code")
                .containsExactly("DAILY_LIFE_WEATHER", "FUNCTION_PROPOSE_PLAN");
        assertThat(question.answers()).hasSize(1);

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).insertQuestion(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getSourceType()).isEqualTo("AI");
        verify(questionAnswerMapper).insertQuestionAnswer(any(QuestionAnswer.class));
        verify(questionTagMapper).insertQuestionTag(100L, 1L);
        verify(questionTagMapper).insertQuestionTag(100L, 2L);
        verify(questionEmbeddingService).embedQuestion(
                "如果明天下雨，我们就在家学习吧。",
                "朋友之间讨论明天的安排。"
        );
        verify(questionEmbeddingService).findSimilarQuestions(anyList());
        verify(questionEmbeddingService).saveEmbedding(any(Question.class), anyList());
        InOrder order = inOrder(aiQuestionClient, transactionManager, questionMapper);
        order.verify(aiQuestionClient).generateQuestions(any(), any(), anyList(), anyList());
        order.verify(transactionManager).getTransaction(any());
        order.verify(questionMapper).insertQuestion(any());
    }

    @Test
    void generateArticleByAiShouldSaveOneArticleAndHideReferenceAnswer() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> validArticleJson(invocation.getArgument(2)));
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(101L);
            return 1;
        });
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(201L);
            return 1;
        });

        QuestionVO question = questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", "旅行", null)
        );

        assertThat(question.questionType()).isEqualTo("TRANSLATION_ZH_TO_JA_ARTICLE");
        assertThat(question.sourceType()).isEqualTo("AI");
        assertThat(question.contextText()).isEqualTo("叙事文，使用自然连贯的书面语。");
        assertThat(question.sourceText()).contains("\n\n");
        assertThat(question.grammarPoint()).contains("郊外：郊外（こうがい）", "避雨：雨宿り（あまやどり）");
        assertThat(question.tags()).extracting("code").containsExactly("NARRATIVE");
        assertThat(question.answers()).isEmpty();
        ArgumentCaptor<QuestionAnswer> answerCaptor = ArgumentCaptor.forClass(QuestionAnswer.class);
        verify(questionAnswerMapper).insertQuestionAnswer(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getAnswerText()).contains("\n\n");
        verify(questionTagMapper).insertQuestionTag(101L, 3L);
        verify(questionEmbeddingService).embedArticleBody(question.sourceText());
        verify(articleGenerationMetadataMapper).insertArticleGenerationMetadata(
                eq(101L), any(), anyString(), any()
        );
    }

    @ParameterizedTest
    @CsvSource({
            "SHORT,60",
            "SHORT,100",
            "MEDIUM,120",
            "MEDIUM,180",
            "LONG,200",
            "LONG,280"
    })
    void generateArticleByAiShouldAcceptSelectedLengthTierBoundaries(String lengthTier, int articleLength) {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> articleJsonWithChineseLength(invocation.getArgument(2), articleLength));
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(120L);
            return 1;
        });
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(220L);
            return 1;
        });

        QuestionVO question = questionService.generateArticleByAi(
                new AiArticleGenerationRequest(
                        "N3", 3, "NARRATIVE", null, null, "ZH_TO_JA", lengthTier
                )
        );

        assertThat(question.sourceText().replaceAll("\\s", "")).hasSize(articleLength);
    }

    @ParameterizedTest
    @CsvSource({
            "SHORT,59,60,100",
            "SHORT,101,60,100",
            "MEDIUM,119,120,180",
            "MEDIUM,181,120,180",
            "LONG,199,200,280",
            "LONG,281,200,280"
    })
    void generateArticleByAiShouldRejectTextOutsideSelectedLengthTier(
            String lengthTier,
            int articleLength,
            int minimum,
            int maximum
    ) {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> articleJsonWithChineseLength(invocation.getArgument(2), articleLength));

        assertThatThrownBy(() -> questionService.generateArticleByAi(
                new AiArticleGenerationRequest(
                        "N3", 3, "NARRATIVE", null, null, "ZH_TO_JA", lengthTier
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 文章长度必须在 " + minimum + " 到 " + maximum + " 个非空白字符之间");
        verify(questionMapper, never()).insertQuestion(any());
    }

    @ParameterizedTest
    @CsvSource({"SHORT,45", "MEDIUM,135", "LONG,210"})
    void generateEnglishArticleShouldUseWordRanges(String lengthTier, int wordCount) {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> articleJsonWithEnglishWordCount(invocation.getArgument(2), wordCount));
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(121L);
            return 1;
        });
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(221L);
            return 1;
        });

        QuestionVO question = questionService.generateArticleByAi(
                new AiArticleGenerationRequest(
                        "N3", 3, "NARRATIVE", null, null, "EN_TO_JA", lengthTier
                )
        );

        assertThat(question.questionType()).isEqualTo("TRANSLATION_EN_TO_JA_ARTICLE");
        assertThat(question.sourceText().trim().split("\\s+")).hasSize(wordCount);
    }

    @Test
    void generateArticleByAiShouldRandomlySelectEnabledGenreWhenNotSpecified() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(dictionaryCacheService.getEnabledTagsByType("GENRE")).thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> validArticleJson(invocation.getArgument(2)));
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(103L);
            return 1;
        });

        QuestionVO question = questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, null, null, null)
        );

        assertThat(question.tags()).extracting("code").containsExactly("NARRATIVE");
        ArgumentCaptor<AiArticleGenerationRequest> requestCaptor =
                ArgumentCaptor.forClass(AiArticleGenerationRequest.class);
        verify(aiQuestionClient).generateArticle(any(), requestCaptor.capture(), anyString());
        assertThat(requestCaptor.getValue().genreTagCode()).isEqualTo("NARRATIVE");
        verify(dictionaryCacheService).getEnabledTagsByType("GENRE");
    }

    @Test
    void generateArticleByAiShouldAcceptClosingQuoteAfterChineseSentencePunctuation() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> validArticleJson(invocation.getArgument(2))
                        .replace("上周末，我和朋友决定去郊外的一座小镇旅行。", "他说：“这次我们要按照原来的计划一起去郊外旅行吧。”"));
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(104L);
            return 1;
        });

        QuestionVO question = questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null)
        );

        assertThat(question.sourceText()).startsWith("他说：“这次我们要按照原来的计划一起去郊外旅行吧。”");
        verify(questionMapper).insertQuestion(any());
    }

    @Test
    void generateArticleByAiShouldReportSentenceIndexAndDetectedKana() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> validArticleJson(invocation.getArgument(2))
                        .replace("我们原本计划乘早班电车出发", "我们の原本计划乘早班电车出发"));

        assertThatThrownBy(() -> questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第 2 句 chineseText")
                .hasMessageContaining("检测到：の");

        verify(aiQuestionClient, times(1)).generateArticle(any(), any(), anyString());
        verify(questionMapper, never()).insertQuestion(any());
    }

    @Test
    void generateArticleByAiShouldPassDuplicateDetailsToNextAttempt() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> validArticleJson(invocation.getArgument(2)));
        when(questionEmbeddingService.findSimilarQuestions(anyList(), eq("TRANSLATION_ZH_TO_JA_ARTICLE")))
                .thenReturn(List.of(
                        new QuestionEmbeddingMatch(47L, "第一篇命中的历史文章正文。", 0.93d),
                        new QuestionEmbeddingMatch(49L, "第二篇命中的历史文章正文。", 0.88d)
                ))
                .thenReturn(List.of());
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(102L);
            return 1;
        });

        questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", "旅行", null)
        );

        ArgumentCaptor<AiQuestionPrompt> promptCaptor = ArgumentCaptor.forClass(AiQuestionPrompt.class);
        verify(aiQuestionClient, times(2)).generateArticle(promptCaptor.capture(), any(), anyString());
        List<AiQuestionPrompt> prompts = promptCaptor.getAllValues();
        assertThat(prompts.get(1).userPrompt())
                .contains("正文向量与 2 篇历史文章相似")
                .contains("最高相似度为 0.9300")
                .contains("第一篇命中的历史文章正文。")
                .contains("第二篇命中的历史文章正文。")
                .contains("上周末，我和朋友决定去郊外的一座小镇旅行。");
        verify(articleGenerationMetadataMapper).insertArticleGenerationMetadata(
                eq(102L), any(), anyString(), any()
        );
    }

    @Test
    void generateArticleByAiShouldStopAfterThreeDuplicateAttempts() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString()))
                .thenAnswer(invocation -> validArticleJson(invocation.getArgument(2)));
        when(questionEmbeddingService.findSimilarQuestions(anyList(), eq("TRANSLATION_ZH_TO_JA_ARTICLE")))
                .thenReturn(List.of(new QuestionEmbeddingMatch(62L, "持续命中的历史文章正文。", 0.91d)));

        assertThatThrownBy(() -> questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补生成后仍未获得可用文章");

        verify(aiQuestionClient, times(3)).generateArticle(any(), any(), anyString());
        verify(questionMapper, never()).insertQuestion(any());
        verify(articleGenerationMetadataMapper, never())
                .insertArticleGenerationMetadata(any(), any(), anyString(), any());
    }

    @Test
    void generateArticleByAiShouldNotRetryInvalidJson() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any(), anyString())).thenReturn("{}");

        assertThatThrownBy(() -> questionService.generateArticleByAi(
                new AiArticleGenerationRequest("N3", 3, "NARRATIVE", null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blueprint 和 article");

        verify(aiQuestionClient, times(1)).generateArticle(any(), any(), anyString());
    }

    @Test
    void generateQuestionsByAiShouldRollbackWhenSimilarQuestionCannotBeReplaced() {
        Tag sceneTag = tag(1L, "SCENE", "DAILY_LIFE_WEATHER", "天气");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_PROPOSE_PLAN", "提出计划");
        when(tagMapper.selectEnabledTagsByCodes(eq("SCENE"), anyList())).thenReturn(List.of(sceneTag));
        when(tagMapper.selectEnabledTagsByCodes(eq("FUNCTION"), anyList())).thenReturn(List.of(functionTag));
        when(aiQuestionClient.generateQuestions(any(), any(), anyList(), anyList())).thenReturn(validAiJson());
        when(questionEmbeddingService.embedQuestion(any(), any())).thenReturn(vector());
        when(questionEmbeddingService.findSimilarQuestions(anyList()))
                .thenReturn(List.of(new QuestionEmbeddingMatch(99L, "历史短句", 0.95d)));

        assertThatThrownBy(() -> questionService.generateQuestionsByAi(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("补生成后仍未达到要求数量");

        verify(aiQuestionClient, times(3)).generateQuestions(any(), any(), anyList(), anyList());
        verify(questionMapper, never()).insertQuestion(any());
        verify(questionEmbeddingService, never()).saveEmbedding(any(), anyList());
    }

    @Test
    void generateQuestionsByAiShouldRejectTagOutsidePromptCandidates() {
        Tag sceneTag = tag(1L, "SCENE", "DAILY_LIFE_WEATHER", "天气");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_PROPOSE_PLAN", "提出计划");
        when(tagMapper.selectEnabledTagsByCodes(eq("SCENE"), anyList())).thenReturn(List.of(sceneTag));
        when(tagMapper.selectEnabledTagsByCodes(eq("FUNCTION"), anyList())).thenReturn(List.of(functionTag));
        when(aiQuestionClient.generateQuestions(any(), any(), anyList(), anyList())).thenReturn("""
                {
                  "questions": [
                    {
                      "questionType": "TRANSLATION_ZH_TO_JA",
                      "sourceText": "如果明天下雨，我们就在家学习吧。",
                      "contextText": "朋友之间讨论明天的安排。",
                      "level": "N4",
                      "difficulty": 3,
                      "grammarPoint": "条件表現「たら」",
                      "spoken": true,
                      "business": false,
                      "exam": false,
                      "tagCodes": ["DAILY_LIFE_WEATHER", "UNKNOWN_TAG"],
                      "answers": [
                        {
                          "answerText": "明日雨が降ったら、家で勉強しましょう。",
                          "answerType": "STANDARD",
                          "primaryAnswer": true,
                          "sortOrder": 0
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> questionService.generateQuestionsByAi(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法标签 code");

        verify(questionMapper, never()).insertQuestion(any());
        verify(questionAnswerMapper, never()).insertQuestionAnswer(any());
        verify(questionTagMapper, never()).insertQuestionTag(any(), any());
    }

    @Test
    void generateQuestionsByAiShouldRejectMissingPrimaryStandardAnswer() {
        Tag sceneTag = tag(1L, "SCENE", "DAILY_LIFE_WEATHER", "天气");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_PROPOSE_PLAN", "提出计划");
        when(tagMapper.selectEnabledTagsByCodes(eq("SCENE"), anyList())).thenReturn(List.of(sceneTag));
        when(tagMapper.selectEnabledTagsByCodes(eq("FUNCTION"), anyList())).thenReturn(List.of(functionTag));
        when(aiQuestionClient.generateQuestions(any(), any(), anyList(), anyList())).thenReturn("""
                {
                  "questions": [
                    {
                      "questionType": "TRANSLATION_ZH_TO_JA",
                      "sourceText": "如果明天下雨，我们就在家学习吧。",
                      "contextText": "朋友之间讨论明天的安排。",
                      "level": "N4",
                      "difficulty": 3,
                      "grammarPoint": "条件表現「たら」",
                      "spoken": true,
                      "business": false,
                      "exam": false,
                      "tagCodes": ["DAILY_LIFE_WEATHER", "FUNCTION_PROPOSE_PLAN"],
                      "answers": [
                        {
                          "answerText": "明日雨が降ったら、家で勉強しましょう。",
                          "answerType": "REFERENCE",
                          "primaryAnswer": false,
                          "sortOrder": 0
                        }
                      ]
                    }
                  ]
                }
                """);

        assertThatThrownBy(() -> questionService.generateQuestionsByAi(request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主标准答案");

        verify(questionMapper, never()).insertQuestion(any());
    }

    @Test
    void generateQuestionsByAiShouldUseDefaultRequestValues() {
        Tag sceneTag = tag(1L, "SCENE", "DAILY_LIFE_WEATHER", "天气");
        Tag parentSceneTag = tag(2L, "SCENE", "DAILY_LIFE", "日常生活");
        parentSceneTag.setParentId(null);
        when(dictionaryCacheService.getEnabledTagsByType("SCENE")).thenReturn(List.of(parentSceneTag, sceneTag));
        when(dictionaryCacheService.getEnabledTagsByType("FUNCTION")).thenReturn(List.of());
        when(aiQuestionClient.generateQuestions(any(), any(), anyList(), anyList())).thenReturn("""
                {
                  "questions": [
                    {
                      "questionType": "TRANSLATION_ZH_TO_JA",
                      "sourceText": "请告诉我车站在哪里。",
                      "contextText": "问路场景。",
                      "level": "N3",
                      "difficulty": 3,
                      "grammarPoint": "場所を尋ねる表現",
                      "spoken": true,
                      "business": false,
                      "exam": false,
                      "tagCodes": ["DAILY_LIFE_WEATHER"],
                      "answers": [
                        {
                          "answerText": "駅はどこですか。",
                          "answerType": "STANDARD",
                          "primaryAnswer": true,
                          "sortOrder": 0
                        }
                      ]
                    }
                  ]
                }
                """);
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(101L);
            return 1;
        });
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(201L);
            return 1;
        });

        List<QuestionVO> questions = questionService.generateQuestionsByAi(new AiQuestionGenerationRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(questions).hasSize(1);
        assertThat(questions.getFirst().level()).isEqualTo("N3");
        assertThat(questions.getFirst().difficulty()).isEqualTo(3);
        verify(dictionaryCacheService).getEnabledTagsByType("SCENE");
        verify(dictionaryCacheService).getEnabledTagsByType("FUNCTION");
        verify(aiQuestionClient).generateQuestions(any(), any(),
                argThat(options -> options.size() == 1 && "DAILY_LIFE_WEATHER".equals(options.getFirst().code())),
                anyList());
    }

    @Test
    void createQuestionShouldSaveManualQuestionWithTagsAndAnswers() {
        Tag sceneTag = tag(1L, "SCENE", "FINANCE_BANK", "银行");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_EXPRESS_PLAN", "表达计划");
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList())).thenReturn(List.of(sceneTag, functionTag));
        when(questionEmbeddingService.embedQuestion(any(), any())).thenReturn(vector());
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(100L);
            return 1;
        });
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(200L + answer.getSortOrder());
            return 1;
        });

        QuestionVO question = questionService.createQuestion(createRequest());

        assertThat(question.id()).isEqualTo(100L);
        assertThat(question.sourceType()).isEqualTo("MANUAL");
        verify(questionEmbeddingService).embedQuestion(
                "我今天下午要去银行办理转账。",
                "日常生活中说明下午的计划。"
        );
        verify(questionEmbeddingService).saveEmbedding(any(Question.class), any());
        InOrder order = inOrder(questionEmbeddingService, transactionManager, questionMapper);
        order.verify(questionEmbeddingService).embedQuestion(any(), any());
        order.verify(transactionManager).getTransaction(any());
        order.verify(questionMapper).insertQuestion(any());
        assertThat(question.tags()).extracting("code")
                .containsExactly("FINANCE_BANK", "FUNCTION_EXPRESS_PLAN");
        assertThat(question.answers()).extracting("answerType")
                .containsExactly("STANDARD", "REFERENCE");

        ArgumentCaptor<Question> questionCaptor = ArgumentCaptor.forClass(Question.class);
        verify(questionMapper).insertQuestion(questionCaptor.capture());
        assertThat(questionCaptor.getValue().getSourceText()).isEqualTo("我今天下午要去银行办理转账。");
        assertThat(questionCaptor.getValue().getEnabled()).isTrue();
        verify(questionTagMapper).insertQuestionTag(100L, 1L);
        verify(questionTagMapper).insertQuestionTag(100L, 2L);
    }

    @Test
    void createQuestionShouldAcceptEnglishSourceForEnglishToJapaneseQuestion() {
        Tag sceneTag = tag(1L, "SCENE", "FINANCE_BANK", "银行");
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList())).thenReturn(List.of(sceneTag));
        when(questionMapper.insertQuestion(any())).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(100L);
            return 1;
        });
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(200L);
            return 1;
        });

        QuestionVO question = questionService.createQuestion(englishCreateRequest());

        assertThat(question.questionType()).isEqualTo("TRANSLATION_EN_TO_JA");
        assertThat(question.sourceText()).isEqualTo("I need to transfer money at the bank this afternoon.");
        verify(questionMapper).insertQuestion(any());
    }

    @Test
    void createQuestionShouldRejectJapaneseKanaInChineseSource() {
        QuestionCreateRequest request = new QuestionCreateRequest(
                "TRANSLATION_ZH_TO_JA",
                "我今天要去コンビニ买东西。",
                "日常购物场景。",
                "N4",
                3,
                "予定を表す表現",
                true,
                false,
                false,
                List.of("FINANCE_BANK"),
                List.of(new QuestionAnswerRequest(
                        "今日はコンビニに買い物に行きます。",
                        "STANDARD",
                        true,
                        0
                ))
        );

        assertThatThrownBy(() -> questionService.createQuestion(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("日语假名");

        verify(questionMapper, never()).insertQuestion(any());
    }

    @Test
    void createArticleShouldRejectMismatchedSourceAndAnswerSegments() {
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList()))
                .thenReturn(List.of(tag(3L, "GENRE", "NARRATIVE", "叙事文")));

        QuestionCreateRequest request = articleCreateRequest(
                "TRANSLATION_ZH_TO_JA_ARTICLE",
                "今天我去了图书馆。\n\n回家后我读完了借来的书。",
                "今日、図書館に行きました。"
        );

        assertThatThrownBy(() -> questionService.createQuestion(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("段落数必须一致");

        verify(questionMapper, never()).insertQuestion(any());
    }

    @Test
    void createQuestionShouldRejectTagsWithoutSceneTag() {
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_EXPRESS_PLAN", "表达计划");
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList())).thenReturn(List.of(functionTag));

        assertThatThrownBy(() -> questionService.createQuestion(createRequestWithoutSceneTag()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("场景标签");

        verify(questionMapper, never()).insertQuestion(any());
    }

    @Test
    void listQuestionsShouldReturnPagedQuestionsWithTagsAndAnswers() {
        Question question = question(100L);
        QuestionAnswer answer = answer(200L, "今日の午後、銀行へ振り込みに行きます。");
        QuestionTagRow tag = tagRow(100L, 1L, "SCENE", "FINANCE_BANK", "银行");
        when(questionMapper.countQuestions(any())).thenReturn(1L);
        when(questionMapper.selectQuestionIds(any(), eq(20), eq(0L))).thenReturn(List.of(100L));
        when(questionMapper.selectQuestionsByIds(List.of(100L))).thenReturn(List.of(question));
        when(tagMapper.selectEnabledTagsByQuestionIds(List.of(100L))).thenReturn(List.of(tag));
        when(questionAnswerMapper.selectActiveAnswersByQuestionIds(List.of(100L))).thenReturn(List.of(answer));

        PageVO<QuestionVO> page = questionService.listQuestions(new QuestionQueryRequest(
                null,
                "N4",
                null,
                List.of("FINANCE_BANK,FUNCTION_EXPRESS_PLAN"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().tags()).extracting("code").containsExactly("FINANCE_BANK");
        assertThat(page.items().getFirst().answers()).extracting("answerText")
                .containsExactly("今日の午後、銀行へ振り込みに行きます。");

        ArgumentCaptor<QuestionQueryRequest> requestCaptor = ArgumentCaptor.forClass(QuestionQueryRequest.class);
        verify(questionMapper).countQuestions(requestCaptor.capture());
        assertThat(requestCaptor.getValue().tagCodes())
                .containsExactly("FINANCE_BANK", "FUNCTION_EXPRESS_PLAN");
    }

    @Test
    void getRandomQuestionShouldReturnQuestionWithTagsAndAnswers() {
        Question question = question(100L);
        QuestionAnswer answer = answer(200L, "今日の午後、銀行へ振り込みに行きます。");
        QuestionTagRow tag = tagRow(100L, 1L, "SCENE", "FINANCE_BANK", "银行");
        when(questionMapper.selectRandomQuestionId(any())).thenReturn(100L);
        when(questionMapper.selectQuestionsByIds(List.of(100L))).thenReturn(List.of(question));
        when(tagMapper.selectEnabledTagsByQuestionIds(List.of(100L))).thenReturn(List.of(tag));
        when(questionAnswerMapper.selectActiveAnswersByQuestionIds(List.of(100L))).thenReturn(List.of(answer));

        QuestionVO result = questionService.getRandomQuestion(new QuestionQueryRequest(
                null,
                "N4",
                null,
                List.of("FINANCE_BANK,FUNCTION_EXPRESS_PLAN"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.tags()).extracting("code").containsExactly("FINANCE_BANK");
        assertThat(result.answers()).extracting("answerText")
                .containsExactly("今日の午後、銀行へ振り込みに行きます。");

        ArgumentCaptor<QuestionQueryRequest> requestCaptor = ArgumentCaptor.forClass(QuestionQueryRequest.class);
        verify(questionMapper).selectRandomQuestionId(requestCaptor.capture());
        assertThat(requestCaptor.getValue().tagCodes())
                .containsExactly("FINANCE_BANK", "FUNCTION_EXPRESS_PLAN");
    }

    @Test
    void getRandomArticleShouldNotReturnReferenceAnswer() {
        Question question = articleQuestion(100L);
        QuestionAnswer answer = answer(200L, "先週、友人と京都へ旅行しました。\n\n天気はよくありませんでしたが、楽しく過ごしました。");
        QuestionTagRow tag = tagRow(100L, 3L, "GENRE", "NARRATIVE", "叙事文");
        when(questionMapper.selectRandomQuestionId(any())).thenReturn(100L);
        when(questionMapper.selectQuestionsByIds(List.of(100L))).thenReturn(List.of(question));
        when(tagMapper.selectEnabledTagsByQuestionIds(List.of(100L))).thenReturn(List.of(tag));
        when(questionAnswerMapper.selectActiveAnswersByQuestionIds(List.of(100L))).thenReturn(List.of(answer));

        QuestionVO result = questionService.getRandomQuestion(new QuestionQueryRequest(
                "TRANSLATION_ZH_TO_JA_ARTICLE",
                null,
                null,
                List.of("NARRATIVE"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result).isNotNull();
        assertThat(result.questionType()).isEqualTo("TRANSLATION_ZH_TO_JA_ARTICLE");
        assertThat(result.answers()).isEmpty();
    }

    @Test
    void getRandomQuestionShouldReturnNullWhenNoQuestionMatched() {
        when(questionMapper.selectRandomQuestionId(any())).thenReturn(null);

        QuestionVO result = questionService.getRandomQuestion(new QuestionQueryRequest(
                null,
                "N1",
                5,
                List.of("UNKNOWN_SCENE"),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result).isNull();
        verify(questionMapper, never()).selectQuestionsByIds(anyList());
        verify(tagMapper, never()).selectEnabledTagsByQuestionIds(anyList());
        verify(questionAnswerMapper, never()).selectActiveAnswersByQuestionIds(anyList());
    }

    @Test
    void updateQuestionShouldReplaceAnswersAndTags() {
        Question existingQuestion = question(100L);
        existingQuestion.setSourceType("AI");
        Tag sceneTag = tag(1L, "SCENE", "FINANCE_BANK", "银行");
        when(questionMapper.selectQuestionById(100L)).thenReturn(existingQuestion);
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList())).thenReturn(List.of(sceneTag));
        when(questionEmbeddingService.contentHash(anyString(), anyString(), anyString())).thenReturn("same-hash");
        when(questionMapper.updateQuestion(any())).thenReturn(1);
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(200L);
            return 1;
        });

        QuestionVO question = questionService.updateQuestion(100L, updateRequest());

        assertThat(question.id()).isEqualTo(100L);
        assertThat(question.sourceType()).isEqualTo("AI");
        assertThat(question.tags()).extracting("code").containsExactly("FINANCE_BANK");
        verify(questionAnswerMapper).logicalDeleteByQuestionId(100L);
        verify(questionTagMapper).deleteQuestionTagsByQuestionId(100L);
        verify(questionTagMapper).insertQuestionTag(100L, 1L);
        verify(questionEmbeddingService, never()).embedQuestion(any(), any());
        verify(questionEmbeddingService, never()).saveEmbedding(any(), any());
    }

    @Test
    void updateArticleShouldAcceptShortEnglishArticleWithoutGenerationLengthTier() {
        Question existingQuestion = articleQuestion(100L);
        existingQuestion.setQuestionType("TRANSLATION_EN_TO_JA_ARTICLE");
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(questionMapper.selectQuestionById(100L)).thenReturn(existingQuestion);
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList())).thenReturn(List.of(genreTag));
        when(questionEmbeddingService.contentHash(anyString(), anyString(), anyString()))
                .thenReturn("existing-hash", "updated-hash");
        when(questionMapper.updateQuestion(any())).thenReturn(1);
        when(questionAnswerMapper.insertQuestionAnswer(any())).thenAnswer(invocation -> {
            QuestionAnswer answer = invocation.getArgument(0);
            answer.setId(200L);
            return 1;
        });

        QuestionVO question = questionService.updateQuestion(100L, englishArticleUpdateRequest());

        assertThat(question.sourceText()).isEqualTo("I visited the library.\n\nThen I read a book at home.");
        assertThat(question.answers()).singleElement()
                .extracting("answerText")
                .isEqualTo("図書館に行きました。\n\nその後、家で本を読みました。");
        verify(questionMapper).updateQuestion(any());
        verify(questionEmbeddingService).embedArticleBody(
                "I visited the library.\n\nThen I read a book at home."
        );
        verify(questionEmbeddingService).saveEmbedding(any(), any());
    }

    @Test
    void updateQuestionEnabledShouldRejectDeletedQuestion() {
        when(questionMapper.updateEnabled(eq(100L), eq(false), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> questionService.updateQuestionEnabled(100L, new QuestionEnabledRequest(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题目不存在或已删除");
    }

    @Test
    void updateQuestionEnabledShouldRejectQuestionInProgressReview() {
        when(reviewCycleQuestionMapper.existsInProgressCycleByQuestionId(100L)).thenReturn(true);

        assertThatThrownBy(() -> questionService.updateQuestionEnabled(100L, new QuestionEnabledRequest(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在复习周期中，不能停用");

        verify(questionMapper, never()).updateEnabled(eq(100L), eq(false), any(LocalDateTime.class));
    }

    @Test
    void deleteQuestionShouldLogicalDeleteQuestion() {
        when(questionMapper.logicalDelete(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        questionService.deleteQuestion(100L);

        verify(questionMapper).logicalDelete(eq(100L), any(LocalDateTime.class));
    }

    @Test
    void deleteQuestionShouldRejectQuestionInProgressReview() {
        when(reviewCycleQuestionMapper.existsInProgressCycleByQuestionId(100L)).thenReturn(true);

        assertThatThrownBy(() -> questionService.deleteQuestion(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在复习周期中，不能删除");

        verify(questionMapper, never()).logicalDelete(eq(100L), any(LocalDateTime.class));
    }

    @Test
    void submitAnswerShouldSaveReviewedResultWithCalculatedTotalScore() {
        Question question = question(100L);
        QuestionAnswer answer = answer(200L, "今日の午後、銀行へ振り込みに行きます。");
        Tag tag = tag(1L, "SCENE", "FINANCE_BANK", "银行");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L)).thenReturn(List.of(tag));
        when(userAnswerMapper.insertUserAnswer(any())).thenAnswer(invocation -> {
            UserAnswer userAnswer = invocation.getArgument(0);
            assertThat(userAnswer.getAnswerStatus()).isEqualTo("SUBMITTED");
            assertThat(userAnswer.getAnswerText()).isEqualTo("今日の午後、銀行に送金をしに行きます。");
            userAnswer.setId(300L);
            return 1;
        });
        when(aiAnswerScoringClient.scoreAnswer(any(), any(), any(), anyList(), anyList())).thenReturn(validReviewJson());

        AnswerReviewVO review = questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest("今日の午後、銀行に送金をしに行きます。")
        );

        assertThat(review.userAnswerId()).isEqualTo(300L);
        assertThat(review.answerStatus()).isEqualTo("REVIEWED");
        assertThat(review.totalScore()).isEqualByComparingTo(new BigDecimal("81.50"));
        assertThat(review.overallComment()).isEqualTo("整体意思基本准确，语法和用词可以再自然一些。");
        assertThat(review.errorAnalysis()).hasSize(1);
        assertThat(review.recommendedExpressions()).extracting("expression")
                .containsExactly("今日の午後、銀行へ振り込みに行きます。");

        ArgumentCaptor<BigDecimal> totalScoreCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(userAnswerMapper).updateReviewed(
                eq(300L),
                eq(82),
                eq(78),
                eq(80),
                eq(86),
                totalScoreCaptor.capture(),
                eq("整体意思基本准确，语法和用词可以再自然一些。"),
                any(LocalDateTime.class)
        );
        assertThat(totalScoreCaptor.getValue()).isEqualByComparingTo(new BigDecimal("81.50"));
        InOrder order = inOrder(aiAnswerScoringClient, transactionManager, userAnswerMapper);
        order.verify(aiAnswerScoringClient).scoreAnswer(any(), any(), any(), anyList(), anyList());
        order.verify(transactionManager).getTransaction(any());
        order.verify(userAnswerMapper).insertUserAnswer(any());
    }

    @Test
    void submitArticleAnswerShouldSaveWholeAnswerOnceAndReturnSentenceReviews() {
        Question question = articleQuestion(100L);
        QuestionAnswer answer = answer(200L, "先週、友人と京都へ旅行しました。\n\n天気はよくありませんでしたが、楽しく過ごしました。");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L))
                .thenReturn(List.of(tag(3L, "GENRE", "NARRATIVE", "叙事文")));
        when(userAnswerMapper.insertUserAnswer(any())).thenAnswer(invocation -> {
            UserAnswer userAnswer = invocation.getArgument(0);
            assertThat(userAnswer.getAnswerText())
                    .isEqualTo("先週、友達と京都へ旅行しました。\n\n天気は悪かったですが、楽しく過ごしました。");
            userAnswer.setId(301L);
            return 1;
        });
        when(aiAnswerScoringClient.scoreAnswer(any(), any(), any(), anyList(), anyList()))
                .thenReturn(validArticleReviewJson());

        AnswerReviewVO review = questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest(
                        "\r\n先週、友達と京都へ旅行しました。\r\n\r\n天気は悪かったですが、楽しく過ごしました。\r\n"
                )
        );

        assertThat(review.sentenceReviews()).hasSize(2);
        assertThat(review.revisedAnswer()).isEqualTo(
                "先週、友人と京都へ旅行しました。\n\n天気はよくありませんでしたが、楽しく過ごしました。"
        );
        verify(userAnswerMapper, times(1)).insertUserAnswer(any());
    }

    @Test
    void submitArticleAnswerShouldFallbackToWholeAnswerWhenExcerptIsNotOriginalText() {
        Question question = articleQuestion(100L);
        QuestionAnswer answer = answer(200L, "先週、友人と京都へ旅行しました。\n\n天気はよくありませんでしたが、楽しく過ごしました。");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L))
                .thenReturn(List.of(tag(3L, "GENRE", "NARRATIVE", "叙事文")));
        when(userAnswerMapper.insertUserAnswer(any())).thenAnswer(invocation -> {
            UserAnswer userAnswer = invocation.getArgument(0);
            userAnswer.setId(302L);
            return 1;
        });
        when(aiAnswerScoringClient.scoreAnswer(any(), any(), any(), anyList(), anyList()))
                .thenReturn(validArticleReviewJson().replace(
                        "先週、友達と京都へ旅行しました。天気は悪かったですが、楽しく過ごしました。",
                        "AI が改写したため、用户答案には存在しない抜粋"
                ));

        String userAnswer = "先週、友達と京都へ旅行しました。天気は悪かったですが、楽しく過ごしました。";
        AnswerReviewVO review = questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest(userAnswer)
        );

        assertThat(review.sentenceReviews())
                .extracting("answerExcerpt")
                .containsOnly(userAnswer);
        verify(userAnswerMapper, times(1)).insertUserAnswer(any());
    }

    @Test
    void submitArticleAnswerShouldIgnoreIncompleteOptionalRecommendations() {
        Question question = articleQuestion(100L);
        QuestionAnswer answer = answer(200L, "先週、友人と京都へ旅行しました。\n\n天気はよくありませんでしたが、楽しく過ごしました。");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L))
                .thenReturn(List.of(tag(3L, "GENRE", "NARRATIVE", "叙事文")));
        when(userAnswerMapper.insertUserAnswer(any())).thenAnswer(invocation -> {
            UserAnswer userAnswer = invocation.getArgument(0);
            userAnswer.setId(303L);
            return 1;
        });
        when(aiAnswerScoringClient.scoreAnswer(any(), any(), any(), anyList(), anyList()))
                .thenReturn(validArticleReviewJson()
                        .replace("\"totalScore\": 86.25", "\"totalScore\": null")
                        .replace("\"sourceText\": \"上周，我和朋友去了京都旅行。\"",
                                "\"sourceText\": \"AI 未原样复制的中文\"")
                        .replace("\"referenceText\": \"先週、友人と京都へ旅行しました。\"",
                                "\"referenceText\": \"AI 未原样复制的日文\"")
                        .replace("\"revisedText\": \"先週、友人と京都へ旅行しました。\"",
                                "\"revisedText\": \"AI 自行改写的修订句\"")
                        .replace("\"revisionSuggestions\": [\"保持全文敬体和时态一致。\"]",
                                "\"revisionSuggestions\": [\"保持全文敬体和时态一致。\", \"\"]")
                        .replace("\"errorAnalysis\": []", """
                                "errorAnalysis": [
                                  {
                                    "errorTypeCode": "UNNATURAL_EXPRESSION",
                                    "original": "用户答案中不存在的片段",
                                    "issue": "",
                                    "suggestion": "",
                                    "severity": "MEDIUM",
                                    "suggestedUserErrorTypeName": "",
                                    "suggestedUserErrorTypeDescription": ""
                                  }
                                ]
                                """)
                        .replace("\"recommendedExpressions\": []", """
                                "recommendedExpressions": [
                                  {
                                    "expression": "",
                                    "usage": "",
                                    "formality": "POLITE",
                                    "note": ""
                                  }
                                ]
                                """));

        AnswerReviewVO review = questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest("先週、友達と京都へ旅行しました。天気は悪かったですが、楽しく過ごしました。")
        );

        assertThat(review.revisionSuggestions()).containsExactly("保持全文敬体和时态一致。");
        assertThat(review.recommendedExpressions()).isEmpty();
        assertThat(review.errorAnalysis()).isEmpty();
        assertThat(review.totalScore()).isEqualByComparingTo("86.25");
        assertThat(review.sentenceReviews().getFirst().sourceText())
                .isEqualTo("上周，我和朋友去了京都旅行。");
        assertThat(review.sentenceReviews().getFirst().referenceText())
                .isEqualTo("先週、友人と京都へ旅行しました。");
        assertThat(review.sentenceReviews().getFirst().revisedText())
                .isEqualTo("先週、友人と京都へ旅行しました。");
        verify(userAnswerMapper, times(1)).insertUserAnswer(any());
    }

    @Test
    void submitAnswerShouldNotSaveAnswerWhenAiReviewIsInvalid() {
        Question question = question(100L);
        QuestionAnswer answer = answer(200L, "今日の午後、銀行へ振り込みに行きます。");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L)).thenReturn(List.of());
        when(aiAnswerScoringClient.scoreAnswer(any(), any(), any(), anyList(), anyList())).thenReturn("""
                {
                  "review": {
                    "scores": {
                      "grammarVocabularyScore": 101,
                      "naturalFluencyScore": 78,
                      "scenarioAdaptationScore": 80,
                      "informationCompletenessScore": 86
                    },
                    "totalScore": 86.25,
                    "overallComment": "总评",
                    "comments": {
                      "grammarComment": "语法评价",
                      "vocabularyComment": "词汇评价",
                      "naturalnessComment": "自然度评价",
                      "scenarioComment": "场景评价"
                    },
                    "errorAnalysis": [],
                    "revisionSuggestions": [],
                    "recommendedExpressions": []
                  }
                }
                """);

        assertThatThrownBy(() -> questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest("今日の午後、銀行に送金をしに行きます。")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("grammarVocabularyScore");

        verify(userAnswerMapper, never()).insertUserAnswer(any());
        verify(userAnswerMapper, never()).updateReviewed(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitAnswerShouldNotSaveAnswerWhenAiApiCallFails() {
        Question question = question(100L);
        QuestionAnswer answer = answer(200L, "今日の午後、銀行へ振り込みに行きます。");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L)).thenReturn(List.of());
        when(aiAnswerScoringClient.scoreAnswer(any(), any(), any(), anyList(), anyList()))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 评分服务调用失败"));

        assertThatThrownBy(() -> questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest("今日の午後、銀行に送金をしに行きます。")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 评分服务调用失败");

        verify(userAnswerMapper, never()).insertUserAnswer(any());
        verify(userAnswerMapper, never()).updateReviewed(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    private AiQuestionGenerationRequest request() {
        return new AiQuestionGenerationRequest(
                1,
                "N4",
                3,
                List.of("DAILY_LIFE_WEATHER"),
                List.of("FUNCTION_PROPOSE_PLAN"),
                List.of(),
                "偏口语"
        );
    }

    private AiQuestionGenerationRequest requestWithExcludedSourceText() {
        return new AiQuestionGenerationRequest(
                1,
                "N4",
                3,
                List.of("DAILY_LIFE_WEATHER"),
                List.of("FUNCTION_PROPOSE_PLAN"),
                List.of("已经生成并保存的题目。"),
                "偏口语"
        );
    }

    private QuestionCreateRequest createRequest() {
        return new QuestionCreateRequest(
                "TRANSLATION_ZH_TO_JA",
                "我今天下午要去银行办理转账。",
                "日常生活中说明下午的计划。",
                "N4",
                3,
                "予定を表す表現",
                true,
                false,
                false,
                List.of("FINANCE_BANK", "FUNCTION_EXPRESS_PLAN"),
                List.of(
                        new QuestionAnswerRequest(
                                "今日の午後、銀行へ振り込みに行きます。",
                                "STANDARD",
                                true,
                                0
                        ),
                        new QuestionAnswerRequest(
                                "今日の午後、銀行に振り込みをしに行きます。",
                                "REFERENCE",
                                false,
                                1
                        )
                )
        );
    }

    private QuestionCreateRequest englishCreateRequest() {
        return new QuestionCreateRequest(
                "TRANSLATION_EN_TO_JA",
                "I need to transfer money at the bank this afternoon.",
                "Explain an afternoon plan in an everyday situation.",
                "N4",
                3,
                "予定を表す表現",
                true,
                false,
                false,
                List.of("FINANCE_BANK"),
                List.of(new QuestionAnswerRequest(
                        "今日の午後、銀行へ振り込みに行きます。",
                        "STANDARD",
                        true,
                        0
                ))
        );
    }

    private QuestionCreateRequest articleCreateRequest(String questionType, String sourceText, String answerText) {
        return new QuestionCreateRequest(
                questionType,
                sourceText,
                "日常生活的简短叙事文。",
                "N4",
                3,
                "时态和篇章衔接",
                false,
                false,
                false,
                List.of("NARRATIVE"),
                List.of(new QuestionAnswerRequest(
                        answerText,
                        "STANDARD",
                        true,
                        0
                ))
        );
    }

    private QuestionUpdateRequest updateRequest() {
        return new QuestionUpdateRequest(
                "我今天下午要去银行办理转账。",
                "日常生活中说明下午的计划。",
                "N4",
                3,
                "予定を表す表現",
                true,
                false,
                false,
                List.of("FINANCE_BANK"),
                List.of(new QuestionAnswerRequest(
                        "今日の午後、銀行へ振り込みに行きます。",
                        "STANDARD",
                        true,
                        0
                ))
        );
    }

    private QuestionUpdateRequest englishArticleUpdateRequest() {
        return new QuestionUpdateRequest(
                "I visited the library.\n\nThen I read a book at home.",
                "A short narrative about reading.",
                "N4",
                3,
                "時制と文章のつながり",
                false,
                false,
                false,
                List.of("NARRATIVE"),
                List.of(new QuestionAnswerRequest(
                        "図書館に行きました。\n\nその後、家で本を読みました。",
                        "STANDARD",
                        true,
                        0
                ))
        );
    }

    private QuestionCreateRequest createRequestWithoutSceneTag() {
        return new QuestionCreateRequest(
                "TRANSLATION_ZH_TO_JA",
                "我今天下午要去银行办理转账。",
                "日常生活中说明下午的计划。",
                "N4",
                3,
                "予定を表す表現",
                true,
                false,
                false,
                List.of("FUNCTION_EXPRESS_PLAN"),
                List.of(new QuestionAnswerRequest(
                        "今日の午後、銀行へ振り込みに行きます。",
                        "STANDARD",
                        true,
                        0
                ))
        );
    }

    private String validAiJson() {
        return """
                {
                  "questions": [
                    {
                      "questionType": "TRANSLATION_ZH_TO_JA",
                      "sourceText": "如果明天下雨，我们就在家学习吧。",
                      "contextText": "朋友之间讨论明天的安排。",
                      "level": "N4",
                      "difficulty": 3,
                      "grammarPoint": "条件表現「たら」",
                      "spoken": true,
                      "business": false,
                      "exam": false,
                      "tagCodes": ["DAILY_LIFE_WEATHER", "FUNCTION_PROPOSE_PLAN"],
                      "answers": [
                        {
                          "answerText": "明日雨が降ったら、家で勉強しましょう。",
                          "answerType": "STANDARD",
                          "primaryAnswer": true,
                          "sortOrder": 0
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String validArticleJson(String seed) {
        return """
                {
                  "blueprint": {
                    "seed": "%s",
                    "coreConcept": "计划之外的旅行经历",
                    "roles": {
                      "subject": "结伴出行的朋友",
                      "setting": "郊外小镇",
                      "experience": "行程意外改变",
                      "changeOrInsight": "重新看待计划外经历"
                    }
                  },
                  "article": {
                    "questionType": "TRANSLATION_ZH_TO_JA_ARTICLE",
                    "contextText": "叙事文，使用自然连贯的书面语。",
                    "level": "N3",
                    "difficulty": 3,
                    "grammarPoint": "郊外：郊外（こうがい）\\n避雨：雨宿り（あまやどり）\\n老街：古い町並み（ふるいまちなみ）",
                    "spoken": false,
                    "business": false,
                    "exam": false,
                    "sentences": [
                      {"index":0,"chineseText":"上周末，我和朋友决定去郊外的一座小镇旅行。","japaneseReference":"先週末、友人と郊外の小さな町へ旅行することにしました。"},
                      {"index":1,"chineseText":"我们原本计划乘早班电车出发，却因为看错时间错过了车。","japaneseReference":"朝早い電車で出発する予定でしたが、時間を見間違えて乗り遅れました。"},
                      {"index":2,"chineseText":"下一班车要等一个小时，所以我们在车站附近吃了早餐。","japaneseReference":"次の電車まで一時間あったので、駅の近くで朝食を取りました。"},
                      {"index":3,"chineseText":"到达小镇时，天空突然下起了大雨，我们只好先找地方避雨。","japaneseReference":"町に着くと急に大雨が降り始めたため、まず雨宿りできる場所を探しました。"},
                      {"index":4,"chineseText":"我们跑进一家旧书店，店主热情地介绍了当地的历史和老街。","japaneseReference":"古い本屋に駆け込むと、店主が町の歴史と古い町並みを親切に紹介してくれました。"}
                    ]
                  }
                }
                """.formatted(seed);
    }

    private String articleJsonWithChineseLength(String seed, int articleLength) {
        String sourceText = "中".repeat(articleLength - 1) + "。";
        return """
                {
                  "blueprint": {
                    "seed": "%s",
                    "coreConcept": "保持内容不变并调整表达难度",
                    "roles": {
                      "subject": "文章主体",
                      "setting": "文章背景",
                      "experience": "主要经历",
                      "changeOrInsight": "可能的发展"
                    }
                  },
                  "article": {
                    "questionType": "TRANSLATION_ZH_TO_JA_ARTICLE",
                    "contextText": "叙事文，使用自然连贯的书面语。",
                    "level": "N3",
                    "difficulty": 3,
                    "grammarPoint": "文章：文章（ぶんしょう）",
                    "spoken": false,
                    "business": false,
                    "exam": false,
                    "sentences": [
                      {
                        "index": 0,
                        "chineseText": "%s",
                        "japaneseReference": "内容に対応する自然な日本語の参考文です。"
                      }
                    ]
                  }
                }
                """.formatted(seed, sourceText);
    }

    private String articleJsonWithEnglishWordCount(String seed, int wordCount) {
        String sourceText = ("word ".repeat(wordCount)).trim() + ".";
        return """
                {
                  "blueprint": {
                    "seed": "%s",
                    "coreConcept": "Keep the content and adjust only its language",
                    "roles": {
                      "subject": "article subject",
                      "setting": "article setting",
                      "experience": "main experience",
                      "changeOrInsight": "possible development"
                    }
                  },
                  "article": {
                    "questionType": "TRANSLATION_EN_TO_JA_ARTICLE",
                    "contextText": "A narrative written in a natural style.",
                    "level": "N3",
                    "difficulty": 3,
                    "grammarPoint": "article: 文章（ぶんしょう）",
                    "spoken": false,
                    "business": false,
                    "exam": false,
                    "sentences": [
                      {
                        "index": 0,
                        "chineseText": "%s",
                        "japaneseReference": "内容に対応する自然な日本語の参考文です。"
                      }
                    ]
                  }
                }
                """.formatted(seed, sourceText);
    }

    private List<Float> vector() {
        Float[] values = new Float[768];
        java.util.Arrays.fill(values, 0.1f);
        return java.util.Arrays.asList(values);
    }

    private String validReviewJson() {
        return """
                {
                  "review": {
                    "scores": {
                      "grammarVocabularyScore": 82,
                      "naturalFluencyScore": 78,
                      "scenarioAdaptationScore": 80,
                      "informationCompletenessScore": 86
                    },
                    "totalScore": 1,
                    "overallComment": "整体意思基本准确，语法和用词可以再自然一些。",
                    "comments": {
                      "grammarComment": "句子结构基本正确，助词使用需要继续注意。",
                      "vocabularyComment": "核心词汇能表达原意，但部分搭配不够自然。",
                      "naturalnessComment": "表达可以理解，不过和日语母语者常用说法仍有距离。",
                      "scenarioComment": "语气基本符合题目场景，敬体表达还可以更稳定。"
                    },
                    "errorAnalysis": [
                      {
                        "errorTypeCode": "UNNATURAL_EXPRESSION",
                        "original": "今日の午後、銀行に送金をしに行きます。",
                        "issue": "表达能传达大意，但整体不够像自然日语。",
                        "suggestion": "参考标准答案调整助词和动词搭配。",
                        "severity": "MEDIUM",
                        "suggestedUserErrorTypeName": "不自然表达",
                        "suggestedUserErrorTypeDescription": "使用符合日语习惯的表达。"
                      }
                    ],
                    "revisionSuggestions": [
                      "先确认中文原文中的时间、动作和对象是否完整保留。"
                    ],
                    "recommendedExpressions": [
                      {
                        "expression": "今日の午後、銀行へ振り込みに行きます。",
                        "usage": "适合本题语境的基础推荐表达。",
                        "formality": "POLITE",
                        "note": "可以作为当前题目的优先记忆表达。"
                      }
                    ]
                  }
                }
                """;
    }

    private String validArticleReviewJson() {
        return """
                {
                  "review": {
                    "scores": {
                      "grammarVocabularyScore": 85,
                      "naturalFluencyScore": 82,
                      "scenarioAdaptationScore": 88,
                      "informationCompletenessScore": 90
                    },
                    "totalScore": 86.25,
                    "overallComment": "全文信息完整，时态和语体一致，句间衔接基本自然。",
                    "comments": {
                      "grammarComment": "语法与用词基本准确。",
                      "vocabularyComment": "词汇能够表达原意。",
                      "naturalnessComment": "合并句仍保持了自然衔接。",
                      "scenarioComment": "叙事文语体一致。"
                    },
                    "sentenceReviews": [
                      {
                        "sourceSegmentIndex": 0,
                        "sourceText": "上周，我和朋友去了京都旅行。",
                        "referenceText": "先週、友人と京都へ旅行しました。",
                        "answerExcerpt": "先週、友達と京都へ旅行しました。天気は悪かったですが、楽しく過ごしました。",
                        "revisedText": "先週、友人と京都へ旅行しました。",
                        "comment": "意思准确，友達也可接受。"
                      },
                      {
                        "sourceSegmentIndex": 1,
                        "sourceText": "天气不太好，但我们过得很愉快。",
                        "referenceText": "天気はよくありませんでしたが、楽しく過ごしました。",
                        "answerExcerpt": "先週、友達と京都へ旅行しました。天気は悪かったですが、楽しく過ごしました。",
                        "revisedText": "天気はよくありませんでしたが、楽しく過ごしました。",
                        "comment": "合并作答不影响语义对应。"
                      }
                    ],
                    "errorAnalysis": [],
                    "revisionSuggestions": ["保持全文敬体和时态一致。"],
                    "recommendedExpressions": []
                  }
                }
                """;
    }

    private Question question(Long id) {
        Question question = new Question();
        question.setId(id);
        question.setQuestionType("TRANSLATION_ZH_TO_JA");
        question.setSourceText("我今天下午要去银行办理转账。");
        question.setContextText("日常生活中说明下午的计划。");
        question.setLevel("N4");
        question.setDifficulty(3);
        question.setGrammarPoint("予定を表す表現");
        question.setSpoken(true);
        question.setBusiness(false);
        question.setExam(false);
        question.setSourceType("AI");
        question.setEnabled(true);
        question.setDeleted(false);
        return question;
    }

    private Question articleQuestion(Long id) {
        Question question = question(id);
        question.setQuestionType("TRANSLATION_ZH_TO_JA_ARTICLE");
        question.setSourceText("上周，我和朋友去了京都旅行。\n\n天气不太好，但我们过得很愉快。");
        question.setContextText("AI 原创叙事文。使用自然连贯的书面语。");
        question.setGrammarPoint("时态和篇章衔接");
        question.setSpoken(false);
        return question;
    }

    private QuestionAnswer answer(Long id, String text) {
        QuestionAnswer answer = new QuestionAnswer();
        answer.setId(id);
        answer.setQuestionId(100L);
        answer.setAnswerText(text);
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        return answer;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUserCode("LOCAL_DEFAULT");
        user.setNickname("本地用户");
        user.setUserType("LOCAL");
        user.setEnabled(true);
        user.setDeleted(false);
        return user;
    }

    private Tag tag(Long id, String tagType, String code, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setTagType(tagType);
        if ("SCENE".equals(tagType) || "FUNCTION".equals(tagType)) {
            tag.setParentId(1000L + id);
        }
        tag.setCode(code);
        tag.setName(name);
        tag.setDescription(name + "标签");
        tag.setSortOrder(id.intValue());
        tag.setEnabled(true);
        tag.setDeleted(false);
        return tag;
    }

    private AiErrorTypeOptionDTO errorTypeOption() {
        return new AiErrorTypeOptionDTO(
                9L,
                "UNNATURAL_EXPRESSION",
                "不自然表达",
                "表达不符合日语习惯",
                "LEXICAL_EXPRESSION",
                "词汇与表达"
        );
    }

    private QuestionTagRow tagRow(Long questionId, Long id, String tagType, String code, String name) {
        QuestionTagRow tag = new QuestionTagRow();
        tag.setQuestionId(questionId);
        tag.setId(id);
        tag.setTagType(tagType);
        tag.setCode(code);
        tag.setName(name);
        tag.setDescription(name + "标签");
        tag.setSortOrder(id.intValue());
        return tag;
    }
}
