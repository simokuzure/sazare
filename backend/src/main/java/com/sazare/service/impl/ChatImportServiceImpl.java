package com.sazare.service.impl;

import com.sazare.common.TranslationDirection;
import com.sazare.dto.ChatImportConfirmationRequest;
import com.sazare.entity.Question;
import com.sazare.entity.QuestionAnswer;
import com.sazare.entity.ReviewCard;
import com.sazare.entity.User;
import com.sazare.entity.UserErrorType;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.mapper.*;
import com.sazare.service.ChatImportService;
import com.sazare.service.DictionaryCacheService;
import com.sazare.service.ReviewService;
import com.sazare.service.ai.AiChatImportClient;
import com.sazare.service.ai.prompt.AiChatImportPromptBuilder;
import com.sazare.service.ai.validation.ChatImportAiResponseValidator;
import com.sazare.util.ChatMarkdownParser;
import com.sazare.util.ChatReviewTextValidator;
import com.sazare.vo.ChatImportAnalysisVO;
import com.sazare.vo.ChatImportConfirmationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatImportServiceImpl implements ChatImportService {
    private static final Logger log = LoggerFactory.getLogger(ChatImportServiceImpl.class);
    private static final Set<String> UNSUPPORTED_CODES = Set.of(
            "OMISSION", "MISTRANSLATION", "ADDITION", "FALSE_FRIEND", "CHINESE_CALQUE");
    private final ChatMarkdownParser parser;
    private final DictionaryCacheService dictionary;
    private final AiChatImportPromptBuilder promptBuilder;
    private final AiChatImportClient client;
    private final ChatImportAiResponseValidator validator;
    private final UserMapper users;
    private final ErrorTypeMapper errorTypes;
    private final UserErrorTypeMapper userTypes;
    private final QuestionMapper questions;
    private final QuestionAnswerMapper answers;
    private final ReviewCardMapper cards;
    private final ReviewService reviews;

    public ChatImportServiceImpl(ChatMarkdownParser parser, DictionaryCacheService dictionary,
            AiChatImportPromptBuilder promptBuilder, AiChatImportClient client, ChatImportAiResponseValidator validator,
            UserMapper users, ErrorTypeMapper errorTypes, UserErrorTypeMapper userTypes,
            QuestionMapper questions, QuestionAnswerMapper answers, ReviewCardMapper cards, ReviewService reviews) {
        this.parser = parser;
        this.dictionary = dictionary;
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.validator = validator;
        this.users = users;
        this.errorTypes = errorTypes;
        this.userTypes = userTypes;
        this.questions = questions;
        this.answers = answers;
        this.cards = cards;
        this.reviews = reviews;
    }

    @Override
    public ChatImportAnalysisVO analyze(byte[] markdown, String learningMode) {
        requireUser();
        String mode = TranslationDirection.fromLearningMode(learningMode).learningMode();
        var messages = parser.parse(markdown);
        var options = dictionary.getEnabledLeafErrorTypes().stream()
                .filter(type -> !UNSUPPORTED_CODES.contains(type.code())).toList();
        if (options.isEmpty()) throw invalid("没有适用于聊天纠错的分类");
        log.info("聊天分析开始: promptVersion={}, userMessageCount={}", AiChatImportPromptBuilder.VERSION,
                messages.stream().filter(message -> "user".equals(message.role())).count());
        String json = client.analyze(promptBuilder.build(messages, options, mode));
        log.info("聊天分析 AI 原始回复: promptVersion={}, learningMode={}, response={}",
                AiChatImportPromptBuilder.VERSION, mode, json);
        var result = validator.validate(json, messages, options.stream().map(type -> type.id()).collect(Collectors.toSet()), mode);
        log.info("聊天分析完成: promptVersion={}, candidateCount={}", AiChatImportPromptBuilder.VERSION, result.candidates().size());
        return result;
    }

    @Override
    @Transactional
    public ChatImportConfirmationVO confirm(ChatImportConfirmationRequest request) {
        User user = requireUser();
        LocalDateTime now = LocalDateTime.now();
        Map<Long, List<ChatImportConfirmationRequest.Item>> groups = new TreeMap<>();
        Map<Long, UserErrorType> resolvedTypes = new HashMap<>();
        for (var item : request.items()) {
            ChatReviewTextValidator.validate(item.reviewSourceText(), item.referenceAnswer(), request.learningMode());
            UserErrorType type = resolveType(user.getId(), request.learningMode(), item, now);
            resolvedTypes.put(type.getId(), type);
            groups.computeIfAbsent(type.getId(), ignored -> new ArrayList<>()).add(item);
        }
        Map<QuestionContent, Long> questionIds = new HashMap<>();
        List<ChatImportConfirmationVO.CardResult> results = new ArrayList<>();
        // 所有导入请求按稳定的用户错误类型顺序获取卡片锁。
        for (var group : groups.entrySet()) {
            ReviewCard previous = cards.selectForUpdateByUserErrorTypeId(group.getKey());
            if (previous == null && group.getValue().stream().anyMatch(item -> "EXISTING_USER_ERROR_TYPE".equals(item.mode()))) {
                throw invalid("已有复习卡片不存在");
            }
            if (previous != null && (Boolean.TRUE.equals(previous.getDeleted()) || !user.getId().equals(previous.getUserId()))) {
                throw invalid("目标复习卡片不存在或已删除");
            }
            String action = previous == null ? "CREATED" : "MASTERED".equals(previous.getStatus()) ? "REACTIVATED" : "UPDATED";
            List<Long> ids = new ArrayList<>();
            int severity = 0;
            for (var item : group.getValue()) {
                QuestionContent key = new QuestionContent(item.reviewSourceText().trim(), item.referenceAnswer().trim());
                Long id = questionIds.computeIfAbsent(key, ignored -> saveQuestion(
                        key, request.learningMode(), resolvedTypes.get(group.getKey()).getName(), now));
                if (!ids.contains(id)) ids.add(id);
                severity = Math.max(severity, switch (item.severity()) { case "HIGH" -> 2; case "MEDIUM" -> 1; default -> 0; });
            }
            ReviewCard card = reviews.recordChatImportErrors(user.getId(), ids, group.getKey(),
                    List.of("LOW", "MEDIUM", "HIGH").get(severity), now);
            results.add(new ChatImportConfirmationVO.CardResult(card.getId(), group.getKey(), List.copyOf(ids), action));
        }
        return new ChatImportConfirmationVO(List.copyOf(results));
    }

    private UserErrorType resolveType(Long userId, String mode, ChatImportConfirmationRequest.Item item, LocalDateTime now) {
        if ("EXISTING_USER_ERROR_TYPE".equals(item.mode())) {
            if (item.userErrorTypeId() == null) throw invalid("请选择已有复习重点");
            UserErrorType type = userTypes.selectActiveByIdAndUserId(item.userErrorTypeId(), userId);
            if (type == null || !mode.equals(type.getLearningMode())) throw invalid("已有复习重点不可用或学习模式不匹配");
            requireErrorType(type.getErrorTypeId());
            return type;
        }
        if (item.errorTypeId() == null || item.name() == null || item.name().isBlank()
                || item.description() == null || item.description().isBlank()) throw invalid("新建复习重点需要分类、名称和说明");
        requireErrorType(item.errorTypeId());
        UserErrorType existing = userTypes.selectActiveByUserIdAndErrorTypeIdAndName(
                userId, item.errorTypeId(), mode, item.name().trim());
        if (existing != null) return existing;
        UserErrorType type = new UserErrorType();
        type.setUserId(userId);
        type.setErrorTypeId(item.errorTypeId());
        type.setLearningMode(mode);
        type.setName(item.name().trim());
        type.setDescription(item.description().trim());
        type.setStatus("ACTIVE");
        type.setCreatedAt(now);
        type.setUpdatedAt(now);
        userTypes.insertUserErrorType(type);
        return type;
    }

    private void requireErrorType(Long id) {
        var type = errorTypes.selectEnabledLeafById(id);
        if (type == null || UNSUPPORTED_CODES.contains(type.getCode())) throw invalid("错误分类不可用或不适用于日语纠错");
    }

    private Long saveQuestion(QuestionContent content, String mode, String focus, LocalDateTime now) {
        Question question = new Question();
        question.setQuestionType(TranslationDirection.fromLearningMode(mode).shortQuestionType());
        question.setSourceText(content.source());
        question.setContextText("聊天导入复习");
        question.setLevel("N3");
        question.setDifficulty(3);
        question.setGrammarPoint(focus);
        question.setSpoken(false);
        question.setBusiness(false);
        question.setExam(false);
        question.setSourceType("REVIEW_DERIVED");
        question.setEnabled(true);
        question.setDeleted(false);
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questions.insertQuestion(question);
        QuestionAnswer answer = new QuestionAnswer();
        answer.setQuestionId(question.getId());
        answer.setAnswerText(content.answer());
        answer.setAnswerType("STANDARD");
        answer.setPrimaryAnswer(true);
        answer.setSortOrder(0);
        answer.setDeleted(false);
        answer.setCreatedAt(now);
        answer.setUpdatedAt(now);
        answers.insertQuestionAnswer(answer);
        return question.getId();
    }

    private User requireUser() {
        User user = users.selectEnabledUserByCode("LOCAL_DEFAULT");
        if (user == null) throw invalid("本地用户不存在或不可用");
        return user;
    }

    private BusinessException invalid(String message) { return new BusinessException(ErrorCode.BUSINESS_ERROR, message); }
    private record QuestionContent(String source, String answer) { }
}
