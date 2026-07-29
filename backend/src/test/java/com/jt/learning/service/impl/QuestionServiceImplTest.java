package com.jt.learning.service.impl;

import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.service.AiQuestionClient;
import com.jt.learning.vo.QuestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

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
    private AiQuestionClient aiQuestionClient;
    private QuestionServiceImpl questionService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        questionMapper = mock(QuestionMapper.class);
        questionAnswerMapper = mock(QuestionAnswerMapper.class);
        questionTagMapper = mock(QuestionTagMapper.class);
        aiQuestionClient = mock(AiQuestionClient.class);

        ObjectMapper objectMapper = new ObjectMapper();
        questionService = new QuestionServiceImpl(
                tagMapper,
                questionMapper,
                questionAnswerMapper,
                questionTagMapper,
                new AiQuestionPromptBuilder(objectMapper),
                aiQuestionClient,
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
}
