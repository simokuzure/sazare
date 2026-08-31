package com.sazare.service.ai.validation;

import com.sazare.common.ArticleLengthTier;
import com.sazare.common.TranslationDirection;
import com.sazare.dto.AiArticleBlueprintDTO;
import com.sazare.dto.AiArticleGenerationRequest;
import com.sazare.dto.AiArticleGenerationResponseDTO;
import com.sazare.dto.AiArticleSentenceDTO;
import com.sazare.dto.AiGeneratedArticleDTO;
import com.sazare.dto.AiGeneratedQuestionDTO;
import com.sazare.dto.AiQuestionAnswerDTO;
import com.sazare.dto.AiQuestionGenerationRequest;
import com.sazare.dto.AiQuestionGenerationResponseDTO;
import com.sazare.entity.Tag;
import com.sazare.exception.BusinessException;
import com.sazare.exception.ErrorCode;
import com.sazare.service.ai.prompt.ArticleGenreRoleRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class AiQuestionGenerationResponseValidator {

    private static final String ANSWER_TYPE_STANDARD = "STANDARD";
    private static final Set<String> VALID_ANSWER_TYPES = Set.of(ANSWER_TYPE_STANDARD, "REFERENCE");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern JAPANESE_TEXT_PATTERN = Pattern.compile("[\\u3040-\\u30ff\\u4e00-\\u9fff]");
    private static final Pattern JAPANESE_KANA_PATTERN = Pattern.compile("[\\u3040-\\u30ff]");
    private static final Pattern CHINESE_ARTICLE_END_PATTERN =
            Pattern.compile(".*[。？！][\"'”’」』》】）〕〗〙〛〉)]*$");
    private static final String ARTICLE_SEPARATOR = "\n\n";
    private static final int ARTICLE_MAX_SENTENCES = 30;

    private final ObjectMapper objectMapper;

    public AiQuestionGenerationResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ValidatedQuestion> validateQuestions(
            AiQuestionGenerationRequest request,
            String aiContent,
            Map<String, Tag> sceneTagMap,
            Map<String, Tag> functionTagMap
    ) {
        AiQuestionGenerationResponseDTO response = parseQuestionResponse(aiContent);
        if (response.questions() == null || response.questions().size() != request.questionCount()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出题目数量不一致");
        }

        Map<String, Tag> allowedTagMap = new LinkedHashMap<>();
        allowedTagMap.putAll(sceneTagMap);
        allowedTagMap.putAll(functionTagMap);

        List<ValidatedQuestion> validatedQuestions = new ArrayList<>();
        for (int index = 0; index < response.questions().size(); index++) {
            validatedQuestions.add(validateQuestion(
                    request,
                    response.questions().get(index),
                    sceneTagMap,
                    allowedTagMap,
                    index
            ));
        }
        return List.copyOf(validatedQuestions);
    }

    public ValidatedArticle validateArticle(
            AiArticleGenerationRequest request,
            String aiContent,
            String expectedSeed
    ) {
        AiArticleGenerationResponseDTO response = parseArticleResponse(aiContent);
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        AiArticleBlueprintDTO blueprint = validateArticleBlueprint(
                response.blueprint(),
                request.genreTagCode(),
                expectedSeed
        );
        if (response.article() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 article 不能为空");
        }
        AiGeneratedArticleDTO article = response.article();
        if (!direction.articleQuestionType().equals(article.questionType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 questionType 不合法");
        }
        if (!request.level().equals(article.level()) || !request.difficulty().equals(article.difficulty())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章等级或难度与请求不一致");
        }
        validateRequiredText(article.contextText(), "AI 文章 contextText 不能为空");
        validateRequiredText(article.grammarPoint(), "AI 文章 grammarPoint 不能为空");
        if (article.grammarPoint().length() > 255) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章生词提示不能超过 255 个字符");
        }
        if (article.spoken() == null || article.business() == null || article.exam() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 spoken、business、exam 必须是布尔值");
        }
        if (article.sentences() == null || article.sentences().isEmpty()
                || article.sentences().size() > ARTICLE_MAX_SENTENCES) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章句子数量必须在 1 到 30 之间");
        }

        List<String> sourceSegments = new ArrayList<>();
        List<String> referenceSegments = new ArrayList<>();
        Set<String> uniqueReferences = new LinkedHashSet<>();
        for (int index = 0; index < article.sentences().size(); index++) {
            AiArticleSentenceDTO sentence = article.sentences().get(index);
            if (sentence == null || sentence.index() == null || sentence.index() != index) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章句子索引必须从 0 连续递增");
            }
            String sourceText = requireArticleText(sentence.chineseText(), "AI 文章源句不能为空");
            if (direction == TranslationDirection.ZH_TO_JA) {
                validateAiArticleChineseSentence(sourceText, index);
            } else if (containsChinese(sourceText) || containsJapaneseKana(sourceText)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 英文文章源句不能包含中日文字");
            }
            String japanese = requireArticleText(sentence.japaneseReference(), "AI 文章 japaneseReference 不能为空");
            if (containsLineBreak(japanese) || !containsJapaneseKana(japanese)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 japaneseReference 必须是无换行的日语句子");
            }
            if (!uniqueReferences.add(japanese)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 japaneseReference 不能重复");
            }
            sourceSegments.add(sourceText);
            referenceSegments.add(japanese);
        }

        int articleLength = direction.countArticleLength(String.join(" ", sourceSegments));
        ArticleLengthTier lengthTier = ArticleLengthTier.from(request.lengthTier());
        int minimumLength = lengthTier.minimum(direction);
        int maximumLength = lengthTier.maximum(direction);
        if (articleLength < minimumLength || articleLength > maximumLength) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "AI 文章长度必须在 " + minimumLength + " 到 "
                            + maximumLength + " " + direction.articleLengthUnit() + "之间");
        }
        return new ValidatedArticle(
                blueprint,
                article,
                String.join(ARTICLE_SEPARATOR, sourceSegments),
                String.join(ARTICLE_SEPARATOR, referenceSegments)
        );
    }

    public void validateAnswers(List<AiQuestionAnswerDTO> answers, String prefix) {
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

    private AiQuestionGenerationResponseDTO parseQuestionResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出为空");
        }
        try {
            JsonNode root = objectMapper.readTree(aiContent);
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
            return objectMapper.treeToValue(root, AiQuestionGenerationResponseDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 输出不是合法 JSON");
        }
    }

    private AiArticleGenerationResponseDTO parseArticleResponse(String aiContent) {
        if (aiContent == null || aiContent.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章输出为空");
        }
        try {
            JsonNode root = objectMapper.readTree(aiContent);
            if (!root.isObject()
                    || !new LinkedHashSet<>(root.propertyNames()).equals(Set.of("blueprint", "article"))
                    || root.get("blueprint") == null
                    || !root.get("blueprint").isObject()
                    || root.get("article") == null
                    || !root.get("article").isObject()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "AI 文章 JSON 顶层必须且只能包含 blueprint 和 article 对象");
            }
            return objectMapper.treeToValue(root, AiArticleGenerationResponseDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章输出不是合法 JSON");
        }
    }

    private ValidatedQuestion validateQuestion(
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
        TranslationDirection direction = TranslationDirection.fromLearningMode(request.learningMode());
        if (!direction.shortQuestionType().equals(question.questionType())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " questionType 不合法");
        }
        validateRequiredText(question.sourceText(), prefix + " sourceText 不能为空");
        if (!containsChinese(question.sourceText()) && direction == TranslationDirection.ZH_TO_JA) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " sourceText 必须包含中文");
        }
        if (direction == TranslationDirection.EN_TO_JA
                && (containsChinese(question.sourceText()) || containsJapaneseKana(question.sourceText()))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, prefix + " sourceText 必须是英文");
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
        return new ValidatedQuestion(question, selectedTags);
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
        if (normalizedCodes.stream().noneMatch(sceneTagMap::containsKey)) {
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
        return List.copyOf(selectedTags);
    }

    private AiArticleBlueprintDTO validateArticleBlueprint(
            AiArticleBlueprintDTO blueprint,
            String genreCode,
            String expectedSeed
    ) {
        if (blueprint == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 blueprint 不能为空");
        }
        if (!expectedSeed.equals(blueprint.seed())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 blueprint.seed 与本次请求不一致");
        }
        try {
            UUID.fromString(blueprint.seed());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 blueprint.seed 不是合法 UUID");
        }
        validateRequiredText(blueprint.coreConcept(), "AI 文章 coreConcept 不能为空");
        Set<String> expectedRoleKeys = ArticleGenreRoleRegistry.roleKeysFor(genreCode);
        if (blueprint.roles() == null || !blueprint.roles().keySet().equals(expectedRoleKeys)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "AI 文章 roles 与 GENRE 语义角色不一致");
        }
        Map<String, String> normalizedRoles = new LinkedHashMap<>();
        for (String roleKey : expectedRoleKeys) {
            String roleValue = blueprint.roles().get(roleKey);
            validateRequiredText(roleValue, "AI 文章角色 " + roleKey + " 不能为空");
            normalizedRoles.put(roleKey, roleValue.trim());
        }
        return new AiArticleBlueprintDTO(
                blueprint.seed(),
                blueprint.coreConcept().trim(),
                Map.copyOf(normalizedRoles)
        );
    }

    private void validateAnswer(AiQuestionAnswerDTO answer, String prefix) {
        validateRequiredText(answer.answerText(), prefix + " answerText 不能为空");
        if (!containsJapaneseText(answer.answerText())) {
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

    private String requireArticleText(String value, String message) {
        validateRequiredText(value, message);
        return value.trim();
    }

    private void validateAiArticleChineseSentence(String value, int index) {
        String fieldName = "AI 文章第 " + (index + 1) + " 句 chineseText";
        if (containsLineBreak(value)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, fieldName + " 不能包含换行");
        }
        if (!containsChinese(value)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, fieldName + " 必须包含中文");
        }
        var kanaMatcher = JAPANESE_KANA_PATTERN.matcher(value);
        if (kanaMatcher.find()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    fieldName + " 不能包含平假名或片假名，检测到：" + kanaMatcher.group()
            );
        }
        if (!CHINESE_ARTICLE_END_PATTERN.matcher(value).matches()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    fieldName + " 必须以。？！之一结束，末尾可以跟引号或括号"
            );
        }
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private void validateRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private boolean containsChinese(String value) {
        return value != null && CHINESE_PATTERN.matcher(value).find();
    }

    private boolean containsJapaneseText(String value) {
        return value != null && JAPANESE_TEXT_PATTERN.matcher(value).find();
    }

    private boolean containsJapaneseKana(String value) {
        return value != null && JAPANESE_KANA_PATTERN.matcher(value).find();
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }

    public record ValidatedQuestion(AiGeneratedQuestionDTO question, List<Tag> tags) {
    }

    public record ValidatedArticle(
            AiArticleBlueprintDTO blueprint,
            AiGeneratedArticleDTO article,
            String sourceText,
            String referenceText
    ) {
    }
}
