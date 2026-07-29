package com.jt.learning.service.impl;

import com.jt.learning.dto.AiGeneratedQuestionDTO;
import com.jt.learning.dto.AiQuestionAnswerDTO;
import com.jt.learning.dto.AiQuestionGenerationRequest;
import com.jt.learning.dto.AiQuestionGenerationResponseDTO;
import com.jt.learning.dto.AiQuestionTagOptionDTO;
import com.jt.learning.entity.Question;
import com.jt.learning.entity.QuestionAnswer;
import com.jt.learning.entity.Tag;
import com.jt.learning.exception.BusinessException;
import com.jt.learning.exception.ErrorCode;
import com.jt.learning.mapper.QuestionAnswerMapper;
import com.jt.learning.mapper.QuestionMapper;
import com.jt.learning.mapper.QuestionTagMapper;
import com.jt.learning.mapper.TagMapper;
import com.jt.learning.service.AiQuestionClient;
import com.jt.learning.service.AiQuestionPrompt;
import com.jt.learning.service.QuestionService;
import com.jt.learning.vo.QuestionAnswerVO;
import com.jt.learning.vo.QuestionVO;
import com.jt.learning.vo.TagVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class QuestionServiceImpl implements QuestionService {

    private static final String QUESTION_TYPE = "TRANSLATION_ZH_TO_JA";
    private static final String SOURCE_TYPE_AI = "AI";
    private static final String TAG_TYPE_SCENE = "SCENE";
    private static final String TAG_TYPE_FUNCTION = "FUNCTION";
    private static final String ANSWER_TYPE_STANDARD = "STANDARD";
    private static final String ANSWER_TYPE_REFERENCE = "REFERENCE";

    private static final Set<String> VALID_ANSWER_TYPES = Set.of(ANSWER_TYPE_STANDARD, ANSWER_TYPE_REFERENCE);
    private static final Pattern CHINESE_PATTERN = Pattern.compile(".*[\\u4e00-\\u9fff].*");
    private static final Pattern JAPANESE_TEXT_PATTERN = Pattern.compile(".*[\\u3040-\\u30ff\\u4e00-\\u9fff].*");

    private final TagMapper tagMapper;
    private final QuestionMapper questionMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final QuestionTagMapper questionTagMapper;
    private final AiQuestionPromptBuilder promptBuilder;
    private final AiQuestionClient aiQuestionClient;
    private final ObjectMapper objectMapper;

    public QuestionServiceImpl(
            TagMapper tagMapper,
            QuestionMapper questionMapper,
            QuestionAnswerMapper questionAnswerMapper,
            QuestionTagMapper questionTagMapper,
            AiQuestionPromptBuilder promptBuilder,
            AiQuestionClient aiQuestionClient,
            ObjectMapper objectMapper
    ) {
        this.tagMapper = tagMapper;
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionTagMapper = questionTagMapper;
        this.promptBuilder = promptBuilder;
        this.aiQuestionClient = aiQuestionClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public List<QuestionVO> generateQuestionsByAi(AiQuestionGenerationRequest request) {
        List<Tag> sceneTags = loadCandidateTags(TAG_TYPE_SCENE, request.sceneTagCodes());
        List<Tag> functionTags = loadCandidateTags(TAG_TYPE_FUNCTION, request.functionTagCodes());
        if (sceneTags.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "没有可用场景标签");
        }

        List<AiQuestionTagOptionDTO> sceneTagOptions = toTagOptions(sceneTags);
        List<AiQuestionTagOptionDTO> functionTagOptions = toTagOptions(functionTags);
        AiQuestionPrompt prompt = promptBuilder.build(request, sceneTagOptions, functionTagOptions);
        AiQuestionGenerationResponseDTO aiResponse = parseAiResponse(
                aiQuestionClient.generateQuestions(prompt, request, sceneTagOptions, functionTagOptions)
        );

        List<ValidatedGeneratedQuestion> validatedQuestions = validateAiResponse(
                request,
                aiResponse,
                toTagMap(sceneTags),
                toTagMap(functionTags)
        );

        List<QuestionVO> savedQuestions = new ArrayList<>();
        for (ValidatedGeneratedQuestion validatedQuestion : validatedQuestions) {
            savedQuestions.add(saveQuestion(validatedQuestion));
        }
        return savedQuestions;
    }

    private List<Tag> loadCandidateTags(String tagType, List<String> requestedCodes) {
        List<String> codes = normalizeCodes(requestedCodes);
        if (codes.isEmpty()) {
            return tagMapper.selectEnabledTagsByType(tagType);
        }

        List<Tag> tags = tagMapper.selectEnabledTagsByCodes(tagType, codes);
        Set<String> foundCodes = tags.stream()
                .map(Tag::getCode)
                .collect(Collectors.toSet());
        List<String> missingCodes = codes.stream()
                .filter(code -> !foundCodes.contains(code))
                .toList();
        if (!missingCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, tagType + " 标签 code 不存在或未启用: " + missingCodes);
        }
        return tags;
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private List<AiQuestionTagOptionDTO> toTagOptions(List<Tag> tags) {
        return tags.stream()
                .map(tag -> new AiQuestionTagOptionDTO(tag.getCode(), tag.getName(), tag.getDescription()))
                .toList();
    }

    private Map<String, Tag> toTagMap(List<Tag> tags) {
        return tags.stream()
                .collect(Collectors.toMap(Tag::getCode, tag -> tag, (left, right) -> left, LinkedHashMap::new));
    }

    private AiQuestionGenerationResponseDTO parseAiResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出为空");
        }

        try {
            JsonNode root = objectMapper.readTree(aiContent);
            validateRootJson(root);
            return objectMapper.treeToValue(root, AiQuestionGenerationResponseDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出不是合法 JSON");
        }
    }

    private void validateRootJson(JsonNode root) {
        if (!root.isObject()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出 JSON 顶层必须是对象");
        }

        List<String> fields = new ArrayList<>(root.propertyNames());
        if (!fields.equals(List.of("questions"))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出 JSON 顶层只能包含 questions 字段");
        }
        if (!root.get("questions").isArray()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出 questions 必须是数组");
        }
    }

    private List<ValidatedGeneratedQuestion> validateAiResponse(
            AiQuestionGenerationRequest request,
            AiQuestionGenerationResponseDTO aiResponse,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> functionTagMap
    ) {
        if (aiResponse.questions() == null || aiResponse.questions().size() != request.questionCount()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出题目数量不一致");
        }

        Map<String, Tag> allowedTagMap = new LinkedHashMap<>();
        allowedTagMap.putAll(sceneTagMap);
        allowedTagMap.putAll(functionTagMap);

        List<ValidatedGeneratedQuestion> validatedQuestions = new ArrayList<>();
        for (int i = 0; i < aiResponse.questions().size(); i++) {
            AiGeneratedQuestionDTO question = aiResponse.questions().get(i);
            validatedQuestions.add(validateQuestion(request, question, sceneTagMap, allowedTagMap, i));
        }
        return validatedQuestions;
    }

    private ValidatedGeneratedQuestion validateQuestion(
            AiQuestionGenerationRequest request,
            AiGeneratedQuestionDTO question,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> allowedTagMap,
            int index
    ) {
        String prefix = "第 " + (index + 1) + " 道题";
        if (question == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + "不能为空");
        }
        if (!QUESTION_TYPE.equals(question.questionType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " questionType 不合法");
        }
        validateRequiredText(question.sourceText(), prefix + " sourceText 不能为空");
        if (!CHINESE_PATTERN.matcher(question.sourceText()).matches()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " sourceText 必须包含中文");
        }
        validateRequiredText(question.contextText(), prefix + " contextText 不能为空");
        if (!request.level().equals(question.level())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " level 与请求不一致");
        }
        if (!request.difficulty().equals(question.difficulty())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " difficulty 与请求不一致");
        }
        validateRequiredText(question.grammarPoint(), prefix + " grammarPoint 不能为空");
        if (question.spoken() == null || question.business() == null || question.exam() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " spoken、business、exam 必须是布尔值");
        }

        List<Tag> selectedTags = validateTagCodes(question.tagCodes(), sceneTagMap, allowedTagMap, prefix);
        validateAnswers(question.answers(), prefix);
        return new ValidatedGeneratedQuestion(question, selectedTags);
    }

    private void validateRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private List<Tag> validateTagCodes(
            List<String> tagCodes,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> allowedTagMap,
            String prefix
    ) {
        List<String> normalizedCodes = normalizeCodes(tagCodes);
        if (normalizedCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " tagCodes 不能为空");
        }

        boolean hasSceneTag = normalizedCodes.stream().anyMatch(sceneTagMap::containsKey);
        if (!hasSceneTag) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " 至少需要 1 个场景标签");
        }

        List<Tag> selectedTags = new ArrayList<>();
        for (String tagCode : normalizedCodes) {
            Tag tag = allowedTagMap.get(tagCode);
            if (tag == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " 存在非法标签 code: " + tagCode);
            }
            selectedTags.add(tag);
        }
        return selectedTags;
    }

    private void validateAnswers(List<AiQuestionAnswerDTO> answers, String prefix) {
        if (answers == null || answers.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answers 不能为空");
        }

        int primaryStandardCount = 0;
        Set<String> answerTexts = new LinkedHashSet<>();
        for (AiQuestionAnswerDTO answer : answers) {
            if (answer == null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answer 不能为空");
            }
            validateAnswer(answer, prefix);
            if (!answerTexts.add(answer.answerText().trim())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answerText 不能重复");
            }
            if (ANSWER_TYPE_STANDARD.equals(answer.answerType()) && Boolean.TRUE.equals(answer.primaryAnswer())) {
                primaryStandardCount++;
            }
        }

        if (primaryStandardCount != 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " 必须有且只有 1 个主标准答案");
        }
    }

    private void validateAnswer(AiQuestionAnswerDTO answer, String prefix) {
        validateRequiredText(answer.answerText(), prefix + " answerText 不能为空");
        if (!JAPANESE_TEXT_PATTERN.matcher(answer.answerText()).matches()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answerText 必须包含日语假名或汉字");
        }
        if (!VALID_ANSWER_TYPES.contains(answer.answerType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " answerType 不合法");
        }
        if (answer.primaryAnswer() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " primaryAnswer 不能为空");
        }
        if (answer.sortOrder() == null || answer.sortOrder() < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " sortOrder 不合法");
        }

        if (ANSWER_TYPE_STANDARD.equals(answer.answerType())) {
            if (!Boolean.TRUE.equals(answer.primaryAnswer()) || answer.sortOrder() != 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " STANDARD 主答案规则不合法");
            }
            return;
        }

        if (Boolean.TRUE.equals(answer.primaryAnswer())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " REFERENCE 答案不能是主答案");
        }
    }

    private QuestionVO saveQuestion(ValidatedGeneratedQuestion validatedQuestion) {
        AiGeneratedQuestionDTO generatedQuestion = validatedQuestion.question();
        LocalDateTime now = LocalDateTime.now();

        Question question = new Question();
        question.setQuestionType(QUESTION_TYPE);
        question.setSourceText(generatedQuestion.sourceText().trim());
        question.setContextText(generatedQuestion.contextText().trim());
        question.setLevel(generatedQuestion.level());
        question.setDifficulty(generatedQuestion.difficulty());
        question.setGrammarPoint(generatedQuestion.grammarPoint().trim());
        question.setSpoken(generatedQuestion.spoken());
        question.setBusiness(generatedQuestion.business());
        question.setExam(generatedQuestion.exam());
        question.setSourceType(SOURCE_TYPE_AI);
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insertQuestion(question);

        List<QuestionAnswer> answers = saveAnswers(question.getId(), generatedQuestion.answers(), now);
        for (Tag tag : validatedQuestion.tags()) {
            questionTagMapper.insertQuestionTag(question.getId(), tag.getId());
        }

        return toQuestionVO(question, validatedQuestion.tags(), answers);
    }

    private List<QuestionAnswer> saveAnswers(Long questionId, List<AiQuestionAnswerDTO> answerDTOs, LocalDateTime now) {
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
        return answers;
    }

    private QuestionVO toQuestionVO(Question question, List<Tag> tags, List<QuestionAnswer> answers) {
        return new QuestionVO(
                question.getId(),
                question.getQuestionType(),
                question.getSourceText(),
                question.getContextText(),
                question.getLevel(),
                question.getDifficulty(),
                question.getGrammarPoint(),
                question.getSpoken(),
                question.getBusiness(),
                question.getExam(),
                question.getSourceType(),
                question.getEnabled(),
                tags.stream().map(this::toTagVO).toList(),
                answers.stream().map(this::toAnswerVO).toList(),
                question.getCreatedAt(),
                question.getUpdatedAt()
        );
    }

    private TagVO toTagVO(Tag tag) {
        return new TagVO(
                tag.getId(),
                tag.getTagType(),
                tag.getParentId(),
                tag.getCode(),
                tag.getName(),
                tag.getDescription(),
                tag.getSortOrder()
        );
    }

    private QuestionAnswerVO toAnswerVO(QuestionAnswer answer) {
        return new QuestionAnswerVO(
                answer.getId(),
                answer.getAnswerText(),
                answer.getAnswerType(),
                answer.getPrimaryAnswer(),
                answer.getSortOrder()
        );
    }

    private record ValidatedGeneratedQuestion(
            AiGeneratedQuestionDTO question,
            List<Tag> tags
    ) {
    }
}
