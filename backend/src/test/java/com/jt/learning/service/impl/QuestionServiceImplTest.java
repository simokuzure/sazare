package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiArticleGenerationRequest;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.QuestionAnswerRequest;
import com.jt.learning.dto.QuestionCreateRequest;
import com.jt.learning.dto.QuestionEnabledRequest;
import com.jt.learning.dto.QuestionEmbeddingMatch;
import com.jt.learning.dto.QuestionQueryRequest;
import com.jt.learning.dto.QuestionTagRow;
import com.jt.learning.dto.QuestionUpdateRequest;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.ai.AiAnswerScoringClient;
import com.jt.learning.service.ai.AiQuestionClient;
import com.jt.learning.service.ai.prompt.AiAnswerScoringPromptBuilder;
import com.jt.learning.service.ai.prompt.AiQuestionPromptBuilder;
import com.jt.learning.service.ai.validation.AiErrorAnalysisValidator;
import com.jt.learning.service.question.QuestionEmbeddingService;
import com.jt.learning.vo.AnswerReviewVO;
import com.jt.learning.vo.PageVO;
import com.jt.learning.vo.QuestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceImplTest {

    private TagMapper tagMapper;
    private QuestionMapper questionMapper;
    private QuestionAnswerMapper questionAnswerMapper;
    private QuestionTagMapper questionTagMapper;
    private UserMapper userMapper;
    private UserAnswerMapper userAnswerMapper;
    private ErrorTypeMapper errorTypeMapper;
    private AiQuestionClient aiQuestionClient;
    private AiAnswerScoringClient aiAnswerScoringClient;
    private QuestionEmbeddingService questionEmbeddingService;
    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        questionMapper = mock(QuestionMapper.class);
        questionAnswerMapper = mock(QuestionAnswerMapper.class);
        questionTagMapper = mock(QuestionTagMapper.class);
        userMapper = mock(UserMapper.class);
        userAnswerMapper = mock(UserAnswerMapper.class);
        errorTypeMapper = mock(ErrorTypeMapper.class);
        aiQuestionClient = mock(AiQuestionClient.class);
        aiAnswerScoringClient = mock(AiAnswerScoringClient.class);
        questionEmbeddingService = mock(QuestionEmbeddingService.class);

        ObjectMapper objectMapper = new ObjectMapper();
        when(errorTypeMapper.selectEnabledLeafOptions()).thenReturn(List.of(errorTypeOption()));
        questionService = new QuestionServiceImpl(
                tagMapper,
                questionMapper,
                questionAnswerMapper,
                questionTagMapper,
                userMapper,
                userAnswerMapper,
                errorTypeMapper,
                new AiQuestionPromptBuilder(objectMapper),
                aiQuestionClient,
                new AiAnswerScoringPromptBuilder(objectMapper),
                aiAnswerScoringClient,
                new AiErrorAnalysisValidator(),
                questionEmbeddingService,
                objectMapper
        );
    }

    @Test
    void generateQuestionsByAiShouldSaveValidatedQuestions() {
        Tag sceneTag = tag(1L, "SCENE", "DAILY_LIFE_WEATHER", "天气");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_PROPOSE_PLAN", "提出计划");
        when(tagMapper.selectEnabledTagsByCodes(eq("SCENE"), anyList())).thenReturn(List.of(sceneTag));
        when(tagMapper.selectEnabledTagsByCodes(eq("FUNCTION"), anyList())).thenReturn(List.of(functionTag));
        when(aiQuestionClient.generateQuestions(any(), any(), anyList(), anyList())).thenReturn(validAiJson());
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

        List<QuestionVO> questions = questionService.generateQuestionsByAi(request());

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
        verify(questionEmbeddingService).saveEmbedding(any(Question.class), anyList());
    }

    @Test
    void generateArticleByAiShouldSaveOneArticleAndHideReferenceAnswer() {
        Tag genreTag = tag(3L, "GENRE", "NARRATIVE", "叙事文");
        when(tagMapper.selectEnabledTagsByCodes("GENRE", List.of("NARRATIVE")))
                .thenReturn(List.of(genreTag));
        when(aiQuestionClient.generateArticle(any(), any())).thenReturn(validArticleJson());
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
        assertThat(question.sourceText()).contains("\n\n");
        assertThat(question.grammarPoint()).contains("郊外：郊外（こうがい）", "避雨：雨宿り（あまやどり）");
        assertThat(question.tags()).extracting("code").containsExactly("NARRATIVE");
        assertThat(question.answers()).isEmpty();
        ArgumentCaptor<QuestionAnswer> answerCaptor = ArgumentCaptor.forClass(QuestionAnswer.class);
        verify(questionAnswerMapper).insertQuestionAnswer(answerCaptor.capture());
        assertThat(answerCaptor.getValue().getAnswerText()).contains("\n\n");
        verify(questionTagMapper).insertQuestionTag(101L, 3L);
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
                .thenReturn(List.of(new QuestionEmbeddingMatch(99L, 0.95d)));

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
        when(tagMapper.selectEnabledTagsByType("SCENE")).thenReturn(List.of(sceneTag));
        when(tagMapper.selectEnabledTagsByType("FUNCTION")).thenReturn(List.of());
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
        verify(tagMapper).selectEnabledTagsByType("SCENE");
        verify(tagMapper).selectEnabledTagsByType("FUNCTION");
    }

    @Test
    void createQuestionShouldSaveManualQuestionWithTagsAndAnswers() {
        Tag sceneTag = tag(1L, "SCENE", "FINANCE_BANK", "银行");
        Tag functionTag = tag(2L, "FUNCTION", "FUNCTION_EXPRESS_PLAN", "表达计划");
        when(tagMapper.selectEnabledTagsByAnyCodes(anyList())).thenReturn(List.of(sceneTag, functionTag));
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
        verify(questionEmbeddingService).synchronizeEmbedding(any(Question.class));
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
    }

    @Test
    void updateQuestionEnabledShouldRejectDeletedQuestion() {
        when(questionMapper.updateEnabled(eq(100L), eq(false), any(LocalDateTime.class))).thenReturn(0);

        assertThatThrownBy(() -> questionService.updateQuestionEnabled(100L, new QuestionEnabledRequest(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("题目不存在或已删除");
    }

    @Test
    void deleteQuestionShouldLogicalDeleteQuestion() {
        when(questionMapper.logicalDelete(eq(100L), any(LocalDateTime.class))).thenReturn(1);

        questionService.deleteQuestion(100L);

        verify(questionMapper).logicalDelete(eq(100L), any(LocalDateTime.class));
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

    @Test
    void submitAnswerShouldRejectReviewDerivedQuestion() {
        Question question = question(100L);
        question.setSourceType("REVIEW_DERIVED");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);

        assertThatThrownBy(() -> questionService.submitAnswer(
                100L,
                new AiAnswerScoringRequest("回答")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须通过复习接口");

        verify(userAnswerMapper, never()).insertUserAnswer(any());
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

    private QuestionUpdateRequest updateRequest() {
        return new QuestionUpdateRequest(
                "TRANSLATION_ZH_TO_JA",
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

    private String validArticleJson() {
        return """
                {
                  "article": {
                    "questionType": "TRANSLATION_ZH_TO_JA_ARTICLE",
                    "contextText": "AI 原创叙事文，使用自然连贯的书面语。",
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
                      {"index":4,"chineseText":"我们跑进一家旧书店，店主热情地介绍了当地的历史和老街。","japaneseReference":"古い本屋に駆け込むと、店主が町の歴史と古い町並みを親切に紹介してくれました。"},
                      {"index":5,"chineseText":"雨停以后，我们按照他的建议慢慢参观了老街，还品尝了当地的点心。","japaneseReference":"雨がやんだ後、彼の勧めに従って古い町並みを歩き、名物のお菓子も味わいました。"},
                      {"index":6,"chineseText":"虽然行程和预想完全不同，但这些意外的经历让这次旅行更加难忘。","japaneseReference":"予定とはまったく違う旅になりましたが、思いがけない経験のおかげで忘れられない旅になりました。"}
                    ]
                  }
                }
                """;
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
