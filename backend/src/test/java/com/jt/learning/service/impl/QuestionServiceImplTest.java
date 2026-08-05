package com.jt.learning.service.impl;

import com.jt.learning.dto.AiAnswerScoringRequest;
import com.jt.learning.dto.AiErrorTypeOptionDTO;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.QuestionAnswerRequest;
import com.jt.learning.dto.QuestionCreateRequest;
import com.jt.learning.dto.QuestionEnabledRequest;
import com.jt.learning.dto.QuestionQueryRequest;
import com.jt.learning.dto.QuestionTagRow;
import com.jt.learning.dto.QuestionUpdateRequest;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.entity.User;
import com.jt.learning.entity.UserAnswer;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.ErrorTypeMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.mapper.UserAnswerMapper;
import com.jt.learning.mapper.UserMapper;
import com.jt.learning.service.AiAnswerScoringClient;
import com.jt.learning.service.AiQuestionClient;
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
    void submitAnswerShouldMarkFailedWhenAiReviewIsInvalid() {
        Question question = question(100L);
        QuestionAnswer answer = answer(200L, "今日の午後、銀行へ振り込みに行きます。");
        when(questionMapper.selectActiveQuestionById(100L)).thenReturn(question);
        when(questionAnswerMapper.selectActiveAnswersByQuestionId(100L)).thenReturn(List.of(answer));
        when(userMapper.selectEnabledUserByCode("LOCAL_DEFAULT")).thenReturn(user(10L));
        when(tagMapper.selectEnabledTagsByQuestionId(100L)).thenReturn(List.of());
        when(userAnswerMapper.insertUserAnswer(any())).thenAnswer(invocation -> {
            UserAnswer userAnswer = invocation.getArgument(0);
            userAnswer.setId(300L);
            return 1;
        });
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

        verify(userAnswerMapper).updateFailed(eq(300L), any(LocalDateTime.class));
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
