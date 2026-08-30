package com.sazare.service.ai.prompt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ArticleGenreRoleRegistry {

    private static final Map<String, Map<String, String>> ROLES_BY_GENRE = buildRolesByGenre();

    private ArticleGenreRoleRegistry() {
    }

    public static Map<String, String> rolesFor(String genreCode) {
        Map<String, String> roles = ROLES_BY_GENRE.get(genreCode);
        if (roles == null) {
            throw new IllegalArgumentException("不支持的文章 GENRE: " + genreCode);
        }
        return roles;
    }

    public static Set<String> roleKeysFor(String genreCode) {
        return rolesFor(genreCode).keySet();
    }

    private static Map<String, Map<String, String>> buildRolesByGenre() {
        Map<String, Map<String, String>> rolesByGenre = new LinkedHashMap<>();
        rolesByGenre.put("NARRATIVE", roles(
                "subject", "叙述对象", "setting", "发生环境",
                "experience", "核心经历", "changeOrInsight", "变化或感悟"));
        rolesByGenre.put("EXPOSITORY", roles(
                "subject", "说明对象", "angle", "解释角度",
                "detail", "具体细节", "impactOrComparison", "影响或对比"));
        rolesByGenre.put("OPINION", roles(
                "issue", "讨论议题", "position", "核心立场",
                "reason", "支持理由", "counterpointOrConstraint", "反方观点或现实限制"));
        rolesByGenre.put("PRACTICAL", roles(
                "purpose", "文本用途", "senderAndRecipient", "发送者与接收者",
                "situation", "实际情境", "requiredInformationOrAction", "需要传达或执行的信息"));
        rolesByGenre.put("ESSAY", roles(
                "triggerImage", "触发物或画面", "feeling", "个人感受",
                "association", "联想主题", "reflection", "思考落点"));
        rolesByGenre.put("DIARY", roles(
                "timeContext", "时间背景", "dailyExperience", "当天经历",
                "emotionalChange", "情绪变化", "unresolvedThought", "尚未解决的想法"));
        rolesByGenre.put("DIALOGUE", roles(
                "participantRelationship", "参与者关系", "setting", "对话场合",
                "informationGapOrDisagreement", "信息差或分歧", "communicationGoal", "沟通目标"));
        rolesByGenre.put("NEWS_REPORT", roles(
                "event", "核心事件", "timeAndPlace", "时间地点",
                "affectedParty", "受影响对象", "causeImpactOrResponse", "原因影响或应对措施"));
        rolesByGenre.put("INTERVIEW", roles(
                "interviewee", "受访者身份", "topic", "访谈主题",
                "keyExperienceOrView", "关键经历或观点", "followUpDirection", "追问方向"));
        rolesByGenre.put("REVIEW", roles(
                "reviewSubject", "评测对象", "usageScenario", "使用或体验场景",
                "criteria", "评价维度", "tradeoff", "优缺点或取舍"));
        rolesByGenre.put("GUIDE", roles(
                "audience", "目标人群", "goal", "操作目标",
                "prerequisiteOrConstraint", "前提或限制", "commonMistakeOrTip", "常见错误或实用提示"));
        rolesByGenre.put("FICTION", roles(
                "protagonistOrEntity", "主角或核心存在", "fictionalSetting", "虚构环境",
                "specialObjectOrRule", "特殊物件或世界规则", "desireOrConflict", "欲望或冲突"));
        return Collections.unmodifiableMap(rolesByGenre);
    }

    private static Map<String, String> roles(
            String key1, String value1,
            String key2, String value2,
            String key3, String value3,
            String key4, String value4
    ) {
        Map<String, String> roles = new LinkedHashMap<>();
        roles.put(key1, value1);
        roles.put(key2, value2);
        roles.put(key3, value3);
        roles.put(key4, value4);
        return Collections.unmodifiableMap(roles);
    }
}
